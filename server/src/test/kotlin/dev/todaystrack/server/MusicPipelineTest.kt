package dev.todaystrack.server

import dev.todaystrack.music.*
import dev.todaystrack.server.music.*
import kotlinx.serialization.json.*
import java.nio.file.Files
import java.util.Base64
import org.junit.Test
import kotlin.test.*

class MusicPipelineTest {
    private val car=MusicPrincipal("owner","car",DeviceRole.CAR)
    private val phone=MusicPrincipal("owner","phone",DeviceRole.PHONE)
    private fun initial()=buildJsonObject {
        put("intentId","input");put("operation","INITIAL")
        put("features",featuresJson(FeatureExtractor().extract(ObservationBatch(listOf(
            Observation(1,Source.ODOMETER,"e","b",10,10,"VALID",value=100.0,unit="km"),Observation(2,Source.ODOMETER,"e","b",20,20,"VALID",value=101.0,unit="km"))))))
        put("preferences",preferencesJson(MusicPreferences(genre="ROCK")));put("materials",JsonArray(emptyList()));put("mediaIds",JsonArray(emptyList()))
        put("coverage",buildJsonArray { add(buildJsonObject { put("deviceId","car");put("sessionId","s");put("fromSeq",1);put("toSeqExclusive",3) }) })
    }
    private fun response()=buildJsonObject { put("steps",buildJsonArray { add(buildJsonObject { put("type","model_output");put("content",buildJsonArray {
        add(buildJsonObject { put("type","audio");put("mime_type","audio/mpeg");put("data",Base64.getEncoder().encodeToString(ByteArray(2048) { 1 })) })
        add(buildJsonObject { put("type","text");put("text","[Verse]\n창문에 남은 빛\n[Chorus]\n다시 빛나네") })
    }) }) }) }.toString()
    @Test fun returnedSongIsSavedWithoutInventingLyricsAndPhoneCanOnlyRegenerateItsCoverage() {
        val policy=ExecutionPolicy.testing();val root=Files.createTempDirectory("music-pipeline").toFile()
        MusicService(root,policy,ByteArray(32)).use { s ->
            var sends=0
            val worker=MusicPipeline(s,ModelTransport { permit,request -> permit.verify(request,true);sends++;response() }, AudioInspection { AudioInspectionResult(90000,false) })
            val input=s.createInput(car,initial()).text("inputId");val quote=s.quote(car,input)
            val id=s.submit(car,input,quote.text("quoteId"),"job").text("jobId")
            assertTrue(worker.processNext());assertFalse(worker.processNext());assertEquals(1,sends)
            val job=s.job(car,id);assertEquals("READY",job.text("state"));assertEquals("LOCAL_TEST",job.text("mode"))
            assertTrue(job.text("returnedLyrics").contains("다시 빛나네"));assertFalse(job.getValue("newAiGeneration").jsonPrimitive.boolean)
            assertFalse(job.getValue("integrity").jsonObject.getValue("fullyDecoded").jsonPrimitive.boolean)
            val regen=buildJsonObject { put("intentId","phone-version");put("operation","REGENERATE");put("parentInputId",input)
                put("preferences",preferencesJson(MusicPreferences(genre="FUNK",variationId="2")));put("materials",JsonArray(emptyList()));put("mediaIds",JsonArray(emptyList())) }
            val next=s.createInput(phone,regen).text("inputId");assertNotEquals(input,next)
            assertEquals(s.input(car,input).getValue("input").jsonObject.getValue("sourceCoverageHash"),s.input(phone,next).getValue("input").jsonObject.getValue("sourceCoverageHash"))
            assertFails { s.createInput(phone,JsonObject(regen+("coverage" to initial().getValue("coverage")))) }
        }
    }
    @Test fun timeoutAndRestartNeverSendAnUncertainAttemptAgain() {
        val root=Files.createTempDirectory("music-uncertain").toFile();val policy=ExecutionPolicy.testing();var sends=0;var id=""
        MusicService(root,policy,ByteArray(32)).use { s ->
            val input=s.createInput(car,initial()).text("inputId");id=s.submit(car,input,s.quote(car,input).text("quoteId"),"job").text("jobId")
            val worker=MusicPipeline(s,ModelTransport { _,_->sends++;throw java.io.IOException("uncertain") },AudioInspection { error("must not validate") })
            assertTrue(worker.processNext());assertEquals("RECONCILING",s.job(car,id).text("state"));assertFalse(worker.processNext())
        }
        MusicService(root,policy,ByteArray(32)).use { s ->
            assertFalse(MusicPipeline(s,ModelTransport { _,_->sends++;response() }).processNext())
            assertEquals(1,sends);assertEquals(1,s.store.attemptCount());assertEquals("RECONCILING",s.job(car,id).text("state"))
        }
    }
    @Test fun withdrawalDuringSendSuppressesTheResultAndKeepsTheAttemptCost() {
        MusicService(Files.createTempDirectory("music-cancel").toFile(),ExecutionPolicy.testing(),ByteArray(32)).use { s ->
            val input=s.createInput(car,initial()).text("inputId");val id=s.submit(car,input,s.quote(car,input).text("quoteId"),"job").text("jobId")
            val worker=MusicPipeline(s,ModelTransport { _,_->s.cancel(car,id);response() },AudioInspection { error("must not validate") })
            assertTrue(worker.processNext());assertEquals("CANCELED",s.job(car,id).text("state"));assertEquals(1,s.store.attemptCount())
            assertTrue(s.store.reservedMicros()>0);assertFalse(s.audioFile(car,id).exists())
        }
    }
    @Test fun productionTransportRejectsATestPermitBeforeRetrievingAnyProviderKey() {
        MusicService(Files.createTempDirectory("no-paid-transport").toFile(),ExecutionPolicy.testing(),ByteArray(32)).use { s ->
            val input=s.createInput(car,initial()).text("inputId");val id=s.submit(car,input,s.quote(car,input).text("quoteId"),"job").text("jobId")
            var keyReads=0
            val request=canonical(MusicRequest.build("Complete original song"))
            val attempt=s.store.beginAttempt(id,"MUSIC","music",request,s.policy.musicMicros,s.policy)!!
            val permit=DispatchPermit(s.store,attempt,s.policy) { true }
            assertFails { GoogleInteractionsTransport { keyReads++;error("must not read key") }.send(permit,request) }
            assertEquals(0,keyReads)
        }
    }
}
