package io.github.rodrigoma.nfse.autoconfigure

import io.github.rodrigoma.nfse.certificate.NfseCertificate
import io.github.rodrigoma.nfse.client.NfseClient
import io.github.rodrigoma.nfse.exception.NfseException
import io.github.rodrigoma.nfse.support.TestCertificates
import io.github.rodrigoma.nfse.support.TestDps
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import java.nio.file.Files
import java.nio.file.Path

@ExtendWith(OutputCaptureExtension::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NfseAutoConfigurationTest {
    private lateinit var pfx: Path
    private lateinit var otherHolderPfx: Path

    @BeforeAll
    fun certificates() {
        pfx = Files.createTempFile("emitter", ".pfx").also { Files.write(it, TestCertificates.emitter().pkcs12()) }
        val otherHolder = TestCertificates.emitter(cnpj = "99999999000191").pkcs12()
        otherHolderPfx = Files.createTempFile("other", ".pfx").also { Files.write(it, otherHolder) }
    }

    private val contextRunner =
        ApplicationContextRunner().withConfiguration(AutoConfigurations.of(NfseAutoConfiguration::class.java))

    private fun required(pfxPath: Path = pfx) =
        arrayOf(
            "nfse.certificate.pfx-path=$pfxPath",
            "nfse.certificate.password=${TestCertificates.PASSWORD}",
            "nfse.emitter.cnpj=${TestDps.CNPJ}",
            "nfse.emitter.municipality-ibge=${TestDps.MUNICIPALITY}",
        )

    @Test
    fun `creates the certificate, rest client and client beans`(output: CapturedOutput) {
        contextRunner.withPropertyValues(*required()).run { context ->
            assertThat(context).hasNotFailed()
            assertThat(context).hasSingleBean(NfseCertificate::class.java)
            assertThat(context).hasSingleBean(NfseClient::class.java)
            assertThat(context.getBeanNamesForType(RestClient::class.java)).contains("nfseRestClient")
            assertThat(output).contains("NFS-e certificate loaded").doesNotContain(TestCertificates.PASSWORD)
            assertThat(output).doesNotContain("E0718")
        }
    }

    @Test
    fun `does nothing when disabled`() {
        contextRunner.withPropertyValues("nfse.enabled=false").run { context ->
            assertThat(context).hasNotFailed()
            assertThat(context).doesNotHaveBean(NfseClient::class.java)
            assertThat(context).doesNotHaveBean(NfseProperties::class.java)
        }
    }

    @Test
    fun `fails to start without the required properties`() {
        contextRunner.run { context ->
            assertThat(context).hasFailed()
            assertThat(context.startupFailure).rootCause().hasMessageContaining("nfse.certificate")
        }
    }

    @Test
    fun `an invalid certificate fails the context with a clear message`() {
        contextRunner.withPropertyValues(*required(), "nfse.certificate.password=wrong").run { context ->
            assertThat(context).hasFailed()
            val certificateFailure =
                generateSequence(
                    context.startupFailure,
                ) { it.cause }.filterIsInstance<NfseException.Certificate>().first()
            assertThat(certificateFailure).hasMessageContaining("wrong password")
        }
    }

    @Test
    fun `warns when the certificate holder differs from the configured emitter`(output: CapturedOutput) {
        contextRunner.withPropertyValues(*required(otherHolderPfx)).run { context ->
            assertThat(context).hasNotFailed()
            assertThat(output).contains("E0718").contains("99999999000191")
        }
    }

    @Test
    fun `targets the configured base url and applies customizers`() {
        contextRunner
            .withPropertyValues(*required(), "nfse.base-url.sefin=http://localhost:1234")
            .withUserConfiguration(CustomizerConfig::class.java)
            .run { context ->
                val builder = context.getBean("nfseRestClient", RestClient::class.java).mutate()
                val server = MockRestServiceServer.bindTo(builder).build()
                server
                    .expect(requestTo("http://localhost:1234/nfse/1"))
                    .andExpect(header("X-Custom", "yes"))
                    .andRespond(withSuccess())
                builder
                    .build()
                    .get()
                    .uri("/nfse/{k}", "1")
                    .retrieve()
                    .toBodilessEntity()
                server.verify()
            }
    }

    @Test
    fun `defaults to the restricted production URL`() {
        contextRunner.withPropertyValues(*required()).run { context ->
            val builder = context.getBean("nfseRestClient", RestClient::class.java).mutate()
            val server = MockRestServiceServer.bindTo(builder).build()
            server
                .expect(requestTo("https://sefin.producaorestrita.nfse.gov.br/SefinNacional/nfse/1"))
                .andRespond(withSuccess())
            builder
                .build()
                .get()
                .uri("/nfse/1")
                .retrieve()
                .toBodilessEntity()
            server.verify()
        }
    }

    @Configuration(proxyBeanMethods = false)
    class CustomizerConfig {
        @Bean
        fun customHeader() = NfseRestClientCustomizer { it.defaultHeader("X-Custom", "yes") }
    }
}
