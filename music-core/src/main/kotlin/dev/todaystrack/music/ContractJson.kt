package dev.todaystrack.music

import kotlinx.serialization.json.*

fun JsonObject.text(key: String)=getValue(key).jsonPrimitive.content.also { require(it.length<=20_000) { "TEXT_LIMIT" } }
fun JsonObject.array(key: String)=getValue(key).jsonArray
fun JsonObject.only(vararg keys: String) { require(this.keys.all { it in keys }) { "UNKNOWN_FIELD" } }
fun JsonObject.stringList(key: String)=array(key).map { it.jsonPrimitive.content }
private fun JsonObject.longOrNull(key: String)=this[key]?.takeUnless { it==JsonNull }?.jsonPrimitive?.long
private fun JsonObject.doubleOrNull(key: String)=this[key]?.takeUnless { it==JsonNull }?.jsonPrimitive?.double?.also { require(it.isFinite()) }
private fun JsonObject.stringOrNull(key: String)=this[key]?.takeUnless { it==JsonNull }?.jsonPrimitive?.content

fun readPreferences(o: JsonObject): MusicPreferences {
    o.only("genre","allowedGenres","excludedGenres","instruments","avoid","lyricMode","language","durationSeconds","variationId","recentGenres","recentArrangements","vocalStyle")
    val defaults=MusicPreferences()
    fun list(key: String,fallback: List<String>)=o[key]?.jsonArray?.map { it.jsonPrimitive.content.also { s->require(s.length<=200 && s.none(Char::isISOControl)) } }?.also { require(it.size<=30) } ?: fallback
    return MusicPreferences(o.stringOrNull("genre") ?: defaults.genre,list("allowedGenres",defaults.allowedGenres),
        list("excludedGenres",emptyList()),list("instruments",emptyList()),list("avoid",emptyList()),
        o.stringOrNull("lyricMode")?.let(LyricMode::valueOf) ?: defaults.lyricMode,o.stringOrNull("language") ?: defaults.language,
        o["durationSeconds"]?.jsonPrimitive?.int ?: defaults.durationSeconds,o.stringOrNull("variationId") ?: defaults.variationId,
        list("recentGenres",emptyList()),list("recentArrangements",emptyList()),list("vocalStyle",emptyList()))
}
fun readMaterial(o: JsonObject): MaterialFact {
    o.only("id","origin","text","attachmentId","fromMs","toMs","uncertainty","allowedInLyrics")
    return MaterialFact(o.text("id"),FactOrigin.valueOf(o.text("origin")),o.text("text"),o.stringOrNull("attachmentId"),
        o.longOrNull("fromMs"),o.longOrNull("toMs"),o.stringOrNull("uncertainty"),o["allowedInLyrics"]?.jsonPrimitive?.boolean ?: false)
}
fun readFeatures(o: JsonObject): JourneyFeatures {
    o.only("version","environment","metrics","sections","stateRuns","gapReasons","excluded","sourceStatuses","unknown","positionOrder","legacyReturnObserved")
    require(o.text("version")=="features-v3") { "FEATURE_VERSION_REQUIRED" }
    fun evidence(element: JsonElement): Evidence {
        val e=element.jsonObject;e.only("source","fromSeq","toSeq","meaning","coverageRef")
        val from=e.getValue("fromSeq").jsonPrimitive.long;val to=e.getValue("toSeq").jsonPrimitive.long
        require(from>=0 && to>=from)
        return Evidence(Source.valueOf(e.text("source")),from,to,e.text("meaning").also { require(it.length<=200) },e.stringOrNull("coverageRef"))
    }
    fun evidenceList(e: JsonObject)=e.array("evidence").also { require(it.size<=20_000) }.map(::evidence)
    val permittedMetrics=setOf("continuousDistanceKm","gapSpanningDistanceKm","unallocatedBoundaryDistanceKm",
        "observedPointDisplacementMeters","directionChangeDegrees","spatialExtentFromFirstMeters","exactPositionRepeatFraction",
        "startEndDistanceMeters","observedSectionSpansMs","speedMeanMps","speedMin","speedMax","speedObservedChange",
        "altitudeMean","altitudeMin","altitudeMax","altitudeObservedChange","headingMean","headingMin","headingMax","headingObservedChange",
        "steeringMean","steeringMin","steeringMax","steeringObservedChange",
        "distanceEarlyFraction","distanceMiddleFraction","distanceLateFraction","distanceUnallocatedPhaseFraction",
        "referenceNearestMeanMeters","referenceExactMatchFraction")
    val metrics=o.getValue("metrics").jsonObject.mapValues { (key,value)->
        require(key in permittedMetrics) { "UNSUPPORTED_METRIC" }
        val m=value.jsonObject;m.only("value","unit","evidence")
        val number=m.getValue("value").jsonPrimitive.double;require(number.isFinite())
        if(key !in setOf("altitudeMean","altitudeMin","altitudeMax","steeringMean","steeringMin","steeringMax")) require(number>=0)
        val unit=m.text("unit");val expected=when {
            key.endsWith("Km")->"km";key.endsWith("Meters") || key.startsWith("altitude")->"m"
            key.startsWith("speed")->"m/s";key.endsWith("Ms")->"ms";key.endsWith("Fraction")->"fraction";else->"degrees"
        }
        require(unit==expected && (!key.endsWith("Fraction") || number<=1)) { "INVALID_METRIC_UNIT_OR_RANGE" }
        Metric(number,unit,evidenceList(m))
    }
    val sections=o.array("sections").also { require(it.size<=2000) }.map { value->
        val s=value.jsonObject;s.only("id","evidence","observedSpanMs","positionOrder","repeatedPositionSpanMs","directionChangeDegrees")
        val span=s.longOrNull("observedSpanMs");val repeated=s.getValue("repeatedPositionSpanMs").jsonPrimitive.long
        require(span==null || span>=0);require(repeated>=0 && (span==null && repeated==0L || span!=null && repeated<=span))
        val order=s.stringList("positionOrder");require(order.size<=200_000 && order.all { it.matches(Regex("(?:[A-Z]|P[0-9]+)")) })
        FeatureSection(s.text("id"),evidenceList(s),span,order,repeated,s.doubleOrNull("directionChangeDegrees"))
    }
    val runs=o.array("stateRuns").also { require(it.size<=2000) }.map { value->
        val s=value.jsonObject;s.only("source","state","evidence","observedSpanMs")
        val source=Source.valueOf(s.text("source"));require(source in setOf(Source.BRAKE,Source.DRIVING_MODE,Source.WIPER,Source.LIGHT,Source.GEAR))
        val state=s.text("state");require(state.length in 1..80)
        val span=s.longOrNull("observedSpanMs");require(span==null || span>=0)
        StateRun(source,state,evidenceList(s),span)
    }
    val statuses=o.array("sourceStatuses").map { value->val s=value.jsonObject;s.only("source","availability","receiptVerified","coverageRef")
        SourceStatus(Source.valueOf(s.text("source")),Availability.valueOf(s.text("availability")),s.getValue("receiptVerified").jsonPrimitive.boolean,s.stringOrNull("coverageRef")) }
    require(statuses.map { it.source to it.coverageRef }.distinct().size==statuses.size)
    fun verified(source: Source,ref: String?)=statuses.any { it.source==source && it.coverageRef==ref && it.availability==Availability.AVAILABLE && it.receiptVerified }
    for(prefix in listOf("speed" to Source.SPEED,"altitude" to Source.ALTITUDE,"heading" to Source.HEADING,"steering" to Source.STEERING))
        require(metrics.filterKeys { it.startsWith(prefix.first) }.values.all { m->m.evidence.isNotEmpty() && m.evidence.all { verified(prefix.second,it.coverageRef) } }) { "UNVERIFIED_SOURCE_FEATURE" }
    require(runs.all { run->run.evidence.all { verified(run.source,it.coverageRef) } }) { "UNVERIFIED_SOURCE_FEATURE" }
    val excluded=o.getValue("excluded").jsonObject.mapKeys { (key,_)->key.toLong().also { require(it>=0) } }.mapValues { it.value.jsonPrimitive.content }
    val order=o.stringList("positionOrder");require(order.size<=200_000 && order.all { it.matches(Regex("(?:[A-Z]|P[0-9]+)")) })
    return JourneyFeatures(environment=Environment.valueOf(o.text("environment")),metrics=metrics,sections=sections,stateRuns=runs,
        gapReasons=o.stringList("gapReasons"),excluded=excluded,sourceStatuses=statuses,unknown=o.stringList("unknown"),positionOrder=order,
        legacyReturnObserved=o.getValue("legacyReturnObserved").jsonPrimitive.boolean)
}

fun readContext(o: JsonObject): ProvidedContext {
    o.only("id","kind","description","source","sourceAtUtcMs","receivedAtUtcMs","confirmation","lyricsAllowed")
    return ProvidedContext(o.text("id"),o.text("kind"),o.text("description"),o.text("source"),o.getValue("sourceAtUtcMs").jsonPrimitive.long,
        o.getValue("receivedAtUtcMs").jsonPrimitive.long,o.text("confirmation"),o["lyricsAllowed"]?.jsonPrimitive?.boolean ?: false).also { it.material() }
}

fun readMediaSelection(o: JsonObject): MediaSelection {
    o.only("kind","sha256","mimeType","bytes","durationMs","fromMs","toMs","consent")
    val c=o.getValue("consent").jsonObject
    c.only("revision","selectedByUser","externalAnalysis","lyricsAllowed","directMusicImage","includeSpeech","revoked")
    fun flag(key: String)=c[key]?.jsonPrimitive?.boolean ?: false
    return MediaSelection(MediaKind.valueOf(o.text("kind")),o.text("sha256"),o.text("mimeType"),o.getValue("bytes").jsonPrimitive.long,
        o.longOrNull("durationMs"),o.longOrNull("fromMs"),o.longOrNull("toMs"),MediaConsent(c.getValue("revision").jsonPrimitive.long,
            flag("selectedByUser"),flag("externalAnalysis"),flag("lyricsAllowed"),flag("directMusicImage"),flag("includeSpeech"),flag("revoked")))
}
fun mediaSelectionJson(s: MediaSelection)=buildJsonObject {
    put("kind",s.kind.name);put("sha256",s.sha256);put("mimeType",s.mimeType);put("bytes",s.bytes)
    put("durationMs",s.durationMs?.let(::JsonPrimitive) ?: JsonNull);put("fromMs",s.fromMs?.let(::JsonPrimitive) ?: JsonNull);put("toMs",s.toMs?.let(::JsonPrimitive) ?: JsonNull)
    put("consent",buildJsonObject { put("revision",s.consent.revision);put("selectedByUser",s.consent.selectedByUser)
        put("externalAnalysis",s.consent.externalAnalysis);put("lyricsAllowed",s.consent.lyricsAllowed);put("directMusicImage",s.consent.directMusicImage)
        put("includeSpeech",s.consent.includeSpeech);put("revoked",s.consent.revoked) })
}
