package dev.todaystrack.vehicle.generation

/** Provider submission and existing-job observation are different operations. */
object GenerationRecoveryPolicy {
    fun automaticallyResume(state: String): Boolean = state in setOf(
        "LOCAL_PENDING", "SENDING", "PREPARING", "QUEUED", "SUBMITTING", "GENERATING", "VALIDATING", "READY",
        "SERVER_PENDING", "TRANSPORT_PENDING", "ACCEPTANCE_UNCERTAIN", "RECONCILING"
    )
    // AUTH_REQUIRED is only re-enqueued by an explicit user action after registration.
    // Legacy TRANSPORT_PENDING may have lost an acceptance response: lookup only.
    // SENDING is a legacy state that could already have sent an acceptance POST.
    fun mayCreateJob(state: String): Boolean = state in setOf("LOCAL_PENDING", "PREPARING", "AUTH_REQUIRED")
}
