package dev.todaystrack.server

import java.sql.Connection
import java.sql.DriverManager
import java.util.UUID
import kotlinx.serialization.json.*

/** Single local submission server. SQLite transactions are durable; no in-memory job authority. */
class JobStore(url: String, historicalMicros: Long = 0) : AutoCloseable {
    private val db = DriverManager.getConnection(url)
    init {
        db.createStatement().use { s ->
            s.execute("PRAGMA journal_mode=WAL")
            s.execute("PRAGMA synchronous=FULL")
            s.execute("PRAGMA busy_timeout=5000")
            s.execute("CREATE TABLE IF NOT EXISTS budget (id INTEGER PRIMARY KEY CHECK(id=1), reserved INTEGER NOT NULL, cap INTEGER NOT NULL)")
            s.execute("CREATE TABLE IF NOT EXISTS jobs (id TEXT PRIMARY KEY, owner TEXT NOT NULL, intent TEXT NOT NULL, hash TEXT NOT NULL, payload TEXT NOT NULL, cost INTEGER NOT NULL, state TEXT NOT NULL, mode TEXT NOT NULL, asset_hash TEXT, asset_bytes INTEGER, duration_ms INTEGER, UNIQUE(owner,intent))")
            s.execute("CREATE TABLE IF NOT EXISTS attempts (id TEXT PRIMARY KEY, job_id TEXT NOT NULL UNIQUE, stage TEXT NOT NULL, request_hash TEXT NOT NULL, request_body TEXT NOT NULL, state TEXT NOT NULL)")
            s.execute("CREATE TABLE IF NOT EXISTS devices (id TEXT PRIMARY KEY, owner TEXT NOT NULL, public_key TEXT NOT NULL, kind TEXT NOT NULL)")
            s.execute("CREATE TABLE IF NOT EXISTS nonces (device TEXT NOT NULL, nonce TEXT NOT NULL, used_at INTEGER NOT NULL, PRIMARY KEY(device,nonce))")
            s.execute("CREATE TABLE IF NOT EXISTS snapshots (id TEXT PRIMARY KEY, owner TEXT NOT NULL, intent TEXT NOT NULL, hash TEXT NOT NULL, payload TEXT NOT NULL, UNIQUE(owner,intent))")
            s.execute("CREATE TABLE IF NOT EXISTS quotes (id TEXT PRIMARY KEY, owner TEXT NOT NULL, snapshot TEXT NOT NULL, cost INTEGER NOT NULL, mode TEXT NOT NULL, expires INTEGER NOT NULL)")
        }
        execute("INSERT OR IGNORE INTO budget VALUES(1,?,5000000)", historicalMicros)
    }
    private fun execute(sql: String, vararg args: Any?): Int = db.prepareStatement(sql).use { p ->
        args.forEachIndexed { i, v -> p.setObject(i + 1, v) }; p.executeUpdate()
    }
    private fun row(sql: String, vararg args: Any?): Map<String, String?>? = db.prepareStatement(sql).use { p ->
        args.forEachIndexed { i, v -> p.setObject(i + 1, v) }
        p.executeQuery().use { r -> if (!r.next()) null else (1..r.metaData.columnCount).associate { r.metaData.getColumnName(it) to r.getString(it) } }
    }
    @Synchronized private fun <T> transaction(block: () -> T): T {
        db.autoCommit = false
        return try { block().also { db.commit() } } catch (e: Exception) { db.rollback(); throw e } finally { db.autoCommit = true }
    }
    @Synchronized fun adopt(owner: String, intent: String, hash: String, payload: String, costMicros: Long): String = transaction {
        val prior = row("SELECT * FROM jobs WHERE owner=? AND intent=?", owner, intent)
        if (prior != null) {
            require(prior["hash"] == hash) { "IDEMPOTENCY_CONFLICT" }
            return@transaction prior.getValue("id")!!
        }
        require(costMicros in 0..5_000_000)
        check(execute("UPDATE budget SET reserved=reserved+? WHERE id=1 AND reserved+?<=cap", costMicros, costMicros) == 1) { "BUDGET_EXHAUSTED" }
        val id = UUID.randomUUID().toString()
        execute("INSERT INTO jobs(id,owner,intent,hash,payload,cost,state,mode) VALUES(?,?,?,?,?,?,'QUEUED',?)", id, owner, intent, hash, payload, costMicros, if(costMicros==0L) "FIXTURE" else "LIVE")
        id
    }
    @Synchronized fun beginDispatch(id: String, request: String? = null): Boolean = transaction {
        val job = row("SELECT * FROM jobs WHERE id=?", id) ?: return@transaction false
        if (job["state"] != "QUEUED") return@transaction false
        val body = request ?: job.getValue("payload")!!
        execute("INSERT INTO attempts VALUES(?,?,'MUSIC',?,?,'SUBMITTING')", UUID.randomUUID().toString(), id, sha256(body.toByteArray()), body)
        execute("UPDATE jobs SET state='SUBMITTING' WHERE id=?", id)
        true
    }
    @Synchronized fun state(id: String): String = row("SELECT state FROM jobs WHERE id=?", id)?.get("state") ?: "MISSING"
    @Synchronized fun recover() = transaction {
        execute("UPDATE jobs SET state='RECONCILING' WHERE state IN ('SUBMITTING','GENERATING')")
        execute("UPDATE attempts SET state='RECONCILING' WHERE state='SUBMITTING'")
    }
    @Synchronized fun reservedMicros(): Long = row("SELECT reserved FROM budget WHERE id=1")!!.getValue("reserved")!!.toLong()
    @Synchronized fun enroll(id: String, publicKey: String) { execute("INSERT INTO devices VALUES(?,'submission-owner',?,'CAR') ON CONFLICT(id) DO UPDATE SET public_key=excluded.public_key", id, publicKey) }
    @Synchronized fun device(id: String): Map<String,String?>? = row("SELECT * FROM devices WHERE id=?", id)
    @Synchronized fun useNonce(device: String, nonce: String, time: Long): Boolean = transaction {
        execute("DELETE FROM nonces WHERE used_at < ?", System.currentTimeMillis()-600_000)
        execute("INSERT OR IGNORE INTO nonces VALUES(?,?,?)", device, nonce, time) == 1
    }
    @Synchronized fun snapshot(owner: String, intent: String, hash: String, payload: String): String = transaction {
        row("SELECT * FROM snapshots WHERE owner=? AND intent=?",owner,intent)?.let {
            require(it["hash"]==hash) { "IDEMPOTENCY_CONFLICT" }; return@transaction it.getValue("id")!!
        }
        val id=UUID.randomUUID().toString()
        execute("INSERT INTO snapshots VALUES(?,?,?,?,?)",id,owner,intent,hash,payload); id
    }
    @Synchronized fun snapshotFor(owner: String, id: String): Map<String,String?>? = row("SELECT * FROM snapshots WHERE owner=? AND id=?",owner,id)
    @Synchronized fun quote(owner: String, snapshot: String, cost: Long, mode: String): String {
        require(snapshotFor(owner,snapshot)!=null)
        val id=UUID.randomUUID().toString()
        execute("INSERT INTO quotes VALUES(?,?,?,?,?,?)",id,owner,snapshot,cost,mode,System.currentTimeMillis()+300_000)
        return id
    }
    @Synchronized fun acceptQuote(owner: String, intent: String, snapshot: String, quoteId: String): String {
        // Acknowledgment loss reuses the accepted job even after the quote expires.
        val old=row("SELECT * FROM jobs WHERE owner=? AND intent=?",owner,intent)
        val snap=snapshotFor(owner,snapshot) ?: error("SNAPSHOT_NOT_FOUND")
        if(old!=null) { require(old["hash"]==snap["hash"]); return old.getValue("id")!! }
        val quote=row("SELECT * FROM quotes WHERE owner=? AND id=? AND snapshot=?",owner,quoteId,snapshot) ?: error("QUOTE_NOT_FOUND")
        check(quote.getValue("expires")!!.toLong()>=System.currentTimeMillis()) { "QUOTE_EXPIRED" }
        return adopt(owner,intent,snap.getValue("hash")!!,snap.getValue("payload")!!,quote.getValue("cost")!!.toLong())
    }
    @Synchronized fun job(owner: String, id: String): Map<String,String?>? = row("SELECT * FROM jobs WHERE owner=? AND id=?",owner,id)
    @Synchronized fun jobByIntent(owner: String, intent: String): Map<String,String?>? = row("SELECT * FROM jobs WHERE owner=? AND intent=?",owner,intent)
    // VALIDATING has a locally saved response/audio and must never return to paid dispatch.
    @Synchronized fun validationPending(): Map<String,String?>? = row("SELECT * FROM jobs WHERE state='VALIDATING' ORDER BY rowid LIMIT 1")
    @Synchronized fun queued(): Map<String,String?>? = row("SELECT * FROM jobs WHERE state='QUEUED' ORDER BY rowid LIMIT 1")
    @Synchronized fun updateState(id: String, state: String) {
        execute("UPDATE jobs SET state=? WHERE id=?",state,id)
        execute("UPDATE attempts SET state=? WHERE job_id=?",state,id)
    }
    @Synchronized fun ready(id: String, hash: String, bytes: Long, duration: Long) = transaction {
        check(state(id) in setOf("SUBMITTING","VALIDATING"))
        execute("UPDATE jobs SET state='READY',asset_hash=?,asset_bytes=?,duration_ms=? WHERE id=?",hash,bytes,duration,id)
        execute("UPDATE attempts SET state='RESULT_SAVED' WHERE job_id=?",id)
    }
    @Synchronized override fun close() { db.close() }
}
