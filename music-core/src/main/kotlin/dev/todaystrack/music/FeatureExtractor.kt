package dev.todaystrack.music

import dev.todaystrack.music.legacy.Fact
import dev.todaystrack.music.legacy.JourneySummaryBuilder
import dev.todaystrack.music.legacy.LocationGapBoundary
import kotlin.math.*

/** Local-only projection. Coordinates stay in ObservationBatch, never JourneyFeatures. */
class FeatureExtractor {
    fun extract(batch: ObservationBatch): JourneyFeatures {
        require(batch.observations.size <= 200_000) { "OBSERVATION_LIMIT" }
        require(batch.observations.map { it.seq }.distinct().size == batch.observations.size) { "DUPLICATE_SEQ" }
        require(batch.observations.all { it.seq >= 0 }) { "INVALID_SEQ" }
        require(batch.gaps.all { it.sources.isNotEmpty() && (it.lastDurableSeq == null || it.firstAfterSeq == null || it.lastDurableSeq < it.firstAfterSeq) }) { "INVALID_GAP" }
        require(batch.statuses.map { it.source }.distinct().size == batch.statuses.size) { "DUPLICATE_SOURCE_STATUS" }
        require(batch.boundarySeqs.all { it>=0 })
        val gaps=batch.gaps+batch.boundarySeqs.filter { it>0 }.map { Gap(Source.entries.toSet(),it-1,it,"INPUT_BOUNDARY") }
        val events = batch.observations.sortedBy { it.seq }
        val rejected = linkedMapOf<Long, String>()
        val statuses = batch.statuses.associateBy { it.source }
        val sourceTimes=mutableMapOf<Triple<Source,String,String>,Long>()
        val valid = events.filter { e ->
            var reason = rejection(e, statuses[e.source])
            if(reason==null && e.source==Source.LOCATION) {
                val domain=Triple(e.source,e.bootId,e.epochId);val previous=sourceTimes[domain]
                reason=when { previous==e.sourceTimestampMs->"DUPLICATE_SOURCE_TIME"
                    previous!=null && e.sourceTimestampMs!!<previous->"OUT_OF_ORDER_SOURCE_TIME";else->null }
                if(reason==null) sourceTimes[domain]=e.sourceTimestampMs!!
            }
            if (reason != null) rejected[e.seq] = reason
            reason == null
        }
        val validSeqs = valid.map { it.seq }.toSet()
        val legacy = JourneySummaryBuilder().build(events.filter { it.source in setOf(Source.LOCATION, Source.ODOMETER) }.map { e ->
            Fact(e.seq,e.epochId,e.bootId,e.receivedElapsedMs,e.source.name,
                if(e.seq !in validSeqs) "INVALID" else e.quality,e.latitude,e.longitude,e.value,e.sourceError)
        },gaps.size,gaps.map { LocationGapBoundary(it.lastDurableSeq,it.firstAfterSeq,it.sources.joinToString("|") { s -> s.name }) })
        val metrics = linkedMapOf<String, Metric>()
        fun put(name: String,value: Double,unit: String,evidence: List<Evidence>) {
            if(value.isFinite()) metrics[name]=Metric(value,unit,compactEvidence(evidence))
        }
        val distanceEvidence=mutableMapOf("continuousDistanceKm" to mutableListOf<Evidence>(),
            "gapSpanningDistanceKm" to mutableListOf(),"unallocatedBoundaryDistanceKm" to mutableListOf())
        val distanceTotals=distanceEvidence.keys.associateWith { 0.0 }.toMutableMap()
        var prior: Observation? = null
        for(e in events.filter { it.source==Source.ODOMETER }) {
            val p=prior
            if(e.seq in validSeqs && e.quality=="VALID" && p!=null && comparable(p,e) && e.value!!>=p.value!!) {
                val key=when {
                    batch.boundarySeqs.any { it>p.seq && it<=e.seq } -> "unallocatedBoundaryDistanceKm"
                    batch.gaps.any { it.crosses(p,e,Source.ODOMETER) } -> "gapSpanningDistanceKm"
                    else -> "continuousDistanceKm"
                }
                distanceTotals[key]=distanceTotals.getValue(key)+e.value-p.value
                distanceEvidence.getValue(key).add(Evidence(Source.ODOMETER,p.seq,e.seq,
                    if(key=="continuousDistanceKm") "continuous cumulative-distance difference" else "scale only; no path, time or section allocation"))
            }
            prior=e.takeIf { it.seq in validSeqs && known(it.bootId) && known(it.epochId) }
        }
        distanceTotals.forEach { (key,value)->put(key,value,"km",distanceEvidence.getValue(key)) }
        // Allocate only intervals wholly within a third of their own comparable source run.
        // A sparse interval crossing a phase boundary stays unallocated; its distance is never interpolated.
        val phaseTotals=DoubleArray(4);val phaseEvidence=Array(4) { mutableListOf<Evidence>() }
        for(run in splitRuns(events,validSeqs,Source.ODOMETER,gaps)) {
            val first=run.first().receivedElapsedMs;val span=run.last().receivedElapsedMs-first
            for((a,b) in run.zipWithNext()) {
                if(b.quality!="VALID" || b.value!!<a.value!! || !comparable(a,b)) continue
                val start=if(span>0) (((a.receivedElapsedMs-first).toDouble()/span)*3).toInt().coerceIn(0,2) else 3
                val end=if(span>0) (((b.receivedElapsedMs-first-1).coerceAtLeast(0).toDouble()/span)*3).toInt().coerceIn(0,2) else 3
                val bucket=if(start==end) start else 3
                phaseTotals[bucket]+=b.value-a.value
                phaseEvidence[bucket].add(Evidence(Source.ODOMETER,a.seq,b.seq,"source-run phase interval; straddling boundaries unallocated"))
            }
        }
        val phaseSum=phaseTotals.sum()
        if(phaseSum>0) listOf("distanceEarlyFraction","distanceMiddleFraction","distanceLateFraction","distanceUnallocatedPhaseFraction").forEachIndexed { i,key->put(key,phaseTotals[i]/phaseSum,"fraction",phaseEvidence[i]) }

        val bySeq=valid.associateBy { it.seq }
        val sections=legacy.positionSegments.flatMapIndexed { index,segment ->
            val points=segment.positionEvidenceSeqs.map { bySeq.getValue(it) }
            // Group adjacent observed intervals by exact-repeat vs changed-position. Shared
            // endpoints do not double-count time. Neither type asserts motion or a stop.
            val chunks=mutableListOf<MutableList<Int>>()
            for(i in 0 until points.lastIndex) {
                val repeat=segment.positionSequence[i]==segment.positionSequence[i+1]
                val priorIndex=chunks.lastOrNull()?.lastOrNull()
                val priorRepeat=priorIndex?.let { segment.positionSequence[it]==segment.positionSequence[it+1] }
                if(priorRepeat!=repeat) chunks.add(mutableListOf())
                chunks.last().add(i)
            }
            if(chunks.isEmpty()) chunks.add(mutableListOf())
            chunks.mapIndexed { part,indices ->
                val start=indices.firstOrNull() ?: 0;val end=indices.lastOrNull()?.plus(1) ?: 0
                val group=points.subList(start,end+1);val first=group.first();val last=group.last()
                val span=if(comparable(first,last)) last.receivedElapsedMs-first.receivedElapsedMs else null
                val order=segment.positionSequence.subList(start,end+1)
                val repeated=order.distinct().size==1 && order.size>1
                val turns=group.zipWithNext().filter { (a,b)->a.latitude!=b.latitude || a.longitude!=b.longitude }
                    .map { (a,b)->bearing(a,b) }.zipWithNext().sumOf { (a,b)->angleDifference(a,b) }
                FeatureSection("position-${index+1}-${part+1}",listOf(Evidence(Source.LOCATION,first.seq,last.seq,"observed position interval; no interpolated path")),
                    span,order,if(repeated) span ?: 0 else 0,turns.takeIf { group.size>=3 })
            }
        }.toMutableList()
        if(sections.isEmpty()) {
            // Odometer-only runs still supply scale/order evidence, never places or a GPS route.
            splitRuns(events,validSeqs,Source.ODOMETER,gaps).forEachIndexed { i,run ->
                sections.add(FeatureSection("odometer-${i+1}",listOf(Evidence(Source.ODOMETER,run.first().seq,run.last().seq,"odometer observation span, not driving duration")),
                    (run.last().receivedElapsedMs-run.first().receivedElapsedMs).takeIf { comparable(run.first(),run.last()) },emptyList()))
            }
        }
        val locationRuns=legacy.positionSegments.map { s->s.positionEvidenceSeqs.map { bySeq.getValue(it) } }
        val allPositions=locationRuns.flatten()
        val geometryEvidence=locationRuns.flatMap { run->run.zipWithNext().map { (a,b)->Evidence(Source.LOCATION,a.seq,b.seq,"observed point displacement, not road length") } }
        if(geometryEvidence.isNotEmpty()) {
            put("observedPointDisplacementMeters",locationRuns.sumOf { run->run.zipWithNext().sumOf { (a,b)->distance(a,b) } },"m",geometryEvidence)
            val changes=locationRuns.sumOf { run->run.zipWithNext().filter { (a,b)->a.latitude!=b.latitude || a.longitude!=b.longitude }
                .map { (a,b)->bearing(a,b) }.zipWithNext().sumOf { (a,b)->angleDifference(a,b) } }
            put("directionChangeDegrees",changes,"degrees",geometryEvidence)
        }
        if(allPositions.isNotEmpty()) {
            val first=allPositions.first()
            put("spatialExtentFromFirstMeters",allPositions.maxOf { distance(first,it) },"m",
                allPositions.map { Evidence(Source.LOCATION,first.seq,it.seq,"observed separation from first position; no path claim") })
            val repeated=allPositions.size-legacy.positionSequence.distinct().size
            put("exactPositionRepeatFraction",repeated.toDouble()/allPositions.size,"fraction",
                allPositions.map { Evidence(Source.LOCATION,it.seq,it.seq,"exact repeated coordinate; not a visit") })
        }
        // Cross-gap endpoint matches do not become proximity/return evidence in the new projection.
        if(locationRuns.size==1 && allPositions.size>=2 && comparable(allPositions.first(),allPositions.last()) &&
            legacy.positionUnknownBoundaries.isEmpty()) {
            val first=allPositions.first();val last=allPositions.last()
            put("startEndDistanceMeters",distance(first,last),"m",listOf(Evidence(Source.LOCATION,first.seq,last.seq,"endpoint separation, not home or confirmed return")))
        }
        val validSpans=sections.filter { (it.observedSpanMs ?: 0)>0 }
        if(validSpans.isNotEmpty()) put("observedSectionSpansMs",validSpans.sumOf { it.observedSpanMs!! }.toDouble(),"ms",validSpans.flatMap { it.evidence })

        for(source in listOf(Source.SPEED,Source.ALTITUDE,Source.HEADING,Source.STEERING)) {
            val rows=valid.filter { it.source==source }
            if(rows.isEmpty()) continue
            val evidence=rows.map { Evidence(source,it.seq,it.seq,"direct source observation") }
            val values=rows.map { if(source==Source.SPEED) abs(it.value!!) else it.value!! }
            val prefix=when(source) { Source.SPEED->"speed";Source.ALTITUDE->"altitude";Source.HEADING->"heading";else->"steering" }
            val unit=rows.first().unit!!
            val mean=if(source==Source.HEADING) {
                val x=values.sumOf { cos(Math.toRadians(it)) };val y=values.sumOf { sin(Math.toRadians(it)) }
                if(hypot(x,y)<1e-9) null else (Math.toDegrees(atan2(y,x))+360)%360
            } else values.average()
            if(mean!=null) put(prefix+"Mean"+if(source==Source.SPEED) "Mps" else "",mean,unit,evidence)
            put(prefix+"Min",values.min(),unit,evidence);put(prefix+"Max",values.max(),unit,evidence)
            val changes=splitRuns(events,validSeqs,source,gaps).flatMap { it.zipWithNext() }
            if(changes.isNotEmpty()) put(prefix+"ObservedChange",changes.sumOf { (a,b)->if(source==Source.HEADING) angleDifference(a.value!!,b.value!!) else abs(b.value!!-a.value!!) },unit,
                changes.map { (a,b)->Evidence(source,a.seq,b.seq,"change between comparable source observations") })
        }
        val stateRuns=mutableListOf<StateRun>()
        for(source in listOf(Source.BRAKE,Source.DRIVING_MODE,Source.WIPER,Source.LIGHT,Source.GEAR)) {
            for(run in splitRuns(events,validSeqs,source,gaps)) {
                var start=0
                while(start<run.size) {
                    var end=start+1
                    while(end<run.size && run[end].state==run[start].state) end++
                    val a=run[start];val b=run[end-1]
                    stateRuns.add(StateRun(source,a.state!!,listOf(Evidence(source,a.seq,b.seq,"repeated state observations; not continuous coverage")),
                        (b.receivedElapsedMs-a.receivedElapsedMs).takeIf { comparable(a,b) }))
                    start=end
                }
            }
        }
        val unknown=mutableListOf("driver emotion", "companions", "place names without supplied confirmation", "events during gaps", "true driving duration")
        if(legacy.positionUnknownBoundaries.isNotEmpty() || allPositions.isEmpty()) unknown.add("position continuity and return across unknown intervals")
        val effectiveStatuses=Source.entries.map { source->statuses[source] ?: SourceStatus(source,
            if(valid.any { it.source==source }) Availability.AVAILABLE else Availability.UNVERIFIED,
            receiptVerified=false) }
        return JourneyFeatures(environment=batch.environment,metrics=metrics,sections=sections,stateRuns=stateRuns,
            gapReasons=(gaps.map { it.reason }+legacy.positionUnknownBoundaries.flatMap { it.reasons }).distinct(),
            excluded=rejected,sourceStatuses=effectiveStatuses,unknown=unknown,positionOrder=legacy.positionSequence,
            legacyReturnObserved=legacy.returnObserved)
    }

    private fun rejection(e: Observation,status: SourceStatus?): String? {
        if(status!=null && status.availability!=Availability.AVAILABLE) return status.availability.name
        if(e.source !in setOf(Source.LOCATION,Source.ODOMETER) && status?.receiptVerified!=true) return "UNVERIFIED_SOURCE"
        if(e.sourceError) return "SOURCE_ERROR"
        if(e.receivedElapsedMs<0) return "INVALID_ELAPSED_TIME"
        if(e.requestStartedElapsedMs!=null && (e.requestStartedElapsedMs<0 || e.requestStartedElapsedMs>e.receivedElapsedMs)) return "INVALID_REQUEST_TIME"
        if(e.quality !in setOf("FRESH","VALID","RESET")) return e.quality
        if(e.source==Source.LOCATION) {
            if(e.quality!="FRESH") return e.quality
            if(e.latitude==null || e.longitude==null || !e.latitude.isFinite() || !e.longitude.isFinite() || e.latitude !in -90.0..90.0 || e.longitude !in -180.0..180.0) return "INVALID_COORDINATE"
            if(e.sourceTimestampMs==null || e.sourceTimestampMs<=0 || e.sourceTimeBasis !in setOf("SDK_UTC_MILLIS","UTC")) return "UNKNOWN_TIME"
            if(e.epochStartedUtcMs!=null && (e.epochStartedUtcMs<=0 || e.sourceTimestampMs<e.epochStartedUtcMs)) return "PRE_EPOCH"
            if(e.sourceTimestampMs>e.receivedAtUtcMs) return "FUTURE_TIME"
            if(e.receivedAtUtcMs-e.sourceTimestampMs>10_000) return "STALE"
        } else if(e.source in setOf(Source.ODOMETER,Source.SPEED,Source.HEADING,Source.ALTITUDE,Source.STEERING)) {
            if(e.value==null || !e.value.isFinite()) return "INVALID_VALUE"
            val unit=when(e.source) { Source.ODOMETER->"km";Source.SPEED->"m/s";Source.ALTITUDE->"m";else->"degrees" }
            if(e.unit!=unit) return "UNSUPPORTED_UNIT"
            if(e.source==Source.ODOMETER && e.value<0) return "INVALID_VALUE"
            if(e.source==Source.HEADING && e.value !in 0.0..<360.0) return "INVALID_HEADING"
        } else if(e.state.isNullOrBlank() || e.state.length>80) return "INVALID_STATE"
        return null
    }

    private fun splitRuns(all: List<Observation>,valid: Set<Long>,source: Source,gaps: List<Gap>): List<List<Observation>> {
        val result=mutableListOf<MutableList<Observation>>()
        var prior: Observation?=null
        for(e in all.filter { it.source==source }) {
            if(e.seq !in valid || !known(e.bootId) || !known(e.epochId)) { prior=null;continue }
            val p=prior
            if(p==null || !comparable(p,e) || gaps.any { it.crosses(p,e,source) }) result.add(mutableListOf())
            result.last().add(e);prior=e
        }
        return result
    }
}

/** Merge overlapping/adjacent evidence ranges only, never synthesize observations in a gap. */
private fun compactEvidence(input: List<Evidence>): List<Evidence> {
    val result=mutableListOf<Evidence>()
    for(e in input) {
        val p=result.lastOrNull()
        if(p!=null && p.source==e.source && p.meaning==e.meaning && e.fromSeq<=p.toSeq && e.toSeq>=p.fromSeq) result[result.lastIndex]=p.copy(fromSeq=min(p.fromSeq,e.fromSeq),toSeq=max(p.toSeq,e.toSeq))
        else result.add(e)
    }
    return result
}

internal fun known(value: String)=value.isNotBlank() && !value.equals("unknown",true)
internal fun comparable(a: Observation,b: Observation)=known(a.bootId) && known(a.epochId) && a.bootId==b.bootId &&
    a.epochId==b.epochId && a.receivedElapsedMs>=0 && b.receivedElapsedMs>=a.receivedElapsedMs
internal fun Gap.crosses(a: Observation,b: Observation,source: Source)=source in sources &&
    (lastDurableSeq==null || lastDurableSeq<b.seq) && (firstAfterSeq==null || firstAfterSeq>a.seq)
private fun distance(a: Observation,b: Observation): Double {
    val lat1=Math.toRadians(a.latitude!!);val lat2=Math.toRadians(b.latitude!!)
    val dLat=lat2-lat1;val dLon=Math.toRadians(b.longitude!!-a.longitude!!)
    val h=sin(dLat/2).pow(2)+cos(lat1)*cos(lat2)*sin(dLon/2).pow(2)
    return 6_371_008.8*2*atan2(sqrt(h.coerceIn(0.0,1.0)),sqrt((1-h).coerceIn(0.0,1.0)))
}
private fun bearing(a: Observation,b: Observation): Double {
    val dLon=Math.toRadians(b.longitude!!-a.longitude!!)
    val aLat=Math.toRadians(a.latitude!!);val bLat=Math.toRadians(b.latitude!!)
    return (Math.toDegrees(atan2(sin(dLon)*cos(bLat),cos(aLat)*sin(bLat)-sin(aLat)*cos(bLat)*cos(dLon)))+360)%360
}
private fun angleDifference(a: Double,b: Double)=abs((b-a+540)%360-180)
