package dev.todaystrack.vehicle.capture

/** Source timestampMillis is UTC in the observed SDK; receive elapsed time is separate. */
class CapturePolicy {
    companion object {
        // App interpretation policy, not an SDK delivery guarantee or user acceptance threshold.
        // FusedLocation 1.1.3 exposes no interval argument/minimum via its callback API. Previous
        // finite SDK runs observed new source-to-receive ages of 4..3377 ms (and a 259568 ms cache).
        // Ten seconds leaves scheduling margin around those observations while rejecting delayed
        // in-epoch positions. The raw value remains stored; STALE is excluded from current facts.
        const val MAX_LOCATION_SOURCE_AGE_MS = 10_000L
    }
    fun locationQuality(sourceMillis: Long?, epochStartedUtc: Long, receivedUtc: Long, previous: Long?): String = when {
        sourceMillis == null || sourceMillis <= 0 -> "UNKNOWN_TIME"
        sourceMillis < epochStartedUtc -> "PRE_EPOCH"
        sourceMillis > receivedUtc -> "FUTURE_TIME"
        sourceMillis == previous -> "DUPLICATE"
        previous != null && sourceMillis < previous -> "OUT_OF_ORDER"
        receivedUtc - sourceMillis > MAX_LOCATION_SOURCE_AGE_MS -> "STALE"
        else -> "FRESH"
    }
    fun distanceDelta(older: Double?, newer: Double?, sameBoot: Boolean, sameEpoch: Boolean): Double? {
        if (!sameBoot || !sameEpoch || older == null || newer == null) return null
        if (!older.isFinite() || !newer.isFinite() || older < 0 || newer < older) return null
        return newer - older
    }
    fun elapsedDelta(older: Long, newer: Long, oldBoot: String, newBoot: String): Long? =
        if (oldBoot == newBoot && oldBoot != "unknown" && newer >= older) newer - older else null
}

/** Main-thread lifetime gate shared by service continuations and marshalled SDK callbacks. */
internal class CaptureStartGate {
    private var started = false
    var isStopped = false
        private set

    fun tryStart(): Boolean {
        if (started || isStopped) return false
        started = true
        return true
    }

    fun runIfActive(action: () -> Unit): Boolean {
        if (!started || isStopped) return false
        action()
        return true
    }

    fun stop(): Boolean {
        if (isStopped) return false
        isStopped = true
        return true
    }
}
