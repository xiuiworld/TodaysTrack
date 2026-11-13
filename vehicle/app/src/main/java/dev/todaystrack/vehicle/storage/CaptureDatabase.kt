package dev.todaystrack.vehicle.storage

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dev.todaystrack.vehicle.generation.*

@Entity(tableName = "sessions")
data class CaptureSession(
    @PrimaryKey val id: String,
    val kind: String,
    val zoneId: String,
    val genre: String,
    val lyricMode: String,
    val startedAtUtc: Long,
    val state: String = "OPEN"
)

@Entity(tableName = "epochs", indices = [Index("sessionId")])
data class CaptureEpoch(
    @PrimaryKey val id: String,
    val sessionId: String,
    val bootId: String,
    val startedElapsedMs: Long,
    val startedAtUtc: Long,
    val lastCommitSeq: Long = 0,
    val lastCommitElapsedMs: Long? = null,
    val closed: Boolean = false
)

@Entity(tableName = "events", indices = [Index("sessionId"), Index("epochId")])
data class CaptureEvent(
    @PrimaryKey(autoGenerate = true) val seq: Long = 0,
    val eventId: String,
    val sessionId: String,
    val epochId: String,
    val bootId: String,
    val kind: String,
    val source: String,
    val receivedAtUtc: Long,
    val receivedElapsedMs: Long,
    val sourceTimestampMs: Long? = null,
    val sourceTimeBasis: String? = null,
    val requestStartedElapsedMs: Long? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val accuracy: Double? = null,
    val sourceConfidence: Double? = null,
    val odometerKm: Double? = null,
    val quality: String = "UNKNOWN",
    val detail: String? = null,
    val acquisition: String = "SDK",
    val environment: String = "SIMULATED",
    val policyVersion: String = "capture-v1"
)

@Entity(tableName = "gaps", indices = [Index("sessionId")])
data class CaptureGap(
    @PrimaryKey val id: String,
    val sessionId: String,
    val epochId: String,
    val sourceMask: String,
    val lastDurableSeq: Long?,
    val firstAfterSeq: Long? = null,
    val reason: String,
    val startCertainty: String = "POSSIBLE_AFTER_LAST_COMMIT",
    val endCertainty: String = "PENDING_VALID_OBSERVATION"
)

@Database(entities = [CaptureSession::class, CaptureEpoch::class, CaptureEvent::class, CaptureGap::class, GenerationIntent::class, Track::class,MusicReceiptEntity::class], version = 3, exportSchema = false)
abstract class CaptureDatabase : RoomDatabase() {
    abstract fun captureDao(): CaptureDao
    abstract fun generationDao(): GenerationDao
    abstract fun musicV2Dao(): MusicV2Dao
    companion object {
        val MIGRATION_2_3=object: Migration(2,3) {
            override fun migrate(db: SupportSQLiteDatabase) { dev.todaystrack.vehicle.generation.v2.MusicReceiptSchema.statements.forEach { db.execSQL(it) } }
        }
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE events ADD COLUMN sourceConfidence REAL")
                // v1 stored the unscaled SDK confidence in accuracy. Preserve the original column.
                db.execSQL("UPDATE events SET sourceConfidence = accuracy")
                db.execSQL("CREATE TABLE IF NOT EXISTS generation_intents (id TEXT NOT NULL PRIMARY KEY, sessionId TEXT NOT NULL, inputHash TEXT NOT NULL, payload TEXT NOT NULL, state TEXT NOT NULL, mode TEXT NOT NULL, jobId TEXT, errorCode TEXT)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_generation_intents_sessionId ON generation_intents(sessionId)")
                db.execSQL("CREATE TABLE IF NOT EXISTS tracks (id TEXT NOT NULL PRIMARY KEY, jobId TEXT NOT NULL, sessionId TEXT NOT NULL, inputHash TEXT NOT NULL, path TEXT NOT NULL, sha256 TEXT NOT NULL, bytes INTEGER NOT NULL, provenance TEXT NOT NULL, title TEXT NOT NULL, durationMs INTEGER NOT NULL)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_tracks_jobId ON tracks(jobId)")
            }
        }
    }
}
