package dev.todaystrack.server.music

import dev.todaystrack.music.*
import kotlinx.serialization.json.*
import java.io.File

/** Missing/invalid/expired approval is zero authority. Legacy dispatch.json never enables v2. */
data class ExecutionPolicy(val analysisMicros: Long=0,val musicMicros: Long=0,val maxRequests: Int=0,
    val totalMicros: Long=0,val expiresAtMs: Long=0,val authorizationQuote: String="",val testing: Boolean=false) {
    fun enabled()=analysisMicros>0 && musicMicros>0 && maxRequests in 1..100_000 && totalMicros in 1..5_000_000 &&
        expiresAtMs>System.currentTimeMillis() && authorizationQuote.isNotBlank() &&
        (testing || (analysisMicros>=PricingBound.analysisMicros && musicMicros>=PricingBound.musicMicros && expiresAtMs<=PricingBound.validUntilMs))
    fun hash()=contentHash(buildJsonObject { put("analysisMicros",analysisMicros);put("musicMicros",musicMicros)
        put("maxRequests",maxRequests);put("totalMicros",totalMicros);put("expiresAtMs",expiresAtMs);put("authorizationQuote",authorizationQuote);put("testing",testing) })
    companion object {
        fun testing(analysisMicros: Long=1000,musicMicros: Long=100_000)=ExecutionPolicy(analysisMicros,musicMicros,100,5_000_000,Long.MAX_VALUE,"LOCAL TEST TRANSPORT ONLY",true)
        fun read(root: File): ExecutionPolicy=runCatching {
            val f=File(root,"music-v2-authorization.json")
            if(!f.isFile || f.length()>16_384 || File(root,"billing-stop").exists()) return ExecutionPolicy()
            val o=musicJson.parseToJsonElement(f.readText()).jsonObject
            o.only("enabled","analysisMicros","musicMicros","maxRequests","totalMicros","expiresAtMs","authorizationQuote")
            if(o.getValue("enabled").jsonPrimitive.boolean!=true) return ExecutionPolicy()
            ExecutionPolicy(o.getValue("analysisMicros").jsonPrimitive.long,o.getValue("musicMicros").jsonPrimitive.long,
                o.getValue("maxRequests").jsonPrimitive.int,o.getValue("totalMicros").jsonPrimitive.long,
                o.getValue("expiresAtMs").jsonPrimitive.long,o.text("authorizationQuote"))
        }.getOrDefault(ExecutionPolicy())
    }
}

/** Conservative full model-context/output bound, not an invoice estimate. Reverify pricing after this snapshot expires. */
object PricingBound {
    const val version="google-standard-2026-11-14"
    const val analysisMicros=1_032_192L // 1,048,576 * $0.75/M + 65,536 * $3.75/M (includes thinking).
    const val musicMicros=80_000L
    val validUntilMs=java.time.Instant.parse("2027-01-01T00:00:00Z").toEpochMilli()
}
