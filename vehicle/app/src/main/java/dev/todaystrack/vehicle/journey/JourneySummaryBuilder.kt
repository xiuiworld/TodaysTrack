package dev.todaystrack.vehicle.journey

data class Fact(val seq: Long, val epoch: String, val boot: String, val elapsed: Long, val source: String,
    val quality: String, val lat: Double? = null, val lon: Double? = null, val km: Double? = null,
    val sourceError: Boolean = false)

data class LocationGapBoundary(val lastDurableSeq: Long?, val firstAfterSeq: Long?, val sourceMask: String = "LOCATION")
data class PositionSegment(val positionSequence: List<String>, val positionEvidenceSeqs: List<Long>,
    val epochId: String, val bootId: String)
data class PositionUnknownBoundary(val lastEvidenceSeq: Long?, val firstEvidenceSeq: Long?, val reasons: List<String>,
    val lastDurableSeq: Long? = null, val firstAfterSeq: Long? = null)
data class DwellEvidence(val position: String, val fromSeq: Long, val toSeq: Long, val observationCount: Int,
    val observedRepeatedPositionSpanMs: Long, val epochId: String, val bootId: String)
data class JourneySummary(val positionSequence: List<String>, val positionEvidenceSeqs: List<Long>,
    val continuousDistanceKm: Double, val gapCount: Int, val returnObserved: Boolean, val epochObservationSpansMs: Map<String, Long>,
    val positionSegments: List<PositionSegment> = emptyList(),
    val positionUnknownBoundaries: List<PositionUnknownBoundary> = emptyList(),
    val dwellEvidence: List<DwellEvidence> = emptyList())
class JourneySummaryBuilder {
    fun build(events: List<Fact>, gapCount: Int, locationGapBoundaries: List<LocationGapBoundary> = emptyList()): JourneySummary {
        val ordered = events.sortedBy { it.seq }
        val locations = ordered.filter { it.isUsableLocation() }
        val labels = linkedMapOf<Pair<Double, Double>, String>()
        val sequence = locations.map { event ->
            labels.getOrPut(event.lat!! to event.lon!!) { if(labels.size<26) "${('A'.code + labels.size).toChar()}" else "P${labels.size+1}" }
        }
        val positionBySeq = locations.map { it.seq }.zip(sequence).toMap()
        val locationGaps = locationGapBoundaries.filter { gap -> gap.sourceMask.split('|').any { it.trim() == "LOCATION" } }
        // Preserve even open gaps and boundaries with no usable position on one side.
        val unknownBoundaries = locationGaps.map { gap -> PositionUnknownBoundary(
            gap.lastDurableSeq?.let { last -> locations.lastOrNull { it.seq <= last }?.seq },
            gap.firstAfterSeq?.let { first -> locations.firstOrNull { it.seq >= first }?.seq },
            listOf("LOCATION_GAP"), gap.lastDurableSeq, gap.firstAfterSeq)
        }.toMutableList()
        fun addUnknownBoundary(previous: Fact?, next: Fact?, reasons: List<String>) {
            val matching = unknownBoundaries.indices.filter { index ->
                unknownBoundaries[index].lastEvidenceSeq == previous?.seq &&
                    unknownBoundaries[index].firstEvidenceSeq == next?.seq
            }
            if (matching.isEmpty()) unknownBoundaries.add(PositionUnknownBoundary(previous?.seq, next?.seq, reasons))
            else matching.forEach { index ->
                val boundary = unknownBoundaries[index]
                unknownBoundaries[index] = boundary.copy(reasons = (boundary.reasons + reasons).distinct())
            }
        }
        val groups = mutableListOf<MutableList<Fact>>()
        var previousLocation: Fact? = null
        var rejectedLocation = false
        for (event in ordered.filter { it.source == "LOCATION" }) {
            if (!event.isUsableLocation()) {
                rejectedLocation = true
                continue
            }
            val previous = previousLocation
            val reasons = mutableListOf<String>()
            if (rejectedLocation) reasons.add("INVALID_LOCATION")
            if (previous != null) {
                if (locationGaps.any { gap ->
                    (gap.lastDurableSeq == null || gap.lastDurableSeq < event.seq) &&
                        (gap.firstAfterSeq == null || gap.firstAfterSeq > previous.seq)
                }) reasons.add("LOCATION_GAP")
                if (previous.epoch != event.epoch) reasons.add("EPOCH_CHANGE")
                if (previous.boot != event.boot) reasons.add("BOOT_CHANGE")
                if (!previous.boot.isKnownIdentity() || !event.boot.isKnownIdentity()) reasons.add("UNKNOWN_BOOT")
                if (!previous.epoch.isKnownIdentity() || !event.epoch.isKnownIdentity()) reasons.add("UNKNOWN_EPOCH")
                if (previous.elapsed < 0 || event.elapsed < 0 ||
                    (previous.boot == event.boot && event.elapsed < previous.elapsed)) reasons.add("NON_MONOTONIC_TIME")
            }
            if (reasons.isNotEmpty()) addUnknownBoundary(previous, event, reasons)
            if (groups.isEmpty() || reasons.isNotEmpty()) groups.add(mutableListOf(event))
            else groups.last().add(event)
            previousLocation = event
            rejectedLocation = false
        }
        if (rejectedLocation) addUnknownBoundary(previousLocation, null, listOf("INVALID_LOCATION"))
        val segments = groups.map { group -> PositionSegment(group.map { positionBySeq.getValue(it.seq) },
            group.map { it.seq }, group.first().epoch, group.first().boot) }
        val dwellEvidence = mutableListOf<DwellEvidence>()
        for (group in groups) {
            // Exact repeated observations are evidence of a span, never proof of a stop or visit.
            var from = 0
            while (from < group.size) {
                val first = group[from]
                val position = positionBySeq.getValue(first.seq)
                var end = from + 1
                while (end < group.size && positionBySeq.getValue(group[end].seq) == position) end++
                val last = group[end - 1]
                if (end - from >= 2 && first.boot.isKnownIdentity() && first.epoch.isKnownIdentity() &&
                    first.elapsed >= 0 && last.elapsed >= first.elapsed && last.seq > first.seq) {
                    dwellEvidence.add(DwellEvidence(position, first.seq, last.seq, end - from,
                        last.elapsed - first.elapsed, first.epoch, first.boot))
                }
                from = end
            }
        }
        var distance = 0.0
        var prior: Fact? = null
        val odometerGaps = locationGapBoundaries.filter { gap ->
            gap.sourceMask.split('|').any { it.trim() == "ODOMETER" }
        }
        for (e in ordered.filter { it.source == "ODOMETER" }) {
            val p = prior
            if (e.quality == "VALID" && !e.sourceError && p != null && e.epoch == p.epoch && e.boot == p.boot &&
                e.boot.isKnownIdentity() && e.epoch.isKnownIdentity() &&
                p.elapsed >= 0 && e.elapsed >= p.elapsed &&
                // Gap-spanning differences are not continuous distance. Keep the raw endpoints;
                // a separate scale-only projection can expose them without inventing a route.
                odometerGaps.none { gap ->
                    (gap.lastDurableSeq == null || gap.lastDurableSeq < e.seq) &&
                        (gap.firstAfterSeq == null || gap.firstAfterSeq > p.seq)
                } &&
                e.km != null && p.km != null && e.km.isFinite() && p.km.isFinite() && e.km >= p.km && p.km >= 0) distance += e.km - p.km
            prior = if (e.quality in setOf("VALID", "RESET") && !e.sourceError && e.boot.isKnownIdentity() &&
                e.epoch.isKnownIdentity() && e.km != null && e.km >= 0 && e.km.isFinite()) e else null
        }
        val spans = linkedMapOf<String, Long>()
        var priorSpan: Fact? = null
        for (event in ordered) {
            if (event.quality !in setOf("FRESH", "VALID") || event.sourceError ||
                !event.boot.isKnownIdentity() || !event.epoch.isKnownIdentity() || event.elapsed < 0) {
                priorSpan = null
                continue
            }
            val previous = priorSpan
            val delta = if (previous != null && event.epoch == previous.epoch && event.boot == previous.boot &&
                event.elapsed >= previous.elapsed) event.elapsed - previous.elapsed else 0L
            spans[event.epoch] = (spans[event.epoch] ?: 0L) + delta
            priorSpan = event
        }
        return JourneySummary(sequence, locations.map { it.seq }, distance, gapCount,
            sequence.size > 2 && sequence.first() == sequence.last() && sequence.distinct().size > 1, spans,
            segments, unknownBoundaries.sortedWith(compareBy({ it.lastEvidenceSeq ?: Long.MIN_VALUE },
                { it.firstEvidenceSeq ?: Long.MAX_VALUE })), dwellEvidence)
    }

    private fun Fact.isUsableLocation(): Boolean = source == "LOCATION" && quality == "FRESH" && !sourceError &&
        lat != null && lon != null && lat.isFinite() && lon.isFinite() && lat in -90.0..90.0 && lon in -180.0..180.0

    private fun String.isKnownIdentity(): Boolean = isNotBlank() && !equals("unknown", ignoreCase = true)
}
