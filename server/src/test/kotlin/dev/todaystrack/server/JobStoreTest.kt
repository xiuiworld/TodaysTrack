package dev.todaystrack.server

import java.nio.file.Files
import kotlin.test.*
import org.junit.Test

class JobStoreTest {
    private fun url() = "jdbc:sqlite:" + Files.createTempDirectory("todaystrack-db-test").resolve("jobs.db")
    // Would catch a duplicate click creating a new paid attempt or changing frozen input.
    @Test fun sameIntentReturnsSameJobAndDifferentBodyIsRejected() {
        JobStore(url()).use { db ->
            val id = db.adopt("owner", "intent", "hash1", "fixed", 100_000)
            assertEquals(id, db.adopt("owner", "intent", "hash1", "fixed", 100_000))
            assertFailsWith<IllegalArgumentException> { db.adopt("owner", "intent", "hash2", "changed", 100_000) }
            assertEquals(100_000, db.reservedMicros())
        }
    }
    // Would catch releasing an unknown charge/reposting a submitted job after restart.
    @Test fun restartKeepsSubmittedAttemptUncertainAndBudgetReserved() {
        val path = url()
        val id = JobStore(path).use { db ->
            db.adopt("owner", "intent", "hash", "fixed", 100_000).also { assertTrue(db.beginDispatch(it)) }
        }
        JobStore(path).use { db ->
            db.recover()
            assertEquals("RECONCILING", db.state(id))
            assertFalse(db.beginDispatch(id))
            assertEquals(100_000, db.reservedMicros())
        }
    }
    // Would catch exceeding the user's USD5 total even if all prior attempts timed out.
    @Test fun allAttemptCostsCountTowardFiveDollars() {
        JobStore(url()).use { db ->
            val id = db.adopt("owner", "first", "a", "fixed", 4_950_000)
            assertTrue(db.beginDispatch(id))
            assertFailsWith<IllegalStateException> { db.adopt("owner", "second", "b", "fixed", 100_000) }
            assertEquals(4_950_000, db.reservedMicros())
        }
    }

    // Lost acknowledgment recovery must not reserve a second charge, even at the cap.
    @Test fun existingIntentLookupAtBudgetCapIsOwnerScopedAndReadOnly() {
        JobStore(url()).use { db ->
            val id=db.adopt("owner","last-intent","fixed-hash","fixed",5_000_000)
            assertTrue(db.beginDispatch(id))
            assertEquals(id,db.jobByIntent("owner","last-intent")?.get("id"))
            assertNull(db.jobByIntent("another-owner","last-intent"))
            assertNull(db.jobByIntent("owner","last"))
            assertEquals(5_000_000,db.reservedMicros())
            assertFalse(db.beginDispatch(id))
        }
    }
}
