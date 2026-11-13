package dev.todaystrack.vehicle.generation

/** Only for private raw-hash canonicalization; raw SDK facts remain unchanged in storage. */
object CanonicalSdkNumber {
    fun encode(value: Double?): Any? = when {
        value == null -> null
        value.isNaN() -> "NaN"
        value == Double.POSITIVE_INFINITY -> "+Infinity"
        value == Double.NEGATIVE_INFINITY -> "-Infinity"
        else -> value
    }
}
