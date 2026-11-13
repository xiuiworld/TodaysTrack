package dev.todaystrack.vehicle.storage

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CaptureDao {
    @Insert suspend fun insertSession(session: CaptureSession)
    @Insert suspend fun insertEpoch(epoch: CaptureEpoch)
    @Insert suspend fun insertEvent(event: CaptureEvent): Long
    @Insert suspend fun insertGap(gap: CaptureGap)
    @Update suspend fun updateEpoch(epoch: CaptureEpoch)
    @Query("UPDATE sessions SET state = :state WHERE id = :id") suspend fun setSessionState(id: String, state: String)
    @Query("SELECT * FROM sessions WHERE state = 'OPEN' ORDER BY startedAtUtc DESC LIMIT 1") suspend fun openSession(): CaptureSession?
    @Query("SELECT * FROM sessions ORDER BY startedAtUtc DESC") fun sessions(): Flow<List<CaptureSession>>
    @Query("SELECT * FROM sessions WHERE id = :id") suspend fun session(id: String): CaptureSession
    @Query("SELECT * FROM sessions WHERE state = 'SEALED' ORDER BY startedAtUtc DESC LIMIT 1") suspend fun latestSealedSession(): CaptureSession?
    @Query("SELECT * FROM sessions WHERE state = 'SEALED' ORDER BY startedAtUtc DESC") suspend fun sealedSessions(): List<CaptureSession>
    @Query("SELECT * FROM epochs WHERE closed = 0") suspend fun openEpochs(): List<CaptureEpoch>
    @Query("SELECT * FROM epochs WHERE id = :id") suspend fun epoch(id: String): CaptureEpoch
    @Query("SELECT * FROM epochs WHERE sessionId = :id ORDER BY rowid") suspend fun epochsForSession(id: String): List<CaptureEpoch>
    @Query("SELECT MAX(seq) FROM events WHERE epochId = :id AND source = :source AND quality IN ('FRESH', 'VALID')") suspend fun lastSourceSeq(id: String, source: String): Long?
    @Query("SELECT * FROM events WHERE sessionId = :id ORDER BY seq") suspend fun events(id: String): List<CaptureEvent>
    @Query("SELECT * FROM events ORDER BY seq DESC LIMIT 1") fun lastEvent(): Flow<CaptureEvent?>
    @Query("SELECT COUNT(*) FROM events") fun count(): Flow<Int>
    @Query("SELECT * FROM gaps WHERE sessionId = :id ORDER BY lastDurableSeq") suspend fun gaps(id: String): List<CaptureGap>
    @Query("UPDATE gaps SET firstAfterSeq = :seq, endCertainty = 'FIRST_VALID_OBSERVATION' WHERE sessionId = :sessionId AND firstAfterSeq IS NULL AND (sourceMask = 'LOCATION|ODOMETER' OR sourceMask = :source)")
    suspend fun endGaps(sessionId: String, source: String, seq: Long)
}
