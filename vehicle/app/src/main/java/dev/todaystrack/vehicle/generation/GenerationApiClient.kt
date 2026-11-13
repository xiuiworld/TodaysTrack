package dev.todaystrack.vehicle.generation

import android.content.Context
import dev.todaystrack.vehicle.auth.DevelopmentIdentity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.security.KeyStore
import java.security.cert.CertificateFactory
import javax.net.ssl.*
import java.net.URL
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class GenerationApiClient(private val context: Context, private val identity: DevelopmentIdentity) {
    val baseUrl = "https://10.0.2.2:8443"
    private val socketFactory: SSLSocketFactory by lazy {
        val id = context.resources.getIdentifier("development_server", "raw", context.packageName)
        check(id != 0) { "Development TLS certificate required" }
        val cert = context.resources.openRawResource(id).use { CertificateFactory.getInstance("X.509").generateCertificate(it) }
        val trust = KeyStore.getInstance(KeyStore.getDefaultType()).apply { load(null); setCertificateEntry("development-server", cert) }
        val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply { init(trust) }
        SSLContext.getInstance("TLS").apply { init(null, factory.trustManagers, null) }.socketFactory
    }
    suspend fun request(method: String, path: String, body: JSONObject? = null): JSONObject = withContext(Dispatchers.IO) {
        val bytes = body?.toString()?.toByteArray(Charsets.UTF_8) ?: byteArrayOf()
        val connection = (URL(baseUrl + path).openConnection() as HttpsURLConnection).apply {
            sslSocketFactory = socketFactory
            requestMethod = method
            connectTimeout = 10000
            readTimeout = 15000
            setRequestProperty("Content-Type", "application/json")
            identity.headers(method, path, bytes).forEach { (key, value) -> setRequestProperty(key, value) }
            if (body != null) { doOutput = true; setFixedLengthStreamingMode(bytes.size) }
        }
        try {
            if (body != null) connection.outputStream.use { it.write(bytes) }
            val code = connection.responseCode
            if (code !in 200..299) error("API_$code")
            val text = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText().also { raw -> check(raw.length <= 128_000) } }
            JSONObject(text)
        } finally { connection.disconnect() }
    }
    suspend fun download(path: String, target: File, expectedHash: String, expectedBytes: Long) = withContext(Dispatchers.IO) {
        require(path.matches(Regex("/v1/assets/[a-f0-9-]{36}/audio")))
        require(expectedBytes in 1024..20L*1024*1024)
        if(target.exists() && target.length()==expectedBytes && DevelopmentIdentity.sha256(target.readBytes())==expectedHash) return@withContext
        target.parentFile!!.mkdirs()
        val temporary=File(target.parentFile,target.name+".part")
        val connection=(URL(baseUrl+path).openConnection() as HttpsURLConnection).apply {
            sslSocketFactory=socketFactory;connectTimeout=10000;readTimeout=30000
            identity.headers("GET",path,byteArrayOf()).forEach { (k,v)->setRequestProperty(k,v) }
        }
        try {
            check(connection.responseCode==200) { "ASSET_UNAVAILABLE" }
            var received=0L
            connection.inputStream.use { input -> temporary.outputStream().use { out ->
                val bytes=ByteArray(8192)
                while(true) { val n=input.read(bytes);if(n<0)break;received+=n;check(received<=expectedBytes);out.write(bytes,0,n) }
                out.fd.sync()
            } }
            check(received==expectedBytes && DevelopmentIdentity.sha256(temporary.readBytes())==expectedHash) { "ASSET_INTEGRITY" }
            Files.move(temporary.toPath(),target.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING)
        } finally { connection.disconnect() }
    }
}
