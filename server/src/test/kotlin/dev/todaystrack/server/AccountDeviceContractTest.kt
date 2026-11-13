package dev.todaystrack.server

import dev.todaystrack.music.DeviceRole
import java.nio.file.Files
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64
import java.util.UUID
import org.junit.Test
import kotlin.test.*

class AccountDeviceContractTest {
    @Test fun signedDeviceIdentityComesFromTrustedRegistrationAndCannotChangeAccountOrRole() {
        JobStore("jdbc:sqlite:"+Files.createTempDirectory("account-devices").resolve("server.db")).use { store ->
            val pair=KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair()
            val publicKey=Base64.getEncoder().encodeToString(pair.public.encoded)
            store.enrollAccountDevice("account-a","phone",publicKey,"PHONE")
            assertFails { store.enrollAccountDevice("account-b","phone",publicKey,"PHONE") }
            assertFails { store.enrollAccountDevice("account-a","phone",publicKey,"CAR") }
            val body="{\"inputId\":\"input\"}".toByteArray();val path="/v2/music/quotes"
            val stamp=System.currentTimeMillis().toString();val nonce=UUID.randomUUID().toString()
            val canonical="POST\n$path\nphone\n$stamp\n$nonce\n${sha256(body)}"
            val signature=Signature.getInstance("SHA256withECDSA").apply { initSign(pair.private);update(canonical.toByteArray()) }.sign()
            val headers=mapOf("X-Device-Id" to "phone","X-Timestamp" to stamp,"X-Nonce" to nonce,"X-Signature" to Base64.getEncoder().encodeToString(signature),"X-Owner-Id" to "account-b","X-Device-Role" to "CAR")
            val auth=DeviceAuth(store)
            assertNull(auth.authenticate("POST",path,body,headers))
            assertNull(auth.authenticatePrincipal("POST",path,body+1,headers))
            val actor=auth.authenticatePrincipal("POST",path,body,headers)!!
            assertEquals("account-a",actor.ownerId);assertEquals(DeviceRole.PHONE,actor.role)
            assertNull(auth.authenticatePrincipal("POST",path,body,headers))
        }
    }
}
