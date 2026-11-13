package dev.todaystrack.vehicle.generation

import androidx.room.*
import dev.todaystrack.music.*
import dev.todaystrack.vehicle.generation.v2.*
import dev.todaystrack.vehicle.storage.*
import kotlinx.serialization.json.*

@Entity(tableName="music_v2_receipts")
data class MusicReceiptEntity(
    @PrimaryKey val intentId: String,val inputId: String?,val inputHash: String?,val acceptanceUncertain: Boolean,
    val plan: String?,val returnedLyrics: String?,val returnedStructure: String?,val integrity: String?,val serverRevision: Long
) {
    fun value()=MusicReceipt(intentId,inputId,inputHash,acceptanceUncertain,plan,returnedLyrics,returnedStructure,integrity,serverRevision)
    companion object { fun from(r: MusicReceipt)=MusicReceiptEntity(r.intentId,r.inputId,r.inputHash,r.acceptanceUncertain,r.plan,r.returnedLyrics,r.returnedStructure,r.integrity,r.serverRevision) }
}
@Dao
interface MusicV2Dao {
    @Query("SELECT * FROM music_v2_receipts WHERE intentId=:id") suspend fun receipt(id: String): MusicReceiptEntity?
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun save(row: MusicReceiptEntity)
    @Query("SELECT r.* FROM music_v2_receipts r JOIN generation_intents g ON g.id=r.intentId WHERE g.state='AVAILABLE' ORDER BY g.rowid DESC LIMIT 10") suspend fun recent(): List<MusicReceiptEntity>
}

/** Only existing capture fields are mapped. Optional SDK capabilities are not claimed by this integration. */
object VehicleMusicProjection {
    private val qualityCodes=setOf("FRESH","VALID","RESET","STALE","UNKNOWN_TIME","FUTURE_TIME","INVALID","PRE_EPOCH","OUT_OF_ORDER","DUPLICATE","ERROR","UNKNOWN")
    private val gapCodes=qualityCodes+setOf("UNCLEAN_EPOCH_END","UNSUPPORTED","NOT_SUPPORTED","NULL_LOCATION","SOURCE_ERROR","SOURCE_INTERRUPTED","SOURCE_INTERRUPTION")
    fun freeze(id: String,session: CaptureSession,events: List<CaptureEvent>,gaps: List<CaptureGap>,epochs: List<CaptureEpoch>,deviceId: String,
        preferences: MusicPreferences=MusicPreferences(genre=session.genre,lyricMode=LyricMode.valueOf(session.lyricMode)),
        materials: List<MaterialFact> = emptyList(),mediaIds: List<String> = emptyList()): LocalMusicIntent {
        require(session.state=="SEALED") { "SEALED_RECORD_REQUIRED" }
        require(events.all { it.sessionId==session.id && it.source in setOf("LOCATION","ODOMETER") && it.acquisition=="SDK" }) { "CAPTURE_SOURCE_NOT_MAPPED" }
        val epochById=epochs.associateBy { it.id }
        require(epochs.all { it.sessionId==session.id } && events.all { epochById[it.epochId]?.bootId==it.bootId }) { "CAPTURE_EPOCH_MISMATCH" }
        val observations=events.map { e->Observation(e.seq,Source.valueOf(e.source),e.epochId,e.bootId,e.receivedElapsedMs,e.receivedAtUtc,e.quality.takeIf { it in qualityCodes } ?: "UNKNOWN_CAPTURE_QUALITY",
            e.sourceTimestampMs,e.sourceTimeBasis,e.latitude,e.longitude,e.odometerKm,if(e.source=="ODOMETER") "km" else null,
            confidence=e.sourceConfidence,sourceError=e.kind=="SOURCE_ERROR",requestStartedElapsedMs=e.requestStartedElapsedMs,epochStartedUtcMs=epochById[e.epochId]?.startedAtUtc) }
        val boundaries=gaps.map { g->Gap(g.sourceMask.split('|').map { Source.valueOf(it.trim()) }.toSet(),g.lastDurableSeq,g.firstAfterSeq,g.reason.takeIf { it in gapCodes } ?: "SOURCE_INTERRUPTION") }
        val environment=events.map { runCatching { Environment.valueOf(it.environment) }.getOrDefault(Environment.UNKNOWN) }.distinct().singleOrNull() ?: Environment.UNKNOWN
        val statuses=listOf(Source.LOCATION,Source.ODOMETER).map { source->SourceStatus(source,
            if(observations.any { it.source==source && !it.sourceError }) Availability.AVAILABLE else Availability.TEMPORARILY_UNAVAILABLE,
            receiptVerified=observations.any { it.source==source && !it.sourceError }) }
        return VehicleMusicInput.freeze(id,session.id,deviceId,ObservationBatch(observations,boundaries,statuses,environment),preferences,materials,mediaIds)
    }
    fun history(preferences: MusicPreferences,receipts: List<MusicReceiptEntity>): MusicPreferences {
        val plans=receipts.mapNotNull { it.plan?.let { raw->runCatching { musicJson.parseToJsonElement(raw).jsonObject }.getOrNull() } }
        return preferences.copy(recentGenres=plans.map { it.text("genre") },recentArrangements=plans.map { it.text("arrangement") })
    }
    fun description(payload: String): String {
        val envelope=musicJson.parseToJsonElement(payload).jsonObject
        if(envelope["schemaVersion"]?.jsonPrimitive?.int!=2) return ""
        return describePlan(envelope.getValue("localPlan").jsonObject)
    }
    fun describePlan(plan: JsonObject): String {
        val preferences=plan.getValue("preferences").jsonObject
        return "음악 방향 · ${plan.text("genre")} · 약 ${preferences.getValue("durationSeconds").jsonPrimitive.int}초 · ${if(preferences.text("lyricMode")=="VOCAL") "새 가사와 곡 함께 생성" else "보컬 없는 연주곡"}\n"+
            "확인된 관측 ${plan.array("sections").size}개 부분의 순서·반복·대비를 음악 전개의 참고로 사용합니다. 실제 운전 시간이나 운전자 감정을 뜻하지 않습니다."
    }
}

class RoomVehicleMusicStore(private val db: CaptureDatabase): VehicleMusicStore {
    override suspend fun load(id: String)=db.generationDao().byId(id)?.let { LocalMusicIntent(it.id,it.sessionId,it.payload,it.inputHash,it.state,it.jobId,it.mode,it.errorCode) } ?: error("LOCAL_INTENT_NOT_FOUND")
    override suspend fun receipt(id: String)=db.musicV2Dao().receipt(id)?.value()
    private suspend fun validate(intent: LocalMusicIntent,receipt: MusicReceipt) {
        val old=db.generationDao().byId(intent.id) ?: error("LOCAL_INTENT_NOT_FOUND")
        require(old.payload==intent.payload && old.inputHash==intent.inputHash && old.sessionId==intent.sessionId && receipt.intentId==intent.id) { "FROZEN_INPUT_IMMUTABLE" }
        db.musicV2Dao().receipt(intent.id)?.let { r->
            require((r.inputId==null || r.inputId==receipt.inputId) && (r.inputHash==null || r.inputHash==receipt.inputHash)) { "REMOTE_INPUT_IMMUTABLE" }
            require(receipt.serverRevision>=r.serverRevision) { "REMOTE_STATE_REVISION_REGRESSION" }
        }
    }
    override suspend fun save(intent: LocalMusicIntent,receipt: MusicReceipt)=db.withTransaction {
        validate(intent,receipt)
        if(db.generationDao().byId(intent.id)?.state=="AVAILABLE" && intent.state!="AVAILABLE") return@withTransaction
        db.musicV2Dao().save(MusicReceiptEntity.from(receipt))
        db.generationDao().update(GenerationIntent(intent.id,intent.sessionId,intent.inputHash,intent.payload,intent.state,intent.mode,intent.jobId,intent.errorCode))
    }
    override suspend fun ready(intent: LocalMusicIntent,receipt: MusicReceipt,audio: SavedMusicAudio,isNewAi: Boolean)=db.withTransaction {
        validate(intent,receipt)
        val job=intent.jobId ?: error("JOB_ID_REQUIRED")
        db.generationDao().trackForJob(job)?.let { require(it.inputHash==intent.inputHash && it.sha256==audio.sha256 && it.path==audio.path) { "EXISTING_TRACK_CONFLICT" } }
        db.generationDao().insertTrack(Track(job,job,intent.sessionId,intent.inputHash,audio.path,audio.sha256,audio.bytes,intent.mode,
            if(isNewAi) "오늘의 트랙 · 새 AI 음악" else "연결 검증용 음원 · 대역",audio.durationMs))
        db.musicV2Dao().save(MusicReceiptEntity.from(receipt))
        db.generationDao().update(GenerationIntent(intent.id,intent.sessionId,intent.inputHash,intent.payload,"AVAILABLE",intent.mode,job,null))
    }
}
