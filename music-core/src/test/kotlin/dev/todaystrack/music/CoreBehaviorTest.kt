package dev.todaystrack.music

import kotlin.test.*
import org.junit.Test

class CoreBehaviorTest {
    private fun odo(seq: Long, ms: Long, km: Double, epoch: String="e",boot: String="boot") =
        Observation(seq,Source.ODOMETER,epoch,boot,ms,100_000+ms,"VALID",value=km,unit="km")
    private fun loc(seq: Long,ms: Long,lat: Double,lon: Double,age: Long=0) =
        Observation(seq,Source.LOCATION,"e","boot",ms,100_000+ms,"FRESH",sourceTimestampMs=100_000+ms-age,
            sourceTimeBasis="SDK_UTC_MILLIS",latitude=lat,longitude=lon)
    private fun journey() = ObservationBatch(listOf(loc(1,0,37.0,127.0),odo(2,100,100.0),
        loc(3,1000,37.001,127.001),odo(4,1100,102.0),loc(5,2000,37.002,127.002),
        loc(6,4000,37.002,127.002),loc(7,5000,37.001,127.001),loc(8,6000,37.0,127.0),odo(9,6100,108.0)),
        environment=Environment.SIMULATED)

    @Test fun gapDistanceIsScaleOnlyAndNeverAllocatedToPositionSections() {
        val input=ObservationBatch(listOf(odo(1,100,100.0),loc(2,200,37.0,127.0),
            loc(3,1000,37.01,127.01),odo(4,1100,108.0),odo(5,1200,109.0)),
            listOf(Gap(setOf(Source.ODOMETER),1,4,"ODOMETER_INTERRUPTED")))
        val result=FeatureExtractor().extract(input)
        assertEquals(1.0,result.metrics.getValue("continuousDistanceKm").value)
        assertEquals(8.0,result.metrics.getValue("gapSpanningDistanceKm").value)
        assertTrue(result.sections.all { section -> section.evidence.none { it.meaning=="allocated odometer distance" } })
    }
    @Test fun nonMonotonicUnknownBootAndResetCannotInflateDistance() {
        val result=FeatureExtractor().extract(ObservationBatch(listOf(odo(1,2000,100.0),odo(2,1000,108.0),
            odo(3,3000,109.0),odo(4,10,500.0,"e2","unknown"),odo(5,20,700.0,"e2","unknown"))))
        assertEquals(1.0,result.metrics.getValue("continuousDistanceKm").value)
        assertEquals(0.0,result.metrics.getValue("gapSpanningDistanceKm").value)
    }
    @Test fun stalePositionSplitsEvidenceAndDoesNotBecomeAPauseOrVisit() {
        val result=FeatureExtractor().extract(ObservationBatch(listOf(loc(1,30000,37.0,127.0),
            loc(2,30100,37.1,127.1,20000),loc(3,31000,37.0,127.0))))
        assertEquals("STALE",result.excluded[2])
        assertEquals(2,result.sections.size)
        assertTrue(result.sections.all { it.repeatedPositionSpanMs==0L })
        assertFalse(result.metrics.containsKey("startEndDistanceMeters"))
    }
    @Test fun directSpeedAndHeadingHaveSeparateUnitsAndNeverUseCruiseTarget() {
        val input=journey().copy(observations=journey().observations+listOf(
            Observation(10,Source.SPEED,"e","boot",6200,106200,"VALID",value=10.0,unit="m/s"),
            Observation(11,Source.SPEED,"e","boot",6300,106300,"VALID",value=20.0,unit="m/s"),
            Observation(12,Source.SPEED,"e","boot",6400,106400,"VALID",value=90.0,unit="cruise-target-km/h")),
            statuses=listOf(SourceStatus(Source.SPEED,Availability.AVAILABLE,true)))
        val result=FeatureExtractor().extract(input)
        assertEquals(15.0,result.metrics.getValue("speedMeanMps").value)
        assertTrue(result.excluded.containsKey(12))
        assertEquals(0.0,result.metrics.getValue("startEndDistanceMeters").value)
    }
    @Test fun deterministicVariationRespectsGenrePreferencesAndDoesNotEmitFixedLyrics() {
        val features=FeatureExtractor().extract(journey())
        val settings=MusicPreferences(allowedGenres=listOf("FUNK","ELECTRONIC"),excludedGenres=listOf("ACOUSTIC"))
        val first=CompositionPlanner().build(features,settings)
        assertEquals(first,CompositionPlanner().build(features,settings))
        assertTrue(first.genre in settings.allowedGenres)
        val other=CompositionPlanner().build(features,settings.copy(variationId="second"))
        assertNotEquals(first.inputHash,other.inputHash)
        val prompt=MusicPromptBuilder().build(first,features)
        assertFalse(prompt.contains("관측된 거리"));assertFalse(prompt.contains("ONLY these supplied lyrics"))
        assertTrue(prompt.contains("Write original"));assertTrue(prompt.contains("not measured driver emotion"))
        assertFalse(prompt.contains("37.0"));assertFalse(prompt.contains("127.0"))
    }
    @Test fun differentJourneyHasDifferentEvidenceAndMusicDirectionsWithinSameGenre() {
        val first=FeatureExtractor().extract(journey())
        val second=FeatureExtractor().extract(ObservationBatch(listOf(odo(1,0,10.0),odo(2,60000,11.0))))
        val prefs=MusicPreferences(genre="ELECTRONIC")
        val a=CompositionPlanner().build(first,prefs);val b=CompositionPlanner().build(second,prefs)
        assertNotEquals(a.sections,b.sections)
        assertFalse(second.metrics.containsKey("startEndDistanceMeters"))
    }
    @Test fun unknownTimeDomainsAndSourceOnlyGapsPreserveIndependentEvidence() {
        val input=journey().copy(gaps=listOf(Gap(setOf(Source.LOCATION),3,7,"POSITION_LOST")))
        val result=FeatureExtractor().extract(input)
        assertEquals(8.0,result.metrics.getValue("continuousDistanceKm").value)
        assertTrue(result.sections.size>1)
        assertFalse(result.metrics.containsKey("startEndDistanceMeters"))
        assertTrue(result.unknown.any { it.contains("continuity") })
    }
    @Test fun instrumentalAndUserMaterialHaveExplicitCreativeBoundaries() {
        val features=FeatureExtractor().extract(journey())
        val material=MaterialFact("memo-1",FactOrigin.USER,"친구와 바다를 보았다",allowedInLyrics=true)
        val plan=CompositionPlanner().build(features,MusicPreferences(lyricMode=LyricMode.INSTRUMENTAL),listOf(material))
        val prompt=MusicPromptBuilder().build(plan,features)
        assertTrue(prompt.contains("no vocals, spoken words, humming"))
        assertTrue(plan.allowedFacts.single().origin==FactOrigin.USER)
        assertTrue(plan.explanations.isNotEmpty())
    }
}
