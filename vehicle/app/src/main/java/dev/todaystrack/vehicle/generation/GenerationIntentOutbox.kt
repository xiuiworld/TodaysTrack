package dev.todaystrack.vehicle.generation

import androidx.room.*
import dev.todaystrack.vehicle.auth.DevelopmentIdentity
import dev.todaystrack.vehicle.storage.CaptureDatabase
import java.util.UUID

@Entity(tableName = "generation_intents", indices = [Index(value = ["sessionId"], unique = true)])
data class GenerationIntent(
    @PrimaryKey val id: String,
    val sessionId: String,
    val inputHash: String,
    val payload: String,
    val state: String = "LOCAL_PENDING",
    val mode: String = "UNKNOWN",
    val jobId: String? = null,
    val errorCode: String? = null
)

@Entity(tableName = "tracks", indices = [Index(value = ["jobId"], unique = true)])
data class Track(
    @PrimaryKey val id: String,
    val jobId: String,
    val sessionId: String,
    val inputHash: String,
    val path: String,
    val sha256: String,
    val bytes: Long,
    val provenance: String,
    val title: String,
    val durationMs: Long
)

@Dao
interface GenerationDao {
    @Query("SELECT * FROM generation_intents WHERE sessionId = :sessionId LIMIT 1") suspend fun forSession(sessionId: String): GenerationIntent?
    @Insert suspend fun insert(intent: GenerationIntent)
    @Update suspend fun update(intent: GenerationIntent)
    @Query("SELECT * FROM generation_intents ORDER BY rowid DESC LIMIT 1") suspend fun latest(): GenerationIntent?
    @Query("SELECT * FROM generation_intents WHERE state != 'AVAILABLE' ORDER BY rowid") suspend fun unfinished(): List<GenerationIntent>
    @Query("SELECT * FROM tracks ORDER BY rowid DESC") suspend fun tracks(): List<Track>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertTrack(track: Track)
}

class GenerationIntentOutbox(private val db: CaptureDatabase, private val identity: DevelopmentIdentity) {
    suspend fun freeze(sessionId: String): GenerationIntent = db.withTransaction {
        db.generationDao().forSession(sessionId)?.let { return@withTransaction it }
        val session = db.captureDao().session(sessionId)
        val json = InputSnapshotBuilder().build(session, db.captureDao().events(sessionId), db.captureDao().gaps(sessionId), identity.deviceId).toString()
        GenerationIntent(UUID.randomUUID().toString(), sessionId, DevelopmentIdentity.sha256(json.toByteArray(Charsets.UTF_8)), json)
            .also { db.generationDao().insert(it) }
    }
}
