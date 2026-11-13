package dev.todaystrack.vehicle.generation

import org.junit.Assert.*
import org.junit.Test

class GenerationRecoveryPolicyTest {
    @Test fun persistedSendAndDownloadWorkCanResumeWithoutAnotherTap() {
        for (state in listOf("LOCAL_PENDING", "SENDING", "QUEUED", "VALIDATING", "READY", "SERVER_PENDING", "TRANSPORT_PENDING", "ACCEPTANCE_UNCERTAIN")) {
            assertTrue("recover $state", GenerationRecoveryPolicy.automaticallyResume(state))
        }
        for (state in listOf("AVAILABLE", "AUTH_REQUIRED", "REQUEST_HELD", "FAILED_VALIDATION")) {
            assertFalse("hold $state", GenerationRecoveryPolicy.automaticallyResume(state))
        }
    }
    @Test fun uncertainAcceptanceCanOnlyLookUpTheOriginalIntent() {
        assertFalse(GenerationRecoveryPolicy.mayCreateJob("ACCEPTANCE_UNCERTAIN"))
        assertFalse(GenerationRecoveryPolicy.mayCreateJob("TRANSPORT_PENDING"))
        assertFalse(GenerationRecoveryPolicy.mayCreateJob("RECONCILING"))
        assertFalse(GenerationRecoveryPolicy.mayCreateJob("REQUEST_HELD"))
        assertTrue(GenerationRecoveryPolicy.mayCreateJob("LOCAL_PENDING"))
        assertFalse(GenerationRecoveryPolicy.mayCreateJob("SENDING"))
        assertTrue(GenerationRecoveryPolicy.mayCreateJob("PREPARING"))
    }
}
