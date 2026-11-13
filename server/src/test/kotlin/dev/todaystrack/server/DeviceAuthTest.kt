package dev.todaystrack.server

import java.nio.file.Files
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64
import java.util.UUID
import kotlin.test.*
import org.junit.Test

class DeviceAuthTest {
    @Test fun registeredSignatureAcceptsOnceAndRejectsBodyTamperingAndUnknownCar() {
        val db=JobStore("jdbc:sqlite:"+Files.createTempDirectory("tt-auth").resolve("auth.db"))
        db.use {
            val pair=KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair()
            val id=UUID.randomUUID().toString()
            db.enroll(id,Base64.getEncoder().encodeToString(pair.public.encoded))
            val stamp=System.currentTimeMillis().toString();val nonce=UUID.randomUUID().toString();val body="fixed snapshot".toByteArray()
            val canonical="POST\n/v1/input-snapshots\n$id\n$stamp\n$nonce\n${sha256(body)}"
            val signature=Signature.getInstance("SHA256withECDSA").apply { initSign(pair.private);update(canonical.toByteArray()) }.sign()
            val headers=mapOf("X-Device-Id" to id,"X-Timestamp" to stamp,"X-Nonce" to nonce,"X-Signature" to Base64.getEncoder().encodeToString(signature))
            val auth=DeviceAuth(db)
            assertNull(auth.authenticate("POST","/v1/input-snapshots","changed".toByteArray(),headers))
            assertEquals("submission-owner",auth.authenticate("POST","/v1/input-snapshots",body,headers))
            assertNull(auth.authenticate("POST","/v1/input-snapshots",body,headers))
            assertNull(auth.authenticate("POST","/v1/input-snapshots",body,headers+ ("X-Device-Id" to "CAR")))
        }
    }
}
