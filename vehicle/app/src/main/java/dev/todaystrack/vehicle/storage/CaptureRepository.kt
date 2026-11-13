package dev.todaystrack.vehicle.storage

import androidx.room.withTransaction
import java.util.UUID

/** Only the service's single writer may mutate capture records. UI reads Room flows. */
class CaptureRepository(private val db: CaptureDatabase) {
    private val dao = db.captureDao()
    suspend fun open(kind: String, genre: String, lyricMode: String, bootId: String, elapsed: Long, utc: Long): CaptureEpoch = db.withTransaction {
        // onDestroy is deliberately not a recovery prerequisite.
        for (old in dao.openEpochs()) {
            for (source in listOf("LOCATION", "ODOMETER")) dao.insertGap(CaptureGap(UUID.randomUUID().toString(), old.sessionId, old.id, source,
                dao.lastSourceSeq(old.id, source), reason = "UNCLEAN_EPOCH_END"))
            dao.updateEpoch(old.copy(closed = true))
        }
        val session = dao.openSession() ?: CaptureSession(UUID.randomUUID().toString(), kind,
            java.time.ZoneId.systemDefault().id, genre, lyricMode, utc).also { dao.insertSession(it) }
        CaptureEpoch(UUID.randomUUID().toString(), session.id, bootId, elapsed, utc).also { dao.insertEpoch(it) }
    }
    suspend fun append(event: CaptureEvent) = db.withTransaction {
        val epoch = dao.epoch(event.epochId)
        check(!epoch.closed) { "Closed epoch" }
        val seq = dao.insertEvent(event)
        dao.updateEpoch(epoch.copy(lastCommitSeq = seq, lastCommitElapsedMs = event.receivedElapsedMs))
        if (event.quality == "FRESH" || event.quality == "VALID") dao.endGaps(event.sessionId, event.source, seq)
        if (event.kind == "SOURCE_ERROR" || event.quality in setOf("PRE_EPOCH", "OUT_OF_ORDER", "INVALID", "RESET", "STALE", "UNKNOWN_TIME", "FUTURE_TIME")) {
            dao.insertGap(CaptureGap(UUID.randomUUID().toString(), event.sessionId, event.epochId, event.source,
                epoch.lastCommitSeq.takeIf { it > 0 }, reason = event.detail ?: event.quality))
        }
        seq
    }
    suspend fun close(epochId: String, seal: Boolean) = db.withTransaction {
        val epoch = dao.epoch(epochId)
        dao.updateEpoch(epoch.copy(closed = true))
        if (seal) dao.setSessionState(epoch.sessionId, "SEALED")
    }
}
