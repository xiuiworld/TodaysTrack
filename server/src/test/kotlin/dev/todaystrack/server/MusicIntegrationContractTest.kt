package dev.todaystrack.server

import dev.todaystrack.music.*
import dev.todaystrack.server.music.*
import kotlinx.serialization.json.*
import java.nio.file.Files
import java.util.Base64
import org.junit.Test
import kotlin.test.*

class MusicIntegrationContractTest {
    private val car=MusicPrincipal("a","car",DeviceRole.CAR)
    private fun body(intent: String,media: List<String> = emptyList(),excluded: List<String> = emptyList())=buildJsonObject {
        put("intentId",intent);put("operation","INITIAL");put("features",featuresJson(JourneyFeatures(environment=Environment.SIMULATED,metrics=emptyMap(),sections=emptyList(),stateRuns=emptyList(),gapReasons=listOf("NO_USABLE_OBSERVATIONS"),excluded=emptyMap(),sourceStatuses=emptyList(),unknown=listOf("true driving duration"),positionOrder=emptyList(),legacyReturnObserved=false)))
        put("preferences",preferencesJson(MusicPreferences(genre="FUNK",variationId=intent)));put("materials",JsonArray(emptyList()));put("mediaIds",JsonArray(media.map(::JsonPrimitive)));put("excludedMediaIds",JsonArray(excluded.map(::JsonPrimitive)))
        put("coverage",buildJsonArray { add(buildJsonObject { put("deviceId","car");put("sessionId","s");put("fromSeq",1);put("toSeqExclusive",10) }) })
    }
    private fun output(content: JsonArray)=buildJsonObject { put("steps",buildJsonArray { add(buildJsonObject { put("type","model_output");put("content",content) }) }) }.toString()
    private fun music()=output(buildJsonArray {
        add(buildJsonObject { put("type","audio");put("mime_type","audio/mpeg");put("data",Base64.getEncoder().encodeToString(ByteArray(2048) { 1 })) })
        add(buildJsonObject { put("type","text");put("text","[Verse]\n오늘의 빛") });add(buildJsonObject { put("type","text");put("text","{\"sections\":[\"verse\"]}") })
    })
    private fun analyzer(category: String,empty: Boolean=false)=output(buildJsonArray { add(buildJsonObject {
        put("type","text");put("text",buildJsonObject {
            put("statements",buildJsonArray { if(!empty) add(buildJsonObject { put("category",category);put("text","Selected source descriptor");put("fromMs",JsonNull);put("toMs",JsonNull);put("uncertainty","Only supported source content") }) })
            put("uncertainties",buildJsonArray { })
        }.toString())
    }) })
    private val inspection=AudioInspection { AudioInspectionResult(90000,false) }
    private val processor=MediaProcessor { bytes,selection->ProcessedMedia(bytes,selection.mimeType,selection.durationMs) }
    @Test fun allManualSourcesCanBeAnalyzedReviewedAndReusedBeforeMusicWithoutResendingThem() {
        MusicService(Files.createTempDirectory("all-materials").toFile(),ExecutionPolicy.testing(),ByteArray(32),processor).use { s ->
            val ids=MediaKind.entries.filter { it!=MediaKind.MEMO }.map { kind->
                val bytes=byteArrayOf(1,2,3);val mime=when(kind) { MediaKind.PHOTO->"image/png";MediaKind.VIDEO->"video/mp4";else->"audio/wav" }
                val selection=MediaSelection(kind,contentHash(bytes),mime,3,if(kind==MediaKind.PHOTO) null else 1000,consent=MediaConsent(1,true,true,directMusicImage=kind==MediaKind.PHOTO))
                val id=s.registerMedia(car,selection).text("mediaId");s.uploadMedia(car,id,bytes);id
            }
            val input=s.createInput(car,body("all",ids)).text("inputId")
            val quote=s.quote(car,input,true);val analysis=s.submit(car,input,quote.text("quoteId"),"analysis").text("jobId")
            var analyses=0;var songs=0
            val transport=ModelTransport { permit,request->permit.verify(request,true)
                val r=musicJson.parseToJsonElement(request).jsonObject
                if(r.text("model")=="lyria-3.5") { songs++;assertEquals(2,r.array("input").size);music() }
                else { analyses++;val prompt=r.array("input")[0].jsonObject.text("text")
                    assertEquals(false,r.getValue("store").jsonPrimitive.boolean)
                    analyzer(when { prompt.contains("as TRANSCRIPT")->"TRANSCRIPT";prompt.contains("as SCENE")->"SCENE";prompt.contains("as AUDIO")->"AUDIO";else->"MUSIC_CONTOUR" }) }
            }
            val worker=MusicPipeline(s,transport,inspection)
            repeat(6) { assertTrue(worker.processNext()) };assertEquals("PLAN_READY",s.job(car,analysis).text("state"));assertEquals(5,analyses);assertEquals(0,songs)
            val preview=s.preview(car,input);assertTrue(preview.getValue("previewComplete").jsonPrimitive.boolean);assertEquals(5,preview.getValue("plan").jsonObject.array("allowedFacts").size)
            val musicQuote=s.quote(car,input);assertEquals(0,musicQuote.getValue("analysisCount").jsonPrimitive.int)
            assertFails { s.submit(car,input,musicQuote.text("quoteId"),"analysis") } // An intent cannot change operation kind.
            val song=s.submit(car,input,musicQuote.text("quoteId"),"music").text("jobId");assertTrue(worker.processNext())
            assertEquals("READY",s.job(car,song).text("state"));assertEquals(1,songs);assertEquals(5,analyses)
            assertTrue(s.job(car,song).text("returnedStructure").contains("verse"))
            assertFalse(s.root.walkTopDown().any { it.extension=="enc" })
        }
    }
    @Test fun inconclusiveMaterialIsHeldUntilTheUserExplicitlyExcludesIt() {
        MusicService(Files.createTempDirectory("material-hold").toFile(),ExecutionPolicy.testing(),ByteArray(32),processor).use { s ->
            val bytes=byteArrayOf(1);val sel=MediaSelection(MediaKind.HUMMING,contentHash(bytes),"audio/wav",1,1000,consent=MediaConsent(1,true,true))
            val media=s.registerMedia(car,sel).text("mediaId");s.uploadMedia(car,media,bytes)
            val input=s.createInput(car,body("input",listOf(media))).text("inputId");val id=s.submit(car,input,s.quote(car,input).text("quoteId"),"job").text("jobId")
            var sends=0
            val worker=MusicPipeline(s,ModelTransport { _,request->sends++;if(request.contains("gemini-3.8-flash")) analyzer("MUSIC_CONTOUR",true) else music() },inspection)
            assertTrue(worker.processNext());assertEquals("MATERIAL_REVIEW_REQUIRED",s.job(car,id).text("state"));assertFalse(worker.processNext());assertEquals(1,sends)
            assertFalse(s.preview(car,input).getValue("previewComplete").jsonPrimitive.boolean)
            val excluded=s.createInput(car,body("excluded",listOf(media),listOf(media))).text("inputId")
            val next=s.submit(car,excluded,s.quote(car,excluded).text("quoteId"),"exclude-job").text("jobId")
            assertTrue(worker.processNext());assertEquals("READY",s.job(car,next).text("state"));assertEquals(2,sends)
        }
    }
    @Test fun savedResultAfterCrashRecoversLocallyWithoutAnotherProviderCall() {
        val root=Files.createTempDirectory("saved-result-recovery").toFile();val policy=ExecutionPolicy.testing();var id="";var sends=0
        class Crash: Error()
        MusicService(root,policy,ByteArray(32)).use { s ->
            val input=s.createInput(car,body("i")).text("inputId");id=s.submit(car,input,s.quote(car,input).text("quoteId"),"job").text("jobId")
            val worker=MusicPipeline(s,ModelTransport { _,_->sends++;music() },AudioInspection { throw Crash() })
            assertFailsWith<Crash> { worker.processNext() }
        }
        MusicService(root,policy,ByteArray(32)).use { s ->
            val worker=MusicPipeline(s,ModelTransport { _,_->error("must not resend saved result") },inspection)
            assertTrue(worker.processNext());assertEquals("READY",s.job(car,id).text("state"));assertEquals(1,sends);assertEquals(1,s.store.attemptCount())
        }
    }
    @Test fun deletingDuringDispatchSuppressesAudioAndFencesOverlappingSourceRanges() {
        MusicService(Files.createTempDirectory("delete-race").toFile(),ExecutionPolicy.testing(),ByteArray(32)).use { s ->
            val input=s.createInput(car,body("i")).text("inputId");val original=s.input(car,input)
            val id=s.submit(car,input,s.quote(car,input).text("quoteId"),"job").text("jobId")
            assertTrue(MusicPipeline(s,ModelTransport { _,_->s.deleteInput(car,input);music() },inspection).processNext())
            assertEquals("DELETED",s.job(car,id).text("state"));assertFalse(s.audioFile(car,id).exists())
            assertFails { s.createInput(car,body("replacement")) };assertFails { s.input(MusicPrincipal("b","other",DeviceRole.CAR),input) }
            assertEquals(original.text("inputHash"),s.store.input(car.ownerId,input)!!.getValue("hash"))
        }
    }
    @Test fun requestAndGlobalCostCapsRollbackAtomicallyWithoutOverbooking() {
        val policy=ExecutionPolicy.testing().copy(maxRequests=1)
        MusicService(Files.createTempDirectory("budget-cap").toFile(),policy,ByteArray(32)).use { s ->
            val a=s.createInput(car,body("a")).text("inputId");val b=s.createInput(car,body("b")).text("inputId")
            s.submit(car,a,s.quote(car,a).text("quoteId"),"a")
            val reserved=s.store.reservedMicros()
            assertFails { s.submit(car,b,s.quote(car,b).text("quoteId"),"b") };assertEquals(reserved,s.store.reservedMicros());assertEquals(0,s.store.attemptCount())
        }
    }
}
