package dev.todaystrack.server.music

import dev.todaystrack.music.*
import kotlinx.serialization.json.*
import java.io.File
import java.sql.DriverManager
import java.util.UUID

/** Adds v2 tables to the existing ledger DB. No legacy payload, attempt or asset is rewritten. */
class MusicStore(root: File,private val allowLocalTestEligibility: Boolean=false): AutoCloseable {
    private val db=DriverManager.getConnection("jdbc:sqlite:${File(root,"server.db").absolutePath}")
    init {
        db.createStatement().use { s ->
            s.execute("PRAGMA journal_mode=WAL");s.execute("PRAGMA synchronous=FULL");s.execute("PRAGMA busy_timeout=5000")
            s.execute("CREATE TABLE IF NOT EXISTS budget(id INTEGER PRIMARY KEY CHECK(id=1),reserved INTEGER NOT NULL,cap INTEGER NOT NULL)")
            s.execute("INSERT OR IGNORE INTO budget VALUES(1,0,5000000)")
            s.execute("CREATE TABLE IF NOT EXISTS v2_inputs(id TEXT PRIMARY KEY,owner TEXT NOT NULL,intent TEXT NOT NULL,hash TEXT NOT NULL,payload TEXT NOT NULL,coverage_hash TEXT NOT NULL,state TEXT NOT NULL DEFAULT 'ACTIVE',UNIQUE(owner,intent))")
            s.execute("CREATE TABLE IF NOT EXISTS v2_media(id TEXT PRIMARY KEY,owner TEXT NOT NULL,selection TEXT NOT NULL,state TEXT NOT NULL,derivative_hash TEXT,mime TEXT,bytes INTEGER,duration_ms INTEGER,revision INTEGER NOT NULL,cleanup_state TEXT NOT NULL DEFAULT 'NOT_UPLOADED',created_ms INTEGER NOT NULL)")
            s.execute("CREATE TABLE IF NOT EXISTS v2_quotes(id TEXT PRIMARY KEY,owner TEXT NOT NULL,input_id TEXT NOT NULL,payload TEXT NOT NULL,expires INTEGER NOT NULL)")
            s.execute("CREATE TABLE IF NOT EXISTS v2_jobs(id TEXT PRIMARY KEY,owner TEXT NOT NULL,intent TEXT NOT NULL,input_id TEXT NOT NULL,input_hash TEXT NOT NULL,coverage_hash TEXT NOT NULL,state TEXT NOT NULL,stage TEXT,revision INTEGER NOT NULL DEFAULT 1,auth_hash TEXT NOT NULL,mode TEXT NOT NULL,reserved INTEGER NOT NULL,plan TEXT,request_hash TEXT,asset_hash TEXT,asset_bytes INTEGER,duration_ms INTEGER,integrity TEXT,lyrics TEXT,structure TEXT,error TEXT,UNIQUE(owner,intent))")
            s.execute("CREATE TABLE IF NOT EXISTS v2_attempts(id TEXT PRIMARY KEY,job_id TEXT NOT NULL,stage TEXT NOT NULL,stage_key TEXT NOT NULL,request_hash TEXT NOT NULL,cost INTEGER NOT NULL,state TEXT NOT NULL,result TEXT,UNIQUE(job_id,stage,stage_key))")
            s.execute("CREATE TABLE IF NOT EXISTS v2_analysis(owner TEXT NOT NULL,cache_key TEXT NOT NULL,result TEXT NOT NULL,PRIMARY KEY(owner,cache_key))")
            s.execute("CREATE TABLE IF NOT EXISTS v2_authorizations(hash TEXT PRIMARY KEY,reserved INTEGER NOT NULL,requests INTEGER NOT NULL)")
            s.execute("CREATE TABLE IF NOT EXISTS v2_deleted_ranges(owner TEXT NOT NULL,coverage_hash TEXT NOT NULL,PRIMARY KEY(owner,coverage_hash))")
            s.execute("CREATE TABLE IF NOT EXISTS v2_deleted_slices(owner TEXT NOT NULL,device TEXT NOT NULL,session TEXT NOT NULL,from_seq INTEGER NOT NULL,to_seq INTEGER NOT NULL)")
            s.execute("CREATE TABLE IF NOT EXISTS v2_dispatch_fences(owner TEXT NOT NULL,stage TEXT NOT NULL,stage_key TEXT NOT NULL,attempt_id TEXT NOT NULL,PRIMARY KEY(owner,stage,stage_key))")
            s.execute("CREATE TABLE IF NOT EXISTS v2_job_intents(owner TEXT NOT NULL,intent TEXT NOT NULL,input_hash TEXT NOT NULL,job_id TEXT NOT NULL,PRIMARY KEY(owner,intent))")
            s.execute("CREATE TABLE IF NOT EXISTS v2_cleanup(attempt_id TEXT PRIMARY KEY,state TEXT NOT NULL)")
        }
        if(rows("PRAGMA table_info(v2_jobs)").none { it["name"]=="kind" }) execute("ALTER TABLE v2_jobs ADD COLUMN kind TEXT NOT NULL DEFAULT 'MUSIC'")
    }
    @Synchronized internal fun execute(sql: String,vararg args: Any?): Int=db.prepareStatement(sql).use { p ->
        args.forEachIndexed { i,v->p.setObject(i+1,v) };p.executeUpdate()
    }
    @Synchronized internal fun rows(sql: String,vararg args: Any?): List<Map<String,String?>> = db.prepareStatement(sql).use { p ->
        args.forEachIndexed { i,v->p.setObject(i+1,v) };p.executeQuery().use { r->buildList {
            while(r.next()) add((1..r.metaData.columnCount).associate { r.metaData.getColumnName(it) to r.getString(it) })
        } }
    }
    internal fun row(sql: String,vararg args: Any?)=rows(sql,*args).firstOrNull()
    @Synchronized internal fun <T> transaction(action: ()->T): T {
        check(db.autoCommit) { "NESTED_TRANSACTION" };db.autoCommit=false
        return try { action().also { db.commit() } } catch(e: Throwable) { db.rollback();throw e } finally { db.autoCommit=true }
    }
    fun reservedMicros(): Long=row("SELECT reserved FROM budget WHERE id=1")!!.getValue("reserved")!!.toLong()
    fun attemptCount(): Int=row("SELECT COUNT(*) AS n FROM v2_attempts")!!.getValue("n")!!.toInt()
    fun input(owner: String,id: String)=row("SELECT * FROM v2_inputs WHERE owner=? AND id=?",owner,id)
    fun media(owner: String,id: String)=row("SELECT * FROM v2_media WHERE owner=? AND id=?",owner,id)
    fun job(owner: String,id: String)=row("SELECT * FROM v2_jobs WHERE owner=? AND id=?",owner,id)
    fun jobById(id: String)=row("SELECT * FROM v2_jobs WHERE id=?",id)
    fun jobByIntent(owner: String,intent: String)=row("SELECT j.* FROM v2_jobs j JOIN v2_job_intents i ON i.job_id=j.id WHERE i.owner=? AND i.intent=?",owner,intent)
    fun attempt(id: String)=row("SELECT * FROM v2_attempts WHERE id=?",id)
    fun attempts(job: String)=rows("SELECT * FROM v2_attempts WHERE job_id=? ORDER BY rowid",job)
    fun hasReady(owner: String,coverage: String): Boolean {
        if(row("SELECT id FROM v2_jobs WHERE owner=? AND coverage_hash=? AND state='READY' AND (mode='LIVE' OR ?=1)",owner,coverage,if(allowLocalTestEligibility) 1 else 0)!=null) return true
        if(row("SELECT name FROM sqlite_master WHERE type='table' AND name='jobs'")==null) return false
        return rows("SELECT payload FROM jobs WHERE owner=? AND state='READY' AND mode='LIVE'",owner).any { r ->
            runCatching { musicJson.parseToJsonElement(r.getValue("payload")!!).jsonObject["sourceCoverageHash"]?.jsonPrimitive?.content==coverage }.getOrDefault(false)
        }
    }
    fun deleted(owner: String,coverage: String)=row("SELECT owner FROM v2_deleted_ranges WHERE owner=? AND coverage_hash=?",owner,coverage)!=null
    fun deletedCoverage(owner: String,coverage: JsonArray)=coverage.any { e->val s=e.jsonObject
        row("SELECT owner FROM v2_deleted_slices WHERE owner=? AND device=? AND session=? AND from_seq<? AND to_seq>? LIMIT 1",owner,s.text("deviceId"),s.text("sessionId"),s.getValue("toSeqExclusive").jsonPrimitive.long,s.getValue("fromSeq").jsonPrimitive.long)!=null }
    fun cache(owner: String,key: String)=row("SELECT result FROM v2_analysis WHERE owner=? AND cache_key=?",owner,key)?.get("result")
    fun cache(owner: String,key: String,result: String) { execute("INSERT OR REPLACE INTO v2_analysis VALUES(?,?,?)",owner,key,result) }

    fun reserveJob(owner: String,input: Map<String,String?>,intent: String,quote: JsonObject,policy: ExecutionPolicy,validateBeforeReserve: ()->Unit={}): String=transaction {
        val kind=if(quote["analysisOnly"]?.jsonPrimitive?.boolean==true) "ANALYSIS" else "MUSIC"
        jobByIntent(owner,intent)?.let { old ->
            require(old["input_hash"]==input["hash"] && old["kind"]==kind) { "IDEMPOTENCY_CONFLICT" };return@transaction old.getValue("id")!!
        }
        row("SELECT * FROM v2_jobs WHERE owner=? AND input_hash=? AND kind=?",owner,input.getValue("hash"),kind)?.let { old ->
            execute("INSERT INTO v2_job_intents VALUES(?,?,?,?)",owner,intent,input.getValue("hash"),old.getValue("id"))
            return@transaction old.getValue("id")!!
        }
        val current=this.input(owner,input.getValue("id")!!) ?: error("INPUT_NOT_FOUND")
        check(current["state"]=="ACTIVE" && !deleted(owner,current.getValue("coverage_hash")!!) && !deletedCoverage(owner,musicJson.parseToJsonElement(current.getValue("payload")!!).jsonObject.array("coverage"))) { "INPUT_DELETED" }
        validateBeforeReserve()
        check(policy.enabled()) { "PAID_EXECUTION_DISABLED" }
        require(quote.text("authorizationHash")==policy.hash()) { "AUTHORIZATION_CHANGED" }
        val total=quote.getValue("maximumCostMicros").jsonPrimitive.long
        val count=quote.getValue("maximumRequests").jsonPrimitive.int
        require(total>=0 && count>0)
        execute("INSERT OR IGNORE INTO v2_authorizations VALUES(?,0,0)",policy.hash())
        check(execute("UPDATE v2_authorizations SET reserved=reserved+?,requests=requests+? WHERE hash=? AND reserved+?<=? AND requests+?<=?",
            total,count,policy.hash(),total,policy.totalMicros,count,policy.maxRequests)==1) { "AUTHORIZATION_BUDGET_EXHAUSTED" }
        check(execute("UPDATE budget SET reserved=reserved+? WHERE id=1 AND reserved+?<=cap",total,total)==1) { "GLOBAL_BUDGET_EXHAUSTED" }
        val id=UUID.randomUUID().toString()
        execute("INSERT INTO v2_jobs(id,owner,intent,input_id,input_hash,coverage_hash,state,auth_hash,mode,reserved,kind) VALUES(?,?,?,?,?,?,'QUEUED',?,?,?,?)",
            id,owner,intent,input.getValue("id"),input.getValue("hash"),input.getValue("coverage_hash"),policy.hash(),if(policy.testing) "LOCAL_TEST" else "LIVE",total,kind)
        execute("INSERT INTO v2_job_intents VALUES(?,?,?,?)",owner,intent,input.getValue("hash"),id)
        id
    }
    /** The durable reservation and uniqueness boundary precedes every provider dispatch. */
    fun beginAttempt(jobId: String,stage: String,stageKey: String,request: String,cost: Long,policy: ExecutionPolicy): String?=transaction {
        val j=jobById(jobId) ?: return@transaction null
        if(j["state"] !in setOf("QUEUED","PREPARING")) return@transaction null
        if(row("SELECT id FROM v2_attempts WHERE job_id=? AND stage=? AND stage_key=?",jobId,stage,stageKey)!=null) return@transaction null
        if(row("SELECT attempt_id FROM v2_dispatch_fences WHERE owner=? AND stage=? AND stage_key=?",j.getValue("owner"),stage,stageKey)!=null) return@transaction null
        check(policy.enabled() && policy.hash()==j["auth_hash"]) { "PAID_EXECUTION_DISABLED" }
        require(stage in setOf("ANALYSIS","MUSIC") && cost==if(stage=="ANALYSIS") policy.analysisMicros else policy.musicMicros)
        check(!deleted(j.getValue("owner")!!,j.getValue("coverage_hash")!!)) { "INPUT_DELETED" }
        val spent=row("SELECT COALESCE(SUM(cost),0) AS n FROM v2_attempts WHERE job_id=?",jobId)!!.getValue("n")!!.toLong()
        check(spent+cost<=j.getValue("reserved")!!.toLong()) { "JOB_RESERVATION_EXCEEDED" }
        val id=UUID.randomUUID().toString()
        execute("INSERT INTO v2_attempts VALUES(?,?,?,?,?,?,'SUBMITTING',NULL)",id,jobId,stage,stageKey,contentHash(request.toByteArray(Charsets.UTF_8)),cost)
        execute("INSERT INTO v2_dispatch_fences VALUES(?,?,?,?)",j.getValue("owner"),stage,stageKey,id)
        execute("UPDATE v2_jobs SET state='SUBMITTING',stage=?,revision=revision+1 WHERE id=?",stage,jobId)
        id
    }
    fun resultSaved(attempt: String,result: String)=transaction {
        val a=attempt(attempt) ?: error("ATTEMPT_NOT_FOUND")
        val j=jobById(a.getValue("job_id")!!) ?: error("JOB_NOT_FOUND")
        check(j["state"] !in setOf("CANCELED","DELETED") && !deleted(j.getValue("owner")!!,j.getValue("coverage_hash")!!)) { "RESULT_SUPPRESSED" }
        execute("UPDATE v2_attempts SET state='RESULT_SAVED',result=? WHERE id=?",result,attempt)
        execute("UPDATE v2_jobs SET state='PREPARING',revision=revision+1 WHERE id=?",j.getValue("id"))
    }
    fun state(job: String,state: String,error: String?=null) {
        require(state in setOf("QUEUED","PREPARING","SUBMITTING","VALIDATING","READY","PLAN_READY","RECONCILING","MATERIAL_REVIEW_REQUIRED","BILLING_HELD","FAILED_VALIDATION","FAILED_PROVIDER_REJECTION","CANCELED","DELETED"))
        execute("UPDATE v2_jobs SET state=?,error=?,revision=revision+1 WHERE id=? AND state NOT IN ('CANCELED','DELETED')",state,error,job)
    }
    fun recover()=transaction {
        execute("UPDATE v2_jobs SET state='RECONCILING',revision=revision+1 WHERE state='SUBMITTING'")
        execute("UPDATE v2_attempts SET state='RECONCILING' WHERE state='SUBMITTING'")
    }
    fun cancel(owner: String,id: String)=transaction { cancelWithinTransaction(owner,id) }
    internal fun cancelWithinTransaction(owner: String,id: String) {
        val j=job(owner,id) ?: error("JOB_NOT_FOUND")
        check(j["state"]!="READY") { "ALREADY_READY_DELETE_SEPARATELY" }
        val spent=row("SELECT COALESCE(SUM(cost),0) AS n FROM v2_attempts WHERE job_id=?",id)!!.getValue("n")!!.toLong()
        val unused=j.getValue("reserved")!!.toLong()-spent
        if(unused>0) {
            execute("UPDATE budget SET reserved=reserved-? WHERE id=1",unused)
            // Request-count release only covers stages that never acquired a transmission boundary.
            execute("UPDATE v2_authorizations SET reserved=reserved-? WHERE hash=?",unused,j.getValue("auth_hash"))
        }
        execute("UPDATE v2_jobs SET state='CANCELED',reserved=?,revision=revision+1 WHERE owner=? AND id=?",spent,owner,id)
    }
    override fun close()=db.close()
}
