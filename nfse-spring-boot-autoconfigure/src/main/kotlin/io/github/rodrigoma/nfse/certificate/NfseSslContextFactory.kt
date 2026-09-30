package io.github.rodrigoma.nfse.certificate

import io.github.rodrigoma.nfse.autoconfigure.NfseProperties
import io.github.rodrigoma.nfse.exception.NfseException
import org.springframework.boot.ssl.SslBundles
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyStore
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * Builds the `SSLContext` for mutual TLS: the emitter's certificate as the client identity, and the JDK's default
 * roots **plus** whatever `nfse.certificate.trust-store-path` (or the SSL bundle the certificate came from) adds —
 * a configured trust store extends the defaults, it does not replace them.
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
        return SSLContext.getInstance("TLS").apply {
            init(keyManagers.keyManagers, trustManagers(trustStore), null)
        }
    }

    /**
     * A configured trust store **adds to** the JDK's own roots instead of replacing them. Replacing is the JDK
     * default and the wrong one here: the usual reason to configure a trust store is a single missing root — the
     * Sefin's chain is anchored on GlobalSign Root R46, absent from several JDK builds — and silently dropping the
     * other ~100 roots breaks every other TLS call the same `SSLContext` would make.
     */
    internal fun trustManagers(trustStore: KeyStore?): Array<TrustManager> {
        val jdkDefaults = trustManagersOf(null)
        if (trustStore == null) return jdkDefaults
        val configured = trustManagersOf(trustStore)
        val delegates = (configured + jdkDefaults).filterIsInstance<X509TrustManager>()
        return arrayOf(AnyOf(delegates))
    }

    private fun trustManagersOf(trustStore: KeyStore?): Array<TrustManager> =
        TrustManagerFactory
            .getInstance(TrustManagerFactory.getDefaultAlgorithm())
            .apply { init(trustStore) }
            .trustManagers

    /** Trusts what any of the [delegates] trusts; fails with the first rejection when none does. */
    private class AnyOf(
        private val delegates: List<X509TrustManager>,
    ) : X509TrustManager {
        override fun checkClientTrusted(
            chain: Array<out X509Certificate>,
            authType: String,
        ) = check { it.checkClientTrusted(chain, authType) }

        override fun checkServerTrusted(
            chain: Array<out X509Certificate>,
            authType: String,
        ) = check { it.checkServerTrusted(chain, authType) }

        override fun getAcceptedIssuers(): Array<X509Certificate> =
            delegates.flatMap { it.acceptedIssuers.asIterable() }.toTypedArray()

        private fun check(verify: (X509TrustManager) -> Unit) {
            var first: CertificateException? = null
            delegates.forEach { delegate ->
                try {
                    verify(delegate)
                    return
                } catch (e: CertificateException) {
                    if (first == null) first = e
                }
            }
            throw first ?: CertificateException("No trust manager accepted the certificate chain")
        }
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
