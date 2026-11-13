package dev.todaystrack.music

/** Music input for ordered selections of trip/day/month sources. Album UI, storage and sync stay outside this module. */
object FeatureSelection {
    fun combine(ordered: List<Pair<String,JourneyFeatures>>): JourneyFeatures {
        require(ordered.isNotEmpty() && ordered.size<=1000 && ordered.map { it.first }.distinct().size==ordered.size)
        require(ordered.all { it.first.matches(Regex("[A-Za-z0-9_-]{1,100}/[A-Za-z0-9_-]{1,100}")) })
        fun scope(ref: String,list: List<Evidence>)=list.map { it.copy(coverageRef=ref) }
        val additive=setOf("continuousDistanceKm","gapSpanningDistanceKm","unallocatedBoundaryDistanceKm","observedPointDisplacementMeters","directionChangeDegrees","observedSectionSpansMs")
        val metrics=additive.mapNotNull { key->
            val present=ordered.mapNotNull { (ref,f)->f.metrics[key]?.let { ref to it } }
            present.takeIf { it.isNotEmpty() }?.let { key to Metric(it.sumOf { p->p.second.value },it.first().second.unit,it.flatMap { p->scope(p.first,p.second.evidence) }) }
        }.toMap()
        var label=0
        val scoped=ordered.mapIndexed { index,(ref,f)->
            val names=f.positionOrder.distinct().associateWith { "P${++label}" }
            f.copy(sections=f.sections.map { s->s.copy(id="selection-${index+1}-${s.id}",evidence=scope(ref,s.evidence),positionOrder=s.positionOrder.map { names.getValue(it) }) },
                stateRuns=f.stateRuns.map { it.copy(evidence=scope(ref,it.evidence)) },positionOrder=f.positionOrder.map { names.getValue(it) },
                sourceStatuses=f.sourceStatuses.map { it.copy(coverageRef=ref) })
        }
        val environment=ordered.map { it.second.environment }.distinct().singleOrNull() ?: Environment.UNKNOWN
        return JourneyFeatures(environment=environment,metrics=metrics,sections=scoped.flatMap { it.sections },stateRuns=scoped.flatMap { it.stateRuns },
            gapReasons=(scoped.flatMap { it.gapReasons }+"SELECTED_SOURCE_BOUNDARIES_UNKNOWN").distinct(),excluded=emptyMap(),sourceStatuses=scoped.flatMap { it.sourceStatuses },
            unknown=(scoped.flatMap { it.unknown }+listOf("cross-selection route, chronology beyond supplied order, visits and return", "excluded observations remain in each source's local feature result", "non-additive metrics are not inferred across selected sources")).distinct(),positionOrder=scoped.flatMap { it.positionOrder },legacyReturnObserved=false)
    }
}
