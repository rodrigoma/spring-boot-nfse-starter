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
import org.springframework.core.io.ClassPathResource
import org.springframework.core.io.FileSystemResource
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import java.util.Base64

class NfseCertificateTest {
    private fun load(identity: TestCertificates.Identity) = NfseCertificate.load(identity.pkcs12(), PASSWORD)

    private fun load(properties: NfseProperties.Certificate) = NfseCertificate.load(properties)

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
    fun `location and base64 load the same key store, line breaks and all`(
        @TempDir dir: Path,
    ) {
        val pfx = TestCertificates.emitter().pkcs12()
        val file = dir.resolve("cert.pfx").also { Files.write(it, pfx) }

        val fromFile = load(NfseProperties.Certificate(location = FileSystemResource(file), password = PASSWORD))
        val fromPath =
            load(NfseProperties.Certificate(location = FileSystemResource(file.toString()), password = PASSWORD))
        // `base64` on macOS and a forgotten `-w0` on Linux wrap the output — the real case, and the one a strict
        // decoder would reject with "Illegal base64 character a".
        val wrapped = Base64.getMimeEncoder().encodeToString(pfx)
        assertThat(wrapped).contains("\n")
        val fromBase64 = load(NfseProperties.Certificate(base64 = wrapped, password = PASSWORD))
        val unwrapped = Base64.getEncoder().encodeToString(pfx)
        val fromSingleLine = load(NfseProperties.Certificate(base64 = unwrapped, password = PASSWORD))

        assertThat(listOf(fromPath, fromBase64, fromSingleLine).map { it.certificate })
            .containsOnly(fromFile.certificate)
        assertThat(fromFile.source).isEqualTo(NfseCertificate.LOCATION_SOURCE)
        assertThat(fromBase64.source).isEqualTo(NfseCertificate.BASE64_SOURCE)
    }

    @Test
    fun `loads from the classpath, as tests and demos configure it`() {
        val properties = NfseProperties.Certificate(location = ClassPathResource(CLASSPATH_PFX), password = PASSWORD)

        val certificate = load(properties)

        assertThat(certificate.subject).contains("EMPRESA TESTE")
        assertThat(certificate.isUsable).isTrue()
    }

    @Test
    fun `refuses more than one source, naming them, and none at all, listing the options`() {
        val both =
            NfseProperties.Certificate(
                location = ClassPathResource(CLASSPATH_PFX),
                base64 = "AAAA",
                password = PASSWORD,
            )
        assertThatThrownBy { load(both) }
            .isInstanceOf(NfseException.Certificate::class.java)
            .hasMessageContaining("only one")
            .hasMessageContaining("nfse.certificate.location")
            .hasMessageContaining("nfse.certificate.base64")

        assertThatThrownBy { load(NfseProperties.Certificate()) }
            .isInstanceOf(NfseException.Certificate::class.java)
            .hasMessageContaining("nfse.certificate.ssl-bundle")
            .hasMessageContaining("nfse.certificate.location")
            .hasMessageContaining("nfse.certificate.base64")
            .hasMessageContaining("NfseCertificateProvider")
    }

    @Test
    fun `picks the alias, or the first key entry, and lists what it found otherwise`() {
        val pfx = TestCertificates.twoKeyEntries()

        val byAlias = NfseCertificate.load(pfx, PASSWORD, alias = "outra")
        val withoutAlias = NfseCertificate.load(pfx, PASSWORD)

        assertThat(byAlias.federalId).isEqualTo(FederalId.Cnpj("98765432000198"))
        // Without an alias the first key entry wins — and a PKCS#12 does not promise insertion order, which is
        // exactly why `alias` exists.
        assertThat(withoutAlias.federalId)
            .isIn(FederalId.Cnpj(TestCertificates.CNPJ), FederalId.Cnpj("98765432000198"))
        assertThatThrownBy { NfseCertificate.load(pfx, PASSWORD, alias = "nao-existe") }
            .isInstanceOf(NfseException.Certificate::class.java)
            .hasMessageContaining("nfse.certificate.alias 'nao-existe'")
            .hasMessageContaining("emitter")
            .hasMessageContaining("outra")
    }

    @Test
    fun `an expired or not yet valid certificate loads, but refuses to sign`() {
        val expired = load(TestCertificates.emitter(expired = true))

        assertThat(expired.isExpired).isTrue()
        assertThat(expired.isUsable).isFalse()
        assertThat(expired.expiresAt).isBefore(Instant.now())
        assertThat(expired.unusableReason).contains("expired on")
        assertThatThrownBy { expired.requireUsable() }
            .isInstanceOf(NfseException.Certificate::class.java)
            .hasMessageContaining("cannot be signed")

        val valid = load(TestCertificates.emitter())
        assertThat(valid.isUsable).isTrue()
        assertThat(valid.unusableReason).isNull()
        assertThat(valid.expiresWithin(Duration.ofDays(1))).isFalse()
        assertThat(valid.expiresWithin(Duration.ofDays(400))).isTrue()
        valid.requireUsable()
    }

    @Test
    fun `a provider wins and carries its own key store`() {
        val provider = NfseCertificateProvider { NfseKeyStore.ofPkcs12(TestCertificates.emitter().pkcs12(), PASSWORD) }

        val certificate = NfseCertificate.load(provider.provide())

        assertThat(certificate.federalId).isEqualTo(FederalId.Cnpj(TestCertificates.CNPJ))
        assertThat(certificate.source).isEqualTo(NfseCertificate.PROVIDER_SOURCE)
        assertThat(provider.provide().toString()).doesNotContain(PASSWORD).contains("<hidden>")
    }

    @Test
    fun `reports a wrong password, a missing file and bad base64 without echoing the secret`() {
        val pfx = TestCertificates.emitter().pkcs12()
        assertThatThrownBy { NfseCertificate.load(pfx, "s3nh4-errada") }
            .isInstanceOf(NfseException.Certificate::class.java)
            .hasMessageContaining("wrong password")
            .hasMessageNotContaining("s3nh4-errada")
        assertThatThrownBy { load(NfseProperties.Certificate(location = FileSystemResource("/nowhere/cert.pfx"))) }
            .isInstanceOf(NfseException.Certificate::class.java)
            .hasMessageContaining("nfse.certificate.location")
        assertThatThrownBy { load(NfseProperties.Certificate(base64 = "###")) }
            .isInstanceOf(NfseException.Certificate::class.java)
            .hasMessageContaining("Base64")
    }

    @Test
    fun `rejects CA, v1 and signature-less certificates`() {
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

    private companion object {
/** Minted by the `generateTestCertificate` Gradle task; no .pfx is committed. */
        const val CLASSPATH_PFX = "certificate/emitter.pfx"
    }
}
