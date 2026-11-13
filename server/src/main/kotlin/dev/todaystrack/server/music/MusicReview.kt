package dev.todaystrack.server.music

import dev.todaystrack.music.*
import kotlinx.serialization.json.*
import java.io.File

/** File-only inspection of the actual Kotlin engine. Never starts Ktor or a model transport. */
fun main(args: Array<String>) {
    require(args.size==2) { "Expected cases/report JSON and output JSON" }
    val source=File(args[0]);require(source.length()<=16*1024*1024)
    val target=File(args[1]).absoluteFile
    require(target.canonicalFile!=source.canonicalFile && !target.exists()) { "REVIEW_OUTPUT_MUST_BE_A_NEW_FILE" }
    val report=musicJson.parseToJsonElement(source.readText()).jsonObject
    val input=(report["input"] ?: report).jsonObject
    val rows=input.array("cases").map { e->val c=e.jsonObject
        val batch=readReviewBatch(c)
        val features=FeatureExtractor().extract(batch)
        val preferences=MusicPreferences(genre="FUNK",variationId="review-v2")
        val note=MaterialFact("note",FactOrigin.USER,"오늘은 오래 미뤘던 일을 마치고 작은 기념을 남기고 싶다.",allowedInLyrics=true)
        fun version(p: MusicPreferences,materials: List<MaterialFact>)=CompositionPlanner().build(features,p,materials).let { plan->buildJsonObject {
            put("plan",planJson(plan));put("planHash",contentHash(planJson(plan)));put("request",MusicRequest.build(MusicPromptBuilder().build(plan,features))) } }
        val old=report["baseline"]?.jsonObject?.array("cases")?.firstOrNull { it.jsonObject.text("id")==c.text("id") }
        buildJsonObject {
            put("id",c.getValue("id"));put("title",c["title"] ?: c.getValue("id"));put("origin",c["origin"] ?: JsonPrimitive("PRESERVED_SDK_INPUT; NOT_A_NEW_RECEIPT_CHECK"))
            put("rawInput",c);put("features",featuresJson(features));put("featureHash",contentHash(featuresJson(features)))
            put("excludedObservations",buildJsonObject { features.excluded.forEach { (seq,reason)->put(seq.toString(),reason) } })
            put("baselineFromPreservedReport",old ?: JsonNull)
            put("withoutMemo",version(preferences,emptyList()));put("withMemo",version(preferences,listOf(note)))
            put("alternativeGenre",version(preferences.copy(genre="DREAM_POP"),emptyList()))
            put("intentionalVariation",version(preferences.copy(variationId="review-v2-b"),emptyList()))
        }
    }
    val output=buildJsonObject { put("schemaVersion",2);put("engine","actual Kotlin music-core + server MusicRequest")
        put("externalCalls",0);put("sourceFileHash",contentHash(source.readBytes()));put("cases",JsonArray(rows))
        put("limits",JsonArray(listOf("No new SDK/Android integration check", "No generated audio or listening assessment", "Legacy baseline is preserved report evidence; it was not re-executed by this tool").map(::JsonPrimitive))) }
    target.apply { parentFile.mkdirs();check(createNewFile());writeText(Json { prettyPrint=true }.encodeToString(JsonObject.serializer(),output)) }
    println("Reviewed ${rows.size} cases using Kotlin; external calls=0; output=${File(args[1]).absolutePath}")
}

fun readReviewBatch(c: JsonObject): ObservationBatch {
    fun JsonObject.longOpt(key: String)=this[key]?.takeUnless { it==JsonNull }?.jsonPrimitive?.long
    fun JsonObject.doubleOpt(key: String)=this[key]?.takeUnless { it==JsonNull }?.jsonPrimitive?.double
    val events=c.array("events").map { element->val e=element.jsonObject;val s=Source.valueOf(e.text("source"))
        Observation(e.getValue("seq").jsonPrimitive.long,s,e.text("epochId"),e.text("bootId"),e.getValue("receivedElapsedMs").jsonPrimitive.long,
            e.getValue("receivedAtUtc").jsonPrimitive.long,e.text("quality"),e.longOpt("sourceTimestampMs"),e["sourceTimeBasis"]?.jsonPrimitive?.content ?: "SDK_UTC_MILLIS",
            e.doubleOpt("latitude"),e.doubleOpt("longitude"),e.doubleOpt("odometerKm") ?: e.doubleOpt("value"),if(s==Source.ODOMETER) "km" else e["unit"]?.jsonPrimitive?.content,
            e["state"]?.jsonPrimitive?.content,sourceError=e["detail"]?.takeUnless { it==JsonNull }?.jsonPrimitive?.content?.contains("SOURCE_ERROR")==true,requestStartedElapsedMs=e.longOpt("requestStartedElapsedMs"),epochStartedUtcMs=c["epochStartsUtc"]?.jsonObject?.get(e.text("epochId"))?.jsonPrimitive?.long) }
    val gaps=c["gaps"]?.jsonArray.orEmpty().map { e->val g=e.jsonObject;Gap(g.text("sourceMask").split('|').map { Source.valueOf(it.trim()) }.toSet(),g.longOpt("lastDurableSeq"),g.longOpt("firstAfterSeq"),g["reason"]?.jsonPrimitive?.content ?: "REVIEW_INPUT") }
    return ObservationBatch(events,gaps,environment=Environment.SIMULATED)
}
