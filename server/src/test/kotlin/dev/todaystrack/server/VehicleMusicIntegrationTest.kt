package dev.todaystrack.server

import dev.todaystrack.music.*
import dev.todaystrack.server.music.*
import dev.todaystrack.vehicle.generation.v2.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import java.nio.file.Files
import kotlin.test.*
import org.junit.Test

class VehicleMusicIntegrationTest {
    private fun batch()=ObservationBatch(listOf(
        Observation(1,Source.LOCATION,"e","b",0,100000,"FRESH",100000,"SDK_UTC_MILLIS",37.0,127.0),
        Observation(2,Source.ODOMETER,"e","b",100,100100,"VALID",value=100.0,unit="km"),
        Observation(3,Source.LOCATION,"e","b",60000,160000,"FRESH",160000,"SDK_UTC_MILLIS",37.001,127.001),
        Observation(4,Source.ODOMETER,"e","b",60100,160100,"VALID",value=101.0,unit="km")),environment=Environment.SIMULATED)
    private class MemoryStore(var intent: LocalMusicIntent): VehicleMusicStore {
        var saved: MusicReceipt?=null;var track: SavedMusicAudio?=null;var newAi: Boolean?=null
        override suspend fun load(id: String)=intent.also { require(it.id==id) }
        override suspend fun receipt(id: String)=saved
        override suspend fun save(intent: LocalMusicIntent,receipt: MusicReceipt) { this.intent=intent;saved=receipt }
        override suspend fun ready(intent: LocalMusicIntent,receipt: MusicReceipt,audio: SavedMusicAudio,isNewAi: Boolean) { save(intent,receipt);track=audio;newAi=isNewAi }
    }
    @Test fun newVehicleInputUsesCoreFeaturesAndDoesNotTransmitCoordinatesOrInventOptionalSignals()=runBlocking {
        val frozen=VehicleMusicInput.freeze("intent","session","car",batch(),MusicPreferences(genre="FUNK"))
        val payload=musicJson.parseToJsonElement(frozen.payload).jsonObject
        assertEquals(2,payload.getValue("schemaVersion").jsonPrimitive.int)
        val input=payload.getValue("input").jsonObject
        assertEquals(1.0,input.getValue("features").jsonObject.getValue("metrics").jsonObject.getValue("continuousDistanceKm").jsonObject.getValue("value").jsonPrimitive.double)
        assertFalse(frozen.payload.contains("latitude"));assertFalse(frozen.payload.contains("127.001"));assertFalse(frozen.payload.contains("speedMeanMps"))
        assertEquals("FUNK",input.getValue("preferences").jsonObject.text("genre"))
    }
    @Test fun disabledServerApprovalKeepsVehicleInputAndPlanButNeverPostsAGenerationJob()=runBlocking {
        val frozen=VehicleMusicInput.freeze("intent","session","car",batch(),MusicPreferences(genre="FUNK"));val local=MemoryStore(frozen)
        val actor=MusicPrincipal("account","car",DeviceRole.CAR)
        MusicService(Files.createTempDirectory("vehicle-v2-contract").toFile()).use { service ->
            val calls=mutableListOf<String>()
            val api=object: VehicleMusicApi {
                override suspend fun call(method: String,path: String,body: JsonObject?): JsonObject {
                    calls.add("$method $path")
                    return when {
                        path=="/v2/music/jobs/by-intent/intent" -> throw MusicApiFailure(404,"JOB_NOT_FOUND")
                        path=="/v2/music/inputs" -> service.createInput(actor,body!!)
                        path.endsWith("/plan") -> service.preview(actor,path.split('/')[4])
                        path=="/v2/music/quotes" -> service.quote(actor,body!!.text("inputId"))
                        else -> error("Unexpected call: $method $path")
                    }
                }
                override suspend fun audio(path: String,jobId: String,sha256: String,bytes: Long)=error("must not download")
            }
            assertEquals(FlowResult.HELD,VehicleMusicFlow(local,api).advance("intent"))
            assertEquals("BILLING_HELD",local.intent.state);assertEquals(frozen.payload,local.intent.payload);assertEquals(frozen.inputHash,local.intent.inputHash)
            assertNotNull(local.saved?.inputId);assertNotNull(local.saved?.plan)
            assertFalse(calls.contains("POST /v2/music/jobs"));assertEquals(0,service.store.attemptCount());assertEquals(0,service.store.reservedMicros())
        }
    }
    @Test fun acceptanceUncertaintyAfterRestartIsLookupOnlyEvenWhenTheServerReturnsNotFound()=runBlocking {
        val frozen=VehicleMusicInput.freeze("intent","session","car",batch(),MusicPreferences(genre="FUNK"))
        val local=MemoryStore(frozen.copy(state="ACCEPTANCE_UNCERTAIN"))
        val calls=mutableListOf<String>()
        val api=object: VehicleMusicApi {
            override suspend fun call(method: String,path: String,body: JsonObject?): JsonObject { calls.add(method);throw MusicApiFailure(404,"JOB_NOT_FOUND") }
            override suspend fun audio(path: String,jobId: String,sha256: String,bytes: Long)=error("must not download")
        }
        assertEquals(FlowResult.HELD,VehicleMusicFlow(local,api).advance("intent"));assertEquals(listOf("GET"),calls)
        assertEquals("ACCEPTANCE_UNCERTAIN",local.intent.state)
    }
    @Test fun lostAcceptanceRecoversOriginalV2JobAndSavesReturnedLyricsAndAudioWithoutAnotherGeneration()=runBlocking {
        val frozen=VehicleMusicInput.freeze("intent","session","car",batch(),MusicPreferences(genre="FUNK"));val local=MemoryStore(frozen)
        val actor=MusicPrincipal("account","car",DeviceRole.CAR)
        MusicService(Files.createTempDirectory("vehicle-v2-result").toFile(),ExecutionPolicy.testing(),ByteArray(32)).use { service ->
            var acceptPosts=0;var loseResponse=true;var interruptDownload=true
            val api=object: VehicleMusicApi {
                override suspend fun call(method: String,path: String,body: JsonObject?): JsonObject = when {
                    path=="/v2/music/jobs/by-intent/intent" -> runCatching { service.jobByIntent(actor,"intent") }.getOrElse { throw MusicApiFailure(404,"JOB_NOT_FOUND") }
                    path=="/v2/music/inputs" -> service.createInput(actor,body!!)
                    path.startsWith("/v2/music/inputs/") && path.endsWith("/plan") -> service.preview(actor,path.split('/')[4])
                    path.startsWith("/v2/music/inputs/") -> service.input(actor,path.split('/')[4])
                    path=="/v2/music/quotes" -> service.quote(actor,body!!.text("inputId"))
                    path=="/v2/music/jobs" -> { acceptPosts++;val job=service.submit(actor,body!!.text("inputId"),body.text("quoteId"),body.text("intentId"));if(loseResponse) { loseResponse=false;throw java.io.IOException("synthetic lost acknowledgement") };job }
                    path.startsWith("/v2/music/jobs/") -> service.job(actor,path.substringAfterLast('/'))
                    else -> error("Unexpected call $method $path")
                }
                override suspend fun audio(path: String,jobId: String,sha256: String,bytes: Long): SavedMusicAudio {
                    assertEquals("/v2/music/jobs/$jobId/audio",path)
                    if(interruptDownload) { interruptDownload=false;throw java.io.IOException("synthetic interrupted download") }
                    val file=service.audioFile(actor,jobId);assertEquals(sha256,contentHash(file.readBytes()));assertEquals(bytes,file.length())
                    return SavedMusicAudio(file.absolutePath,sha256,bytes,90000)
                }
            }
            assertEquals(FlowResult.RETRY,VehicleMusicFlow(local,api).advance("intent"));assertEquals("ACCEPTANCE_UNCERTAIN",local.intent.state)
            assertTrue(local.saved!!.acceptanceUncertain);assertNotEquals(frozen.inputHash,local.saved!!.inputHash)
            assertEquals(FlowResult.PENDING,VehicleMusicFlow(local,api).advance("intent"));val jobId=local.intent.jobId!!
            val response=buildJsonObject { put("steps",buildJsonArray { add(buildJsonObject { put("type","model_output");put("content",buildJsonArray {
                add(buildJsonObject { put("type","text");put("text","[Verse]\n오늘의 빛을 노래해") })
                add(buildJsonObject { put("type","audio");put("mime_type","audio/mpeg");put("data",java.util.Base64.getEncoder().encodeToString(ByteArray(2048))) })
            }) }) }) }.toString()
            assertTrue(MusicPipeline(service,ModelTransport { _,_->response },AudioInspection { AudioInspectionResult(90000,false) }).processNext())
            assertEquals(FlowResult.RETRY,VehicleMusicFlow(local,api).advance("intent"));assertEquals("TRANSPORT_PENDING",local.intent.state)
            assertEquals(FlowResult.COMPLETE,VehicleMusicFlow(local,api).advance("intent"))
            assertEquals("AVAILABLE",local.intent.state);assertEquals(jobId,local.intent.jobId);assertEquals(frozen.payload,local.intent.payload)
            assertTrue(local.saved!!.returnedLyrics!!.contains("오늘의 빛"));assertNotNull(local.saved!!.integrity);assertNotNull(local.track)
            assertEquals(false,local.newAi);assertEquals(1,acceptPosts);assertEquals(1,service.store.attemptCount())
        }
    }
    @Test fun aReadyResultForDifferentFrozenInputIsHeldBeforeAnyAudioDownload()=runBlocking {
        val frozen=VehicleMusicInput.freeze("intent","session","car",batch(),MusicPreferences(genre="FUNK"))
        val job="00000000-0000-0000-0000-000000000001";val input="00000000-0000-0000-0000-000000000002"
        val local=MemoryStore(frozen.copy(jobId=job,state="READY")).also { it.saved=MusicReceipt("intent",input,"a".repeat(64)) }
        var downloads=0
        val api=object: VehicleMusicApi {
            override suspend fun call(method: String,path: String,body: JsonObject?)=buildJsonObject {
                put("jobId",job);put("inputId",input);put("inputHash","b".repeat(64));put("stateRevision",3);put("state","READY");put("mode","LIVE")
            }
            override suspend fun audio(path: String,jobId: String,sha256: String,bytes: Long): SavedMusicAudio { downloads++;error("must not download") }
        }
        assertEquals(FlowResult.HELD,VehicleMusicFlow(local,api).advance("intent"));assertEquals("REQUEST_HELD",local.intent.state)
        assertEquals(0,downloads);assertEquals(frozen.payload,local.intent.payload);assertNull(local.track)
    }
}
