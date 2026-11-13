package dev.todaystrack.server

import dev.todaystrack.music.*
import dev.todaystrack.server.music.*
import kotlinx.serialization.json.*
import java.nio.file.Files
import kotlin.test.*
import org.junit.Test

class MusicV2BehaviorTest {
    private fun features()=featuresJson(FeatureExtractor().extract(ObservationBatch(listOf(
        Observation(1,Source.ODOMETER,"e","boot",100,100100,"VALID",value=100.0,unit="km"),
        Observation(2,Source.ODOMETER,"e","boot",200,100200,"VALID",value=108.0,unit="km")),environment=Environment.SIMULATED)))
    private fun input(intent: String="i")=buildJsonObject {
        put("intentId",intent);put("operation","INITIAL");put("features",features());put("preferences",preferencesJson(MusicPreferences(genre="FUNK")))
        put("coverage",buildJsonArray { add(buildJsonObject { put("deviceId","car");put("sessionId","session");put("fromSeq",1);put("toSeqExclusive",3) }) })
        put("materials",buildJsonArray { });put("mediaIds",buildJsonArray { })
    }
    private fun service(): MusicService=MusicService(Files.createTempDirectory("music-v2-test").toFile())
    private val car=MusicPrincipal("owner","car",DeviceRole.CAR)
    private val phone=MusicPrincipal("owner","phone",DeviceRole.PHONE)

    @Test fun authenticatedOwnerAndFirstCarGenerationAreEnforced() {
        service().use { service ->
            val accepted=service.createInput(car,input())
            assertEquals(accepted,service.createInput(car,input()))
            assertFails { service.input(MusicPrincipal("other","car2",DeviceRole.CAR),accepted.string("inputId")) }
            assertFails { service.createInput(phone,input()) }
            assertFails { service.createInput(car,JsonObject(input()+ ("features" to buildJsonObject { put("latitude",37.0) }))) }
            assertFails { service.createInput(car,JsonObject(input()+ ("preferences" to preferencesJson(MusicPreferences(genre="ROCK"))))) }
        }
    }
    @Test fun disabledPaidExecutionDoesNotReserveDispatchOrReadAnyKey() {
        service().use { s ->
            val id=s.createInput(car,input()).string("inputId")
            val quote=s.quote(car,id)
            assertFalse(quote.getValue("dispatchAllowed").jsonPrimitive.boolean)
            assertEquals(0,s.store.reservedMicros())
            assertFails { s.submit(car,id,quote.string("quoteId"),"job-i") }
            assertEquals(0,s.store.attemptCount())
        }
    }
    @Test fun allManualKindsRequireSelectionConsentAndSupportedRange() {
        for(kind in MediaKind.entries.filter { it!=MediaKind.MEMO }) {
            val mime=when(kind) { MediaKind.PHOTO->"image/jpeg";MediaKind.VIDEO->"video/mp4";else->"audio/wav" }
            val descriptor=MediaSelection(kind,"a".repeat(64),mime,1024,if(kind==MediaKind.PHOTO) null else 2000,
                consent=MediaConsent(1,true,true))
            MediaSelectionValidator.validate(descriptor)
            assertFails { MediaSelectionValidator.validate(descriptor.copy(consent=descriptor.consent.copy(selectedByUser=false))) }
            assertFails { MediaSelectionValidator.validate(descriptor.copy(consent=descriptor.consent.copy(revoked=true))) }
            if(kind!=MediaKind.PHOTO) assertFails { MediaSelectionValidator.validate(descriptor.copy(fromMs=2000,toMs=1000)) }
        }
    }
    @Test fun pendingAttemptCannotBeResentAfterStoreRestartAndBudgetIsNotRefunded() {
        val root=Files.createTempDirectory("music-v2-recovery").toFile()
        val policy=ExecutionPolicy.testing(analysisMicros=1000,musicMicros=100_000)
        var jobId=""
        MusicService(root,policy).use { s ->
            val id=s.createInput(car,input()).string("inputId");val quote=s.quote(car,id)
            jobId=s.submit(car,id,quote.string("quoteId"),"one").string("jobId")
            assertEquals(jobId,s.submit(car,id,quote.string("quoteId"),"one").string("jobId"))
            assertTrue(s.store.beginAttempt(jobId,"MUSIC","music","request",100_000,policy)!=null)
        }
        MusicService(root,policy).use { s ->
            assertEquals("RECONCILING",s.job(car,jobId).string("state"))
            assertNull(s.store.beginAttempt(jobId,"MUSIC","music","request",100_000,policy))
            assertEquals(100_000,s.store.reservedMicros())
            assertEquals(1,s.store.attemptCount())
        }
    }
}
