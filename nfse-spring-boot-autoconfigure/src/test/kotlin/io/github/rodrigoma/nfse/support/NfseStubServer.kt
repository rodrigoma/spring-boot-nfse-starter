package io.github.rodrigoma.nfse.support

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpsConfigurator
import com.sun.net.httpserver.HttpsParameters
import com.sun.net.httpserver.HttpsServer
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyStore
import java.security.cert.X509Certificate
import java.util.concurrent.Executors
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory

/**
 * HTTPS stub of the Sefin Nacional built on the JDK's `HttpsServer`: a self-signed `localhost` certificate and,
 * when [trustedClients] is given, real mutual TLS (`needClientAuth`) accepting only those client certificates.
 */
class NfseStubServer(
    private val trustedClients: List<X509Certificate> = emptyList(),
) : AutoCloseable {
    data class Stub(
        val status: Int,
        val body: ByteArray,
        val contentType: String = "application/json",
        val delayMillis: Long = 0,
        val headers: Map<String, String> = emptyMap(),
    )

    data class RecordedRequest(
        val method: String,
        val path: String,
        val body: String,
        val headers: Map<String, List<String>>,
    )

    private val identity = TestCertificates.server()
    private val server: HttpsServer = HttpsServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    private val stubs = mutableMapOf<String, Stub>()
    val requests = mutableListOf<RecordedRequest>()

    val port: Int get() = server.address.port
    val baseUrl: String get() = "https://localhost:$port"

    /** Trust store (PKCS#12, password [TestCertificates.PASSWORD]) holding the server certificate. */
    val trustStore: KeyStore = TestCertificates.trustStoreOf(identity.certificate)

    init {
        val keyStore =
            KeyStore.getInstance("PKCS12").apply {
                load(null, null)
                val password = TestCertificates.PASSWORD.toCharArray()
                setKeyEntry("server", identity.keyPair.private, password, arrayOf(identity.certificate))
            }
        val keyManagers =
            KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply {
                init(keyStore, TestCertificates.PASSWORD.toCharArray())
            }
        val trustManagers =
            TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply {
                init(TestCertificates.trustStoreOf(*trustedClients.toTypedArray()))
            }
        val sslContext =
            SSLContext.getInstance("TLS").apply { init(keyManagers.keyManagers, trustManagers.trustManagers, null) }
        server.httpsConfigurator =
            object : HttpsConfigurator(sslContext) {
                override fun configure(params: HttpsParameters) {
                    val parameters = sslContext.defaultSSLParameters
                    parameters.needClientAuth = trustedClients.isNotEmpty()
                    params.setSSLParameters(parameters)
                }
            }
        server.createContext("/", ::handle)
        server.executor = Executors.newCachedThreadPool()
        server.start()
    }

    @Suppress("LongParameterList")
    fun stub(
        method: String,
        path: String,
        status: Int = 200,
        body: String = "",
        delayMillis: Long = 0,
        headers: Map<String, String> = emptyMap(),
    ) {
        stubs["$method $path"] = Stub(status, body.toByteArray(), "application/json", delayMillis, headers)
    }

    fun stubBytes(
        method: String,
        path: String,
        body: ByteArray,
        contentType: String,
    ) {
        stubs["$method $path"] = Stub(200, body, contentType)
    }

    /** Writes the trust store to a temporary file for `nfse.certificate.trust-store-path`. */
    fun trustStoreFile(): Path =
        Files.createTempFile("nfse-stub-trust", ".p12").also { path ->
            Files.newOutputStream(path).use { trustStore.store(it, TestCertificates.PASSWORD.toCharArray()) }
            path.toFile().deleteOnExit()
        }

    private fun handle(exchange: HttpExchange) {
        val body = exchange.requestBody.readBytes().toString(Charsets.UTF_8)
        val path = exchange.requestURI.path
        requests += RecordedRequest(exchange.requestMethod, path, body, exchange.requestHeaders)
        val stub = stubs["${exchange.requestMethod} $path"]
        if (stub == null) {
            exchange.sendResponseHeaders(404, -1)
            exchange.close()
            return
        }
        if (stub.delayMillis > 0) Thread.sleep(stub.delayMillis)
        exchange.responseHeaders.add("Content-Type", stub.contentType)
        stub.headers.forEach { (name, value) -> exchange.responseHeaders.add(name, value) }
        if (exchange.requestMethod == "HEAD" || stub.body.isEmpty()) {
            exchange.sendResponseHeaders(stub.status, -1)
        } else {
            exchange.sendResponseHeaders(stub.status, stub.body.size.toLong())
            exchange.responseBody.use { it.write(stub.body) }
        }
        exchange.close()
    }

    override fun close() = server.stop(0)
}
