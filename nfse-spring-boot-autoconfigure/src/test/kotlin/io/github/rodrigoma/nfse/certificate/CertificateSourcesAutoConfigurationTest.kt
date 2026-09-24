package io.github.rodrigoma.nfse.certificate

import io.github.rodrigoma.nfse.autoconfigure.NfseAutoConfiguration
import io.github.rodrigoma.nfse.support.TestCertificates
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.autoconfigure.ssl.SslAutoConfiguration
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64

/**
 * The four ways a certificate reaches the library, as a running context: a file, a Base64 environment variable, an
 * SSL bundle of Spring Boot, and an [NfseCertificateProvider] bean standing in for a vault.
 */
class CertificateSourcesAutoConfigurationTest {
    private val emitter = TestCertificates.emitter()

    private val contextRunner =
        ApplicationContextRunner()
            .withConfiguration(
                AutoConfigurations.of(NfseAutoConfiguration::class.java, SslAutoConfiguration::class.java),
            ).withPropertyValues(
                "nfse.emitter.cnpj=${TestCertificates.CNPJ}",
                "nfse.emitter.municipality-ibge=3550308",
            )

    private fun pfx(
        dir: Path,
        identity: TestCertificates.Identity = emitter,
    ): Path = dir.resolve("certificado.pfx").also { Files.write(it, identity.pkcs12()) }

    @Test
    fun `loads the certificate from a mounted file`(
        @TempDir dir: Path,
    ) {
        contextRunner
            .withPropertyValues(
                "nfse.certificate.location=file:${pfx(dir)}",
                "nfse.certificate.password=${TestCertificates.PASSWORD}",
            ).run { context ->
                assertThat(context).hasNotFailed()
                val certificate = context.getBean(NfseCertificate::class.java)
                assertThat(certificate.source).isEqualTo(NfseCertificate.LOCATION_SOURCE)
                assertThat(certificate.certificate).isEqualTo(emitter.certificate)
            }
    }

    @Test
    fun `loads the certificate from a Base64 environment variable`() {
        contextRunner
            .withPropertyValues(
                "nfse.certificate.base64=${Base64.getEncoder().encodeToString(emitter.pkcs12())}",
                "nfse.certificate.password=${TestCertificates.PASSWORD}",
            ).run { context ->
                assertThat(context).hasNotFailed()
                assertThat(context.getBean(NfseCertificate::class.java).certificate).isEqualTo(emitter.certificate)
            }
    }

    @Test
    fun `loads the certificate from a Spring Boot SSL bundle`(
        @TempDir dir: Path,
    ) {
        contextRunner
            .withPropertyValues(
                "spring.ssl.bundle.jks.nfse.keystore.location=file:${pfx(dir)}",
                "spring.ssl.bundle.jks.nfse.keystore.password=${TestCertificates.PASSWORD}",
                "spring.ssl.bundle.jks.nfse.keystore.type=PKCS12",
                "spring.ssl.bundle.jks.nfse.key.password=${TestCertificates.PASSWORD}",
                "nfse.certificate.ssl-bundle=nfse",
            ).run { context ->
                assertThat(context).hasNotFailed()
                val certificate = context.getBean(NfseCertificate::class.java)
                assertThat(certificate.source).isEqualTo(NfseCertificate.SSL_BUNDLE_SOURCE)
                assertThat(certificate.certificate).isEqualTo(emitter.certificate)
            }
    }

    @Test
    fun `a missing SSL bundle fails the context by name`() {
        contextRunner.withPropertyValues("nfse.certificate.ssl-bundle=nao-existe").run { context ->
            assertThat(context).hasFailed()
            // The Spring exception stays as the cause; the library's message names the property to fix.
            assertThat(context.startupFailure)
                .hasStackTraceContaining("No SSL bundle named 'nao-existe'")
                .hasRootCauseMessage("SSL bundle name 'nao-existe' cannot be found")
        }
    }

    @Test
    fun `a provider bean wins over the properties, and works without them`(
        @TempDir dir: Path,
    ) {
        val fromVault = TestCertificates.emitter(cnpj = "98765432000198")

        contextRunner.withUserConfiguration(VaultConfiguration::class.java).run { context ->
            assertThat(context).hasNotFailed()
            val certificate = context.getBean(NfseCertificate::class.java)
            assertThat(certificate.source).isEqualTo(NfseCertificate.PROVIDER_SOURCE)
            assertThat(certificate.certificate).isEqualTo(VaultConfiguration.identity.certificate)
        }

        // Properties set as well — even two of them, which alone would fail — are not read at all.
        contextRunner
            .withUserConfiguration(VaultConfiguration::class.java)
            .withPropertyValues(
                "nfse.certificate.location=file:${pfx(dir, fromVault)}",
                "nfse.certificate.base64=AAAA",
                "nfse.certificate.password=${TestCertificates.PASSWORD}",
            ).run { context ->
                assertThat(context).hasNotFailed()
                assertThat(context.getBean(NfseCertificate::class.java).certificate)
                    .isEqualTo(VaultConfiguration.identity.certificate)
            }
    }

    @Test
    fun `two configured sources fail the context, naming both`(
        @TempDir dir: Path,
    ) {
        contextRunner
            .withPropertyValues(
                "nfse.certificate.location=file:${pfx(dir)}",
                "nfse.certificate.base64=AAAA",
                "nfse.certificate.password=${TestCertificates.PASSWORD}",
            ).run { context ->
                assertThat(context).hasFailed()
                assertThat(context.startupFailure)
                    .rootCause()
                    .hasMessageContaining("only one")
                    .hasMessageContaining("nfse.certificate.location")
                    .hasMessageContaining("nfse.certificate.base64")
            }
    }

    @Test
    fun `no source at all fails the context, listing the options`() {
        contextRunner.run { context ->
            assertThat(context).hasFailed()
            assertThat(context.startupFailure)
                .rootCause()
                .hasMessageContaining("No certificate configured")
                .hasMessageContaining("NfseCertificateProvider")
        }
    }

    @Test
    fun `an expired certificate starts the context, because the application does more than issue notes`() {
        val expired = TestCertificates.emitter(expired = true)

        contextRunner
            .withPropertyValues(
                "nfse.certificate.base64=${Base64.getEncoder().encodeToString(expired.pkcs12())}",
                "nfse.certificate.password=${TestCertificates.PASSWORD}",
            ).run { context ->
                assertThat(context).hasNotFailed()
                assertThat(context.getBean(NfseCertificate::class.java).isExpired).isTrue()
            }
    }

    @Configuration(proxyBeanMethods = false)
    class VaultConfiguration {
        @Bean
        fun nfseCertificateProvider(): NfseCertificateProvider =
            NfseCertificateProvider { NfseKeyStore.ofPkcs12(identity.pkcs12(), TestCertificates.PASSWORD) }

        companion object {
            val identity: TestCertificates.Identity = TestCertificates.emitter(cnpj = "11222333000181")
        }
    }
}
