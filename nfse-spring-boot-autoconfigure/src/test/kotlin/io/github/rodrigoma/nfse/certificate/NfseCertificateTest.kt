package io.github.rodrigoma.nfse.certificate

import io.github.rodrigoma.nfse.autoconfigure.NfseProperties
import io.github.rodrigoma.nfse.exception.NfseException
import io.github.rodrigoma.nfse.model.dps.FederalId
import io.github.rodrigoma.nfse.support.TestCertificates
import io.github.rodrigoma.nfse.support.TestCertificates.PASSWORD
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.bouncycastle.asn1.x509.KeyUsage
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64

class NfseCertificateTest {
    private fun load(identity: TestCertificates.Identity) = NfseCertificate.load(identity.pkcs12(), PASSWORD)

    @Test
    fun `loads a PKCS12 from a path and reads the CNPJ from the ICP-Brasil extension`() {
        val pfx = TestCertificates.emitter().pkcs12()
        val certificate = NfseCertificate.load(pfx, TestCertificates.PASSWORD)

        assertThat(certificate.federalId).isEqualTo(FederalId.Cnpj(TestCertificates.CNPJ))
        assertThat(certificate.subject).contains("EMPRESA TESTE")
        assertThat(certificate.chain).hasSize(1)
        assertThat(certificate.toString()).contains("subject=").doesNotContain(TestCertificates.PASSWORD)
        assertThat(certificate.signer()).isNotNull()
    }

    @Test
    fun `loads from pfx-path and pfx-base64 properties`(
        @TempDir dir: Path,
    ) {
        val pfx = TestCertificates.emitter().pkcs12()
        val file = dir.resolve("cert.pfx").also { Files.write(it, pfx) }

        val fromPath = NfseCertificate.load(NfseProperties.Certificate(pfxPath = file.toString(), password = PASSWORD))
        val base64 = Base64.getMimeEncoder().encodeToString(pfx)
        val fromBase64 = NfseCertificate.load(NfseProperties.Certificate(pfxBase64 = base64, password = PASSWORD))

        assertThat(fromPath.certificate).isEqualTo(fromBase64.certificate)
    }

    @Test
    fun `reports a wrong password, a missing file, bad base64 and missing configuration clearly`() {
        val pfx = TestCertificates.emitter().pkcs12()
        assertThatThrownBy { NfseCertificate.load(pfx, "wrong") }
            .isInstanceOf(NfseException.Certificate::class.java)
            .hasMessageContaining("wrong password")
        assertThatThrownBy { NfseCertificate.load(NfseProperties.Certificate(pfxPath = "/nowhere/cert.pfx")) }
            .isInstanceOf(NfseException.Certificate::class.java)
            .hasMessageContaining("pfx-path")
        assertThatThrownBy { NfseCertificate.load(NfseProperties.Certificate(pfxBase64 = "***")) }
            .isInstanceOf(NfseException.Certificate::class.java)
            .hasMessageContaining("Base64")
        assertThatThrownBy { NfseCertificate.load(NfseProperties.Certificate()) }
            .isInstanceOf(NfseException.Certificate::class.java)
            .hasMessageContaining("pfx-path")
    }

    @Test
    fun `rejects expired, CA, v1 and signature-less certificates`() {
        assertThatThrownBy { load(TestCertificates.emitter(expired = true)) }
            .isInstanceOf(NfseException.Certificate::class.java)
            .hasMessageContaining("expired")
        assertThatThrownBy { load(TestCertificates.emitter(ca = true)) }
            .isInstanceOf(NfseException.Certificate::class.java)
            .hasMessageContaining("CA certificate")
        assertThatThrownBy { load(TestCertificates.emitter(version = 1)) }
            .isInstanceOf(NfseException.Certificate::class.java)
            .hasMessageContaining("v3")
        assertThatThrownBy { load(TestCertificates.emitter(keyUsages = KeyUsage.keyEncipherment)) }
            .isInstanceOf(NfseException.Certificate::class.java)
            .hasMessageContaining("Non Repudiation")
    }

    @Test
    fun `accepts a certificate without key usage or ICP-Brasil extensions`() {
        assertThat(load(TestCertificates.emitter(cnpj = null, keyUsages = null)).federalId).isNull()
    }

    @Test
    fun `reads an alphanumeric CNPJ from the extension`() {
        val certificate = load(TestCertificates.emitter(cnpj = "12ABC34501DE35"))
        assertThat(certificate.federalId).isEqualTo(FederalId.Cnpj("12ABC34501DE35"))
    }

    @Test
    fun `reads the CPF from the e-CPF extension`() {
        val certificate = load(TestCertificates.emitter(cnpj = null, cpf = true))
        assertThat(certificate.federalId).isEqualTo(FederalId.Cpf("12345678909"))
    }

    @Test
    fun `builds an SSL context with the default or a custom trust store`(
        @TempDir dir: Path,
    ) {
        val certificate = NfseCertificate.load(TestCertificates.emitter().pkcs12(), TestCertificates.PASSWORD)
        assertThat(NfseSslContextFactory.create(certificate, NfseProperties.Certificate())).isNotNull()

        val trustStore = dir.resolve("trust.p12")
        Files.newOutputStream(trustStore).use {
            TestCertificates.trustStoreOf(certificate.certificate).store(it, TestCertificates.PASSWORD.toCharArray())
        }
        val properties =
            NfseProperties.Certificate(trustStorePath = trustStore.toString(), trustStorePassword = PASSWORD)
        assertThat(NfseSslContextFactory.create(certificate, properties)).isNotNull()
        assertThatThrownBy { NfseSslContextFactory.loadTrustStore(dir.resolve("missing.jks").toString(), null) }
            .isInstanceOf(NfseException.Certificate::class.java)
    }
}
