package dev.todaystrack.music

import kotlin.math.*

/** Optional on-device comparison against a caller-selected previous observed point set. Never identifies a place. */
object SpatialComparison {
    fun compare(current: List<Observation>,reference: List<Observation>): Map<String,Metric> {
        require(current.size.toLong()*reference.size<=2_000_000) { "REFERENCE_COMPARISON_WINDOW_REQUIRED" }
        fun usable(e: Observation)=e.source==Source.LOCATION && e.quality=="FRESH" && !e.sourceError && e.latitude?.let { it.isFinite() && it in -90.0..90.0 }==true && e.longitude?.let { it.isFinite() && it in -180.0..180.0 }==true &&
            e.sourceTimestampMs?.let { it>0 && it<=e.receivedAtUtcMs && e.receivedAtUtcMs-it<=10_000 }==true && e.sourceTimeBasis in setOf("UTC","SDK_UTC_MILLIS")
        val points=current.filter(::usable);val previous=reference.filter(::usable)
        if(points.isEmpty() || previous.isEmpty()) return emptyMap()
        fun separation(a: Observation,b: Observation): Double {
            val aLat=Math.toRadians(a.latitude!!);val bLat=Math.toRadians(b.latitude!!)
            val h=sin((aLat-bLat)/2).pow(2)+cos(aLat)*cos(bLat)*sin(Math.toRadians(a.longitude!!-b.longitude!!)/2).pow(2)
            return 12_742_017.6*asin(sqrt(h.coerceIn(0.0,1.0)))
        }
        val near=points.map { point->previous.minOf { separation(point,it) } }
        val evidence=points.map { Evidence(Source.LOCATION,it.seq,it.seq,"comparison against selected previous observed point set, not a confirmed revisit or route match") }
        return mapOf("referenceNearestMeanMeters" to Metric(near.average(),"m",evidence),
            "referenceExactMatchFraction" to Metric(near.count { it==0.0 }.toDouble()/near.size,"fraction",evidence))
    }
}
