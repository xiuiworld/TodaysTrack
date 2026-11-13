package dev.todaystrack.vehicle.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.io.File
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.UUID

/** Per-install key in Android Keystore; server must explicitly enroll its public key. */
class DevelopmentIdentity(context: Context) {
    private val prefs = context.getSharedPreferences("device", Context.MODE_PRIVATE)
    val deviceId: String = prefs.getString("deviceId", null) ?: UUID.randomUUID().toString().also { prefs.edit().putString("deviceId", it).commit() }
    private val alias = "todaystrack-device-$deviceId"
    private val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    init {
        if (!store.containsAlias(alias)) KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore").apply {
            initialize(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
                .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1")).setDigests(KeyProperties.DIGEST_SHA256).build())
        }.generateKeyPair()
        // Public registration material only; no token or private/provider key.
        File(context.filesDir, "development-enrollment.json").writeText(JSONObject()
            .put("deviceId", deviceId).put("publicKey", Base64.encodeToString(store.getCertificate(alias).publicKey.encoded, Base64.NO_WRAP)).toString())
    }
    fun headers(method: String, path: String, body: ByteArray): Map<String, String> {
        val nonce = UUID.randomUUID().toString()
        val utc = System.currentTimeMillis().toString()
        val canonical = "$method\n$path\n$deviceId\n$utc\n$nonce\n${sha256(body)}"
        val signature = Signature.getInstance("SHA256withECDSA").apply {
            initSign(store.getKey(alias, null) as java.security.PrivateKey)
            update(canonical.toByteArray(Charsets.UTF_8))
        }.sign()
        return mapOf("X-Device-Id" to deviceId, "X-Timestamp" to utc, "X-Nonce" to nonce,
            "X-Signature" to Base64.encodeToString(signature, Base64.NO_WRAP))
    }
    fun publicRegistration(): JSONObject = JSONObject().put("deviceId",deviceId)
        .put("publicKey",Base64.encodeToString(store.getCertificate(alias).publicKey.encoded,Base64.NO_WRAP))
    companion object { fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) } }
}
