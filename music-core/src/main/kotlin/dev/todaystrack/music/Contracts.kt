package dev.todaystrack.music

enum class Source { LOCATION, ODOMETER, SPEED, HEADING, ALTITUDE, STEERING, BRAKE, DRIVING_MODE, WIPER, LIGHT, GEAR }
enum class Availability { AVAILABLE, UNSUPPORTED, PERMISSION_DENIED, TEMPORARILY_UNAVAILABLE, UNVERIFIED }
enum class Environment { SIMULATED, ROAD, UNKNOWN }
enum class FactOrigin { SENSOR, USER, PROVIDED_CONTEXT, MEDIA_INFERENCE, CREATIVE }
enum class MediaKind { MEMO, VOICE_MEMO, PHOTO, VIDEO, ENVIRONMENT_AUDIO, HUMMING }
enum class DeviceRole { CAR, PHONE }
enum class LyricMode { VOCAL, INSTRUMENTAL }

data class Observation(
    val seq: Long, val source: Source, val epochId: String, val bootId: String,
    val receivedElapsedMs: Long, val receivedAtUtcMs: Long, val quality: String,
    val sourceTimestampMs: Long? = null, val sourceTimeBasis: String? = null,
    val latitude: Double? = null, val longitude: Double? = null,
    val value: Double? = null, val unit: String? = null, val state: String? = null,
    val confidence: Double? = null, val sourceError: Boolean = false,
    val requestStartedElapsedMs: Long? = null,val epochStartedUtcMs: Long?=null
)
data class Gap(val sources: Set<Source>, val lastDurableSeq: Long?, val firstAfterSeq: Long?, val reason: String)
data class SourceStatus(val source: Source, val availability: Availability, val receiptVerified: Boolean = false,val coverageRef: String?=null)
data class ObservationBatch(val observations: List<Observation>, val gaps: List<Gap> = emptyList(),
    val statuses: List<SourceStatus> = emptyList(), val environment: Environment = Environment.UNKNOWN,
    val boundarySeqs: List<Long> = emptyList())
data class Evidence(val source: Source, val fromSeq: Long, val toSeq: Long, val meaning: String,val coverageRef: String?=null)
data class Metric(val value: Double, val unit: String, val evidence: List<Evidence>)
data class FeatureSection(val id: String, val evidence: List<Evidence>, val observedSpanMs: Long?,
    val positionOrder: List<String>, val repeatedPositionSpanMs: Long = 0,
    val directionChangeDegrees: Double? = null)
data class StateRun(val source: Source, val state: String, val evidence: List<Evidence>, val observedSpanMs: Long?)
data class JourneyFeatures(
    val version: String = "features-v3", val environment: Environment,
    val metrics: Map<String, Metric>, val sections: List<FeatureSection>, val stateRuns: List<StateRun>,
    val gapReasons: List<String>, val excluded: Map<Long, String>, val sourceStatuses: List<SourceStatus>,
    val unknown: List<String>, val positionOrder: List<String>, val legacyReturnObserved: Boolean
)
data class MaterialFact(val id: String, val origin: FactOrigin, val text: String,
    val attachmentId: String? = null, val fromMs: Long? = null, val toMs: Long? = null,
    val uncertainty: String? = null, val allowedInLyrics: Boolean = false)
data class MusicPreferences(
    val genre: String = "AUTO", val allowedGenres: List<String> = listOf("POP", "ELECTRONIC", "FUNK", "DREAM_POP", "ROCK", "RNB", "JAZZ", "AMBIENT"),
    val excludedGenres: List<String> = emptyList(), val instruments: List<String> = emptyList(),
    val avoid: List<String> = emptyList(), val lyricMode: LyricMode = LyricMode.VOCAL,
    val language: String = "ko", val durationSeconds: Int = 90, val variationId: String = "original",
    val recentGenres: List<String> = emptyList(), val recentArrangements: List<String> = emptyList(),
    val vocalStyle: List<String> = emptyList()
)
data class PlanSection(val role: String, val relativeWeight: Double?, val direction: String, val evidence: List<Evidence>)
data class MusicalControl(val value: Double,val interpretation: String,val evidence: List<Evidence>)
data class CompositionPlan(val version: String = "composition-v2", val genre: String,
    val arrangement: String, val theme: String, val themeOrigin: FactOrigin,
    val sections: List<PlanSection>, val allowedFacts: List<MaterialFact>, val excludedMaterialIds: List<String>,
    val preferences: MusicPreferences, val explanations: List<String>, val inputHash: String,
    val controls: Map<String,MusicalControl> = emptyMap())

/** App-supplied provenance, never filled by guessing from coordinates. No provider lookup runs here. */
data class ProvidedContext(val id: String,val kind: String,val description: String,val source: String,
    val sourceAtUtcMs: Long,val receivedAtUtcMs: Long,val confirmation: String,val lyricsAllowed: Boolean=false) {
    fun material(): MaterialFact {
        require(kind in setOf("DESTINATION","WAYPOINT","WEATHER","REGION"))
        require(id.matches(Regex("[A-Za-z0-9_-]{1,80}")) && description.length in 1..1000 && source.length in 1..200)
        require(sourceAtUtcMs>0 && receivedAtUtcMs>=sourceAtUtcMs && confirmation in setOf("USER_CONFIRMED","PROVIDER_REPORTED"))
        return MaterialFact("context-$id",FactOrigin.PROVIDED_CONTEXT,"$kind: $description (source: $source; source time UTC ms: $sourceAtUtcMs; confirmation: $confirmation)",
            uncertainty=if(confirmation=="PROVIDER_REPORTED") "Source-reported context, not verified by vehicle sensors" else "Explicitly user confirmed",allowedInLyrics=lyricsAllowed)
    }
}
