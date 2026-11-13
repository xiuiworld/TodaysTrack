package dev.todaystrack.server

import java.security.MessageDigest
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

class DeviceAuth(private val store: JobStore) {
    fun authenticate(method: String, path: String, body: ByteArray, headers: Map<String,String>): String? = verified(method,path,body,headers,setOf("CAR"))?.ownerId
    fun authenticatePrincipal(method: String,path: String,body: ByteArray,headers: Map<String,String>): dev.todaystrack.server.music.MusicPrincipal? = verified(method,path,body,headers,setOf("CAR","PHONE"))
    private fun verified(method: String,path: String,body: ByteArray,headers: Map<String,String>,roles: Set<String>): dev.todaystrack.server.music.MusicPrincipal? = runCatching {
        val id=headers.getValue("X-Device-Id")
        val stamp=headers.getValue("X-Timestamp")
        val nonce=headers.getValue("X-Nonce")
        require(id.length<=80 && nonce.matches(Regex("[A-Za-z0-9-]{1,80}")))
        val now=System.currentTimeMillis()
        require(stamp.toLong() in (now-120_000)..(now+120_000))
        val device=store.device(id) ?: return null
        require(device["kind"] in roles)
        val key=KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(device.getValue("public_key"))))
        val canonical="$method\n$path\n$id\n$stamp\n$nonce\n${sha256(body)}"
        val valid=Signature.getInstance("SHA256withECDSA").apply { initVerify(key); update(canonical.toByteArray()) }
            .verify(Base64.getDecoder().decode(headers.getValue("X-Signature")))
        require(valid && store.useNonce(id,nonce,System.currentTimeMillis()))
        dev.todaystrack.server.music.MusicPrincipal(device.getValue("owner")!!,id,dev.todaystrack.music.DeviceRole.valueOf(device.getValue("kind")!!))
    }.getOrNull()
}
