package io.github.rodrigoma.nfse.autoconfigure

import io.github.rodrigoma.nfse.model.dps.FederalId
import io.github.rodrigoma.nfse.model.dps.SimplesNacionalAssessment
import io.github.rodrigoma.nfse.model.dps.SimplesNacionalOption
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import java.time.Duration

class NfsePropertiesTest {
    private val contextRunner = ApplicationContextRunner().withUserConfiguration(PropertiesTestConfig::class.java)

    private val required =
        arrayOf(
            "nfse.certificate.location=file:/tmp/cert.pfx",
            "nfse.certificate.password=secret",
            "nfse.emitter.cnpj=12.345.678/0001-95",
            "nfse.emitter.municipality-ibge=3550308",
        )

    @Test
    fun `binds every property and resolves the defaults`() {
        contextRunner
            .withPropertyValues(
                *required,
                "nfse.environment=production",
                "nfse.emitter.municipal-registration=123",
                "nfse.emitter.dps-series=7",
                "nfse.emitter.tax-regime.simples-nacional=me_epp",
                "nfse.emitter.tax-regime.simples-nacional-assessment=simples_nacional",
                "nfse.emitter.address.street=Rua A",
                "nfse.emitter.address.number=1",
                "nfse.emitter.address.district=Centro",
                "nfse.emitter.address.zip-code=01001000",
                "nfse.emitter.address.state=SP",
                "nfse.read-timeout=5s",
                "nfse.log-requests=true",
            ).run { context ->
                assertThat(context).hasNotFailed()
                val props = context.getBean(NfseProperties::class.java)
                assertThat(props.environment).isEqualTo(NfseEnvironment.PRODUCTION)
                assertThat(props.resolvedSefinBaseUrl()).isEqualTo("https://sefin.nfse.gov.br/SefinNacional")
                assertThat(props.resolvedDanfseBaseUrl()).isEqualTo("https://adn.nfse.gov.br/danfse")
                assertThat(props.resolvedAdnBaseUrl()).isEqualTo("https://adn.nfse.gov.br/contribuintes")
                assertThat(
                    props.resolvedMunicipalParametersBaseUrl(),
                ).isEqualTo("https://adn.nfse.gov.br/parametrizacao")
                assertThat(props.emitterFederalId()).isEqualTo(FederalId.Cnpj("12345678000195"))
                assertThat(props.emitter.dpsSeries).isEqualTo(7)
                assertThat(props.emitter.taxRegime.simplesNacional).isEqualTo(SimplesNacionalOption.ME_EPP)
                assertThat(
                    props.emitterProvider().taxRegime.simplesNacionalAssessment,
                ).isEqualTo(SimplesNacionalAssessment.SIMPLES_NACIONAL)
                assertThat(props.emitterAddress()?.street).isEqualTo("Rua A")
                assertThat(props.readTimeout).isEqualTo(Duration.ofSeconds(5))
                assertThat(props.connectTimeout).isEqualTo(Duration.ofSeconds(10))
                assertThat(props.logRequests).isTrue()
                assertThat(props.resolvedApplicationVersion()).startsWith("nfse-boot/").hasSizeLessThanOrEqualTo(20)
            }
    }

    @Test
    fun `defaults to restricted production and the official URLs`() {
        contextRunner.withPropertyValues(*required).run { context ->
            val props = context.getBean(NfseProperties::class.java)
            assertThat(props.environment).isEqualTo(NfseEnvironment.RESTRICTED_PRODUCTION)
            assertThat(props.resolvedSefinBaseUrl())
                .isEqualTo("https://sefin.producaorestrita.nfse.gov.br/SefinNacional")
            assertThat(props.resolvedDanfseBaseUrl()).isEqualTo("https://adn.producaorestrita.nfse.gov.br/danfse")
            assertThat(props.resolvedAdnBaseUrl()).isEqualTo("https://adn.producaorestrita.nfse.gov.br/contribuintes")
            assertThat(props.resolvedMunicipalParametersBaseUrl())
                .isEqualTo("https://adn.producaorestrita.nfse.gov.br/parametrizacao")
            assertThat(props.emitterAddress()).isNull()
            assertThat(props.emitterProvider().name).isNull()
        }
    }

    @Test
    fun `base urls override the environment`() {
        contextRunner
            .withPropertyValues(
                *required,
                "nfse.base-url.sefin=http://localhost:1234",
                "nfse.base-url.danfse=http://localhost:1234/pdf",
            ).run { context ->
                val props = context.getBean(NfseProperties::class.java)
                assertThat(props.resolvedSefinBaseUrl()).isEqualTo("http://localhost:1234")
                assertThat(props.resolvedDanfseBaseUrl()).isEqualTo("http://localhost:1234/pdf")
            }
    }

    @Test
    fun `accepts a CPF emitter`() {
        contextRunner
            .withPropertyValues(
                "nfse.certificate.base64=AAAA",
                "nfse.emitter.cpf=123.456.789-09",
                "nfse.emitter.municipality-ibge=1",
            ).run { context ->
                assertThat(context).hasNotFailed()
                val props = context.getBean(NfseProperties::class.java)
                assertThat(props.emitterFederalId()).isEqualTo(FederalId.Cpf("12345678909"))
            }
    }

    @Test
    fun `fails fast on invalid configuration with a message naming the property`() {
        val cases =
            mapOf(
                // The certificate sources are not validated here — see CertificateSourcesAutoConfigurationTest,
                // because only the certificate bean knows whether an NfseCertificateProvider is published.
                arrayOf("nfse.certificate.location=file:/x", "nfse.emitter.municipality-ibge=1") to "nfse.emitter.cnpj",
                arrayOf(*required, "nfse.emitter.cpf=12345678909") to "exactly one of nfse.emitter",
                arrayOf(
                    "nfse.certificate.location=file:/x",
                    "nfse.emitter.cnpj=123",
                    "nfse.emitter.municipality-ibge=1",
                ) to
                    "CNPJ must have",
                arrayOf("nfse.certificate.location=file:/x", "nfse.emitter.cnpj=12345678000195") to "municipality-ibge",
                arrayOf(*required, "nfse.emitter.dps-series=0") to "dps-series",
                arrayOf(*required, "nfse.emitter.tax-regime.simples-nacional=me_epp") to "simples-nacional-assessment",
                arrayOf(*required, "nfse.emitter.tax-regime.simples-nacional-assessment=none") to
                    "simples-nacional-assessment",
                arrayOf(*required, "nfse.emitter.address.street=Rua") to "nfse.emitter.address",
                arrayOf(*required, "nfse.application-version= ") to "application-version",
                arrayOf(*required, "nfse.base-url.sefin=/relative") to "nfse.base-url.sefin",
                arrayOf(*required, "nfse.base-url.adn=relative") to "nfse.base-url.adn",
            )
        cases.forEach { (values, message) ->
            contextRunner.withPropertyValues(*values).run { context ->
                assertThat(context).hasFailed()
                assertThat(context.startupFailure).rootCause().hasMessageContaining(message)
            }
        }
    }

    @Test
    fun `toString never prints secrets`() {
        val props =
            NfseProperties(
                certificate =
                    NfseProperties.Certificate(
                        base64 = "SECRET_BLOB",
                        password = "SECRET_PASSWORD",
                        trustStorePassword = "TS",
                    ),
            )
        assertThat(props.toString())
            .doesNotContain("SECRET_BLOB")
            .doesNotContain("SECRET_PASSWORD")
            .contains("password=<hidden>")
            .contains("base64=<hidden>")
    }

    @EnableConfigurationProperties(NfseProperties::class)
    class PropertiesTestConfig
}
