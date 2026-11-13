package dev.todaystrack.music

import kotlinx.serialization.json.*
import org.junit.Test
import kotlin.test.*

class CompositionContractTest {
    private fun loc(seq: Long,lat: Double,lon: Double)=Observation(seq,Source.LOCATION,"e","b",seq*100,100000+seq,"FRESH",100000+seq,"SDK_UTC_MILLIS",lat,lon)
    @Test fun coordinateNoiseAndProximityRemainContinuousEvidenceWithoutInventingVisits() {
        val input=listOf(loc(1,37.0,127.0),loc(2,37.000001,127.000001),loc(3,37.0,127.0))
        val f=FeatureExtractor().extract(ObservationBatch(input))
        assertEquals(0L,f.sections.sumOf { it.repeatedPositionSpanMs });assertEquals(0.0,f.metrics.getValue("startEndDistanceMeters").value)
        val compared=SpatialComparison.compare(input,listOf(loc(8,37.0,127.0)))
        assertTrue(compared.getValue("referenceNearestMeanMeters").value>0);assertEquals(2.0/3,compared.getValue("referenceExactMatchFraction").value)
        assertFalse(featuresJson(f).toString().contains("latitude"))
    }
    @Test fun sourceContextAndVocalPreferencesRoundTripWithExplicitOrigins() {
        val context=ProvidedContext("weather","WEATHER","약한 비","test weather provider",1000,2000,"PROVIDER_REPORTED",true)
        val fact=context.material();assertEquals(FactOrigin.PROVIDED_CONTEXT,fact.origin)
        assertFails { context.copy(source="").material() };assertFails { context.copy(sourceAtUtcMs=3000).material() }
        val prefs=MusicPreferences(genre="FUNK",vocalStyle=listOf("부드러운 중음 보컬"))
        assertEquals(prefs,readPreferences(preferencesJson(prefs)))
        val features=FeatureExtractor().extract(ObservationBatch(emptyList()))
        val plan=CompositionPlanner().build(features,prefs,listOf(fact))
        val prompt=MusicPromptBuilder().build(plan,features)
        assertTrue(prompt.contains("PROVIDED_CONTEXT"));assertTrue(prompt.contains("부드러운 중음 보컬"));assertTrue(prompt.contains("source report"))
        assertFalse(prompt.contains("\"continuousDistanceKm\"")) // No evidence is not a measured zero-distance journey.
    }
    @Test fun sourceSpecificInputBoundariesAndSparsePhasesAreNotInterpolated() {
        fun odo(seq: Long,ms: Long,value: Double)=Observation(seq,Source.ODOMETER,"e","b",ms,100000+ms,"VALID",value=value,unit="km")
        val f=FeatureExtractor().extract(ObservationBatch(listOf(odo(1,0,10.0),odo(2,6000,16.0))))
        assertEquals(1.0,f.metrics.getValue("distanceUnallocatedPhaseFraction").value)
        assertTrue(f.metrics.getValue("distanceEarlyFraction").evidence.isEmpty())
        val broken=FeatureExtractor().extract(ObservationBatch(listOf(loc(1,37.0,127.0),loc(3,37.0,127.0),odo(4,400,10.0),odo(6,600,15.0)),boundarySeqs=listOf(2,5)))
        assertFalse(broken.metrics.containsKey("startEndDistanceMeters"));assertEquals(5.0,broken.metrics.getValue("unallocatedBoundaryDistanceKm").value)
        assertEquals(0.0,broken.metrics.getValue("continuousDistanceKm").value)
    }
    @Test fun automaticGenreAvoidsRecentlyUsedChoicesAndSameFrozenInputReproducesThePlan() {
        val f=FeatureExtractor().extract(ObservationBatch(listOf(loc(1,37.0,127.0),loc(2,37.1,127.1))))
        val prefs=MusicPreferences(allowedGenres=listOf("FUNK","ROCK"),recentGenres=listOf("FUNK"))
        val a=CompositionPlanner().build(f,prefs);assertEquals("ROCK",a.genre)
        assertEquals(contentHash(planJson(a)),contentHash(planJson(CompositionPlanner().build(readFeatures(featuresJson(f)),readPreferences(preferencesJson(prefs))))))
        assertFails { readFeatures(JsonObject(featuresJson(f)+("longitude" to JsonPrimitive(127.0)))) }
    }
    @Test fun selectedTripsKeepTheirOwnEvidenceAndNeverJoinIntoAContinuousAlbumRoute() {
        val first=FeatureExtractor().extract(ObservationBatch(listOf(loc(1,37.0,127.0),loc(2,37.1,127.1))))
        val selected=FeatureSelection.combine(listOf("car/trip1" to first,"car/trip2" to first))
        assertEquals(2,selected.sections.size);assertEquals(setOf("car/trip1","car/trip2"),selected.sections.flatMap { it.evidence }.map { it.coverageRef }.toSet())
        assertFalse(selected.metrics.containsKey("startEndDistanceMeters"));assertFalse(selected.legacyReturnObserved)
        assertEquals(4,selected.positionOrder.distinct().size)
        assertEquals(selected,readFeatures(featuresJson(selected)))
    }
    @Test fun instrumentalPalettesNeverAskForVocalLayersOrHarmonies() {
        val f=FeatureExtractor().extract(ObservationBatch(emptyList()))
        for(genre in GenreCatalog.palettes.keys) for(v in 1..12) {
            val p=CompositionPlanner().build(f,MusicPreferences(genre=genre,lyricMode=LyricMode.INSTRUMENTAL,variationId=v.toString()))
            assertFalse(p.arrangement.contains("vocal",true),"$genre: ${p.arrangement}")
        }
    }
}
