package io.github.rodrigoma.nfse.autoconfigure

import io.github.rodrigoma.nfse.exception.NfseException
import io.github.rodrigoma.nfse.support.TestCertificates
import io.github.rodrigoma.nfse.support.TestDps
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.health.contributor.HealthIndicator
import org.springframework.boot.health.contributor.Status
import org.springframework.boot.test.context.FilteredClassLoader
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.mock.http.client.MockClientHttpRequest
import org.springframework.mock.http.client.MockClientHttpResponse
import org.springframework.web.client.RestClient
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NfseHealthIndicatorAutoConfigurationTest {
    private lateinit var pfx: Path

    @BeforeAll
    fun certificate() {
        pfx = Files.createTempFile("emitter", ".pfx").also { Files.write(it, TestCertificates.emitter().pkcs12()) }
    }

    private val contextRunner by lazy {
        ApplicationContextRunner()
            .withConfiguration(
                AutoConfigurations.of(
                    NfseAutoConfiguration::class.java,
                    NfseHealthIndicatorAutoConfiguration::class.java,
                ),
            ).withPropertyValues(
                "nfse.certificate.pfx-path=$pfx",
                "nfse.certificate.password=${TestCertificates.PASSWORD}",
                "nfse.emitter.cnpj=${TestDps.CNPJ}",
                "nfse.emitter.municipality-ibge=${TestDps.MUNICIPALITY}",
            )
    }

    @Test
    fun `health indicator is created only when enabled and actuator is present`() {
        contextRunner.run { context -> assertThat(context).doesNotHaveBean(HealthIndicator::class.java) }
        contextRunner
            .withPropertyValues("nfse.health-indicator-enabled=true")
            .withClassLoader(FilteredClassLoader(HealthIndicator::class.java))
            .run { context -> assertThat(context).doesNotHaveBean("nfseHealthIndicator") }
        contextRunner
            .withPropertyValues("nfse.health-indicator-enabled=true")
            .run { context -> assertThat(context).hasSingleBean(HealthIndicator::class.java) }
    }

    private fun indicator(
        status: HttpStatus,
        failure: Throwable? = null,
    ): NfseHealthIndicator {
        val restClient =
            RestClient
                .builder()
                .baseUrl("https://stub")
                .requestFactory { uri: URI, method: HttpMethod ->
                    MockClientHttpRequest(method, uri).also {
                        it.setResponse(MockClientHttpResponse(ByteArray(0), status))
                    }
                }.defaultStatusHandler({ it.isError }) { _, _ ->
                    throw failure ?: NfseException.Unavailable("down", status.value())
                }.build()
        return NfseHealthIndicator(restClient, TestDps.MUNICIPALITY)
    }

    @Test
    fun `reports up, out of service on unauthorized and down otherwise`() {
        assertThat(indicator(HttpStatus.OK).health().status).isEqualTo(Status.UP)
        val unauthorized = indicator(HttpStatus.FORBIDDEN, NfseException.Unauthorized(403, "no"))
        assertThat(unauthorized.health().status).isEqualTo(Status.OUT_OF_SERVICE)
        assertThat(indicator(HttpStatus.BAD_GATEWAY).health().status).isEqualTo(Status.DOWN)
    }
}
