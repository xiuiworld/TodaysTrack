package dev.todaystrack.server

import dev.todaystrack.server.music.*
import kotlinx.serialization.json.*
import java.util.Base64
import org.junit.Test
import kotlin.test.*

class ProviderResultContractTest {
    @Test fun directImagesCannotIntroduceNewLyricsFactsOutsideExplicitPermittedAnalysis() {
        val request=MusicRequest.build("Original song",listOf(ProcessedMedia(byteArrayOf(1),"image/png",null)))
        assertTrue(request.getValue("input").jsonArray[0].jsonObject.getValue("text").jsonPrimitive.content.contains("Do not extract new lyrics facts from attached images"))
    }
    @Test fun jsonTextLyricsAndStructureStaySeparateAndExactReturnedTextIsPreserved() {
        val text="{\"lyrics\":\"오늘을 노래해\",\"structure\":[{\"type\":\"chorus\"}]}"
        val response=buildJsonObject { put("steps",buildJsonArray { add(buildJsonObject { put("type","model_output");put("content",buildJsonArray {
            add(buildJsonObject { put("type","text");put("text",text) })
            add(buildJsonObject { put("type","audio");put("mime_type","audio/mpeg");put("data",Base64.getEncoder().encodeToString(ByteArray(2048))) })
        }) }) }) }.toString()
        val parsed=musicResponse(response)
        assertEquals("오늘을 노래해",parsed.lyrics);assertTrue(parsed.structure.contains("chorus"));assertFalse(parsed.structure.contains("오늘을 노래해"));assertEquals(text,parsed.returnedText)
    }
}
