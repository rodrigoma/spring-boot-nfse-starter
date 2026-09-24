package io.github.rodrigoma.nfse.certificate

import io.github.rodrigoma.nfse.autoconfigure.NfseProperties
import io.github.rodrigoma.nfse.exception.NfseException
import org.springframework.boot.ssl.SslBundles
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyStore
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory

/**
 * Builds the `SSLContext` for mutual TLS: the emitter's certificate as the client identity, and either the JDK's
 * default trust store, the one from `nfse.certificate.trust-store-path` (useful for stubs and corporate proxies)
 * or the one of the SSL bundle the certificate came from.
 */
object NfseSslContextFactory {
    fun create(
        certificate: NfseCertificate,
        trustStore: KeyStore? = null,
    ): SSLContext {
        val keyManagers =
            KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply {
                init(certificate.keyStore, certificate.password())
            }
        val trustManagers =
            TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply { init(trustStore) }
        return SSLContext.getInstance("TLS").apply { init(keyManagers.keyManagers, trustManagers.trustManagers, null) }
    }

    /**
     * The trust store is `nfse.certificate.trust-store-path` when set, otherwise the one of the configured SSL
     * bundle, otherwise the JDK default.
     */
    @JvmOverloads
    fun create(
        certificate: NfseCertificate,
        properties: NfseProperties.Certificate,
        sslBundles: SslBundles? = null,
    ): SSLContext = create(certificate, trustStore(properties, sslBundles))

    private fun trustStore(
        properties: NfseProperties.Certificate,
        sslBundles: SslBundles?,
    ): KeyStore? =
        properties.trustStorePath?.let { loadTrustStore(it, properties.trustStorePassword) }
            ?: properties.sslBundle?.let { name ->
                runCatching { sslBundles?.getBundle(name)?.stores?.trustStore }.getOrNull()
            }

    /** PKCS#12 for `.p12`/`.pfx`, JKS otherwise. */
    fun loadTrustStore(
        path: String,
        password: String?,
    ): KeyStore {
        val type = if (path.endsWith(".p12", true) || path.endsWith(".pfx", true)) "PKCS12" else "JKS"
        return runCatching {
            KeyStore.getInstance(type).apply {
                Files.newInputStream(Path.of(path)).use { load(it, password?.toCharArray()) }
            }
        }.getOrElse { throw NfseException.Certificate("Cannot load nfse.certificate.trust-store-path '$path'", it) }
    }
}
