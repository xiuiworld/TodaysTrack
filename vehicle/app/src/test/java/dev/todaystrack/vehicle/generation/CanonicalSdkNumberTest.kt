package dev.todaystrack.vehicle.generation

import org.junit.Assert.*
import org.junit.Test

class CanonicalSdkNumberTest {
    // Returning NaN/infinity to JSONObject would abort freezing an otherwise usable journey.
    @Test fun nonFiniteSdkValuesHaveDistinctDeterministicRawHashTags() {
        assertEquals("NaN", CanonicalSdkNumber.encode(Double.NaN))
        assertEquals("+Infinity", CanonicalSdkNumber.encode(Double.POSITIVE_INFINITY))
        assertEquals("-Infinity", CanonicalSdkNumber.encode(Double.NEGATIVE_INFINITY))
    }

    // Stringifying finite values or replacing null would change previously recorded raw hashes.
    @Test fun finiteNumbersAndNullKeepTheirOriginalJsonInputTypesAndValues() {
        assertNull(CanonicalSdkNumber.encode(null))
        for (value in listOf(37.0, 127.125, 20010.0, 0.0, -0.0)) {
            val encoded = CanonicalSdkNumber.encode(value)
            assertTrue(encoded is Double)
            assertEquals(java.lang.Double.doubleToRawLongBits(value),
                java.lang.Double.doubleToRawLongBits(encoded as Double))
        }
    }
}
