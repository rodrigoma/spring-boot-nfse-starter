package io.github.rodrigoma.nfse.certificate

import io.github.rodrigoma.nfse.support.TestCertificates
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.security.KeyStore
import java.security.cert.CertificateException
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * A configured trust store must **extend** the JDK's roots, not replace them. Replacing is the JDK default and the
 * wrong behaviour here: the usual reason to configure one is a single missing root — the Sefin's chain is anchored
 * on GlobalSign Root R46, which several JDK builds do not carry — and dropping the other ~100 roots silently
 * breaks every other TLS call made through the same context.
 */
class TrustStoreMergeTest {
    private val stub = TestCertificates.server()
    private val stranger = TestCertificates.server("outro.example").certificate

    private fun managerFor(trustStore: KeyStore?): X509TrustManager =
        NfseSslContextFactory.trustManagers(trustStore).filterIsInstance<X509TrustManager>().single()

    private fun jdkDefaults(): X509TrustManager =
        TrustManagerFactory
            .getInstance(TrustManagerFactory.getDefaultAlgorithm())
            .apply { init(null as KeyStore?) }
            .trustManagers
            .filterIsInstance<X509TrustManager>()
            .first()

    @Test
    fun `a configured trust store adds to the JDK roots instead of replacing them`() {
        val onlyTheStub = TestCertificates.trustStoreOf(stub.certificate)

        val merged = managerFor(onlyTheStub).acceptedIssuers.toList()

        assertThat(merged).contains(stub.certificate)
        assertThat(merged).containsAll(jdkDefaults().acceptedIssuers.toList())
        assertThat(merged).hasSize(jdkDefaults().acceptedIssuers.size + 1)
    }

    @Test
    fun `a certificate only the configured store knows is accepted`() {
        val manager = managerFor(TestCertificates.trustStoreOf(stub.certificate))

        assertThatCode { manager.checkServerTrusted(arrayOf(stub.certificate), "RSA") }
            .doesNotThrowAnyException()
    }

    @Test
    fun `a certificate nobody knows is still rejected`() {
        val manager = managerFor(TestCertificates.trustStoreOf(stub.certificate))

        assertThatThrownBy { manager.checkServerTrusted(arrayOf(stranger), "RSA") }
            .isInstanceOf(CertificateException::class.java)
    }

    @Test
    fun `without a configured store the JDK roots are used untouched`() {
        val manager = managerFor(null)

        assertThat(manager.acceptedIssuers).hasSameSizeAs(jdkDefaults().acceptedIssuers)
    }
}
