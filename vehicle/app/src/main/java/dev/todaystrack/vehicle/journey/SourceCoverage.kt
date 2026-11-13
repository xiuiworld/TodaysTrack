package dev.todaystrack.vehicle.journey

data class Slice(val deviceId: String, val sessionId: String, val fromSeq: Long, val toSeqExclusive: Long)
object SourceCoverage {
    fun normalize(slices: List<Slice>): List<Slice> {
        require(slices.all { it.fromSeq >= 0 && it.toSeqExclusive > it.fromSeq })
        val result = mutableListOf<Slice>()
        for (slice in slices.sortedWith(compareBy({ it.deviceId }, { it.sessionId }, { it.fromSeq }))) {
            val last = result.lastOrNull()
            if (last != null && last.deviceId == slice.deviceId && last.sessionId == slice.sessionId && slice.fromSeq <= last.toSeqExclusive) {
                result[result.lastIndex] = last.copy(toSeqExclusive = maxOf(last.toSeqExclusive, slice.toSeqExclusive))
            } else result.add(slice)
        }
        return result
    }
}
