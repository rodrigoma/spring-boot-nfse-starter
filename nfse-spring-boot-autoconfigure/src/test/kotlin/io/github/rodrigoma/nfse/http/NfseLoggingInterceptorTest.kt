package io.github.rodrigoma.nfse.http

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.slf4j.LoggerFactory
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.mock.http.client.MockClientHttpRequest
import org.springframework.mock.http.client.MockClientHttpResponse
import org.springframework.web.client.RestClient
import java.net.URI

@ExtendWith(OutputCaptureExtension::class)
class NfseLoggingInterceptorTest {
    private val logger = LoggerFactory.getLogger(NfseLoggingInterceptor::class.java) as Logger
    private var previousLevel: Level? = null

    @BeforeEach
    fun setUp() {
        previousLevel = logger.level
        logger.level = Level.DEBUG
    }

    @AfterEach
    fun tearDown() {
        logger.level = previousLevel
    }

    private fun client(status: HttpStatus = HttpStatus.OK): RestClient =
        RestClient
            .builder()
            .requestFactory { uri: URI, method: HttpMethod ->
                MockClientHttpRequest(method, uri).also {
                    val body = """{"nfseXmlGZipB64":"abc","chaveAcesso":"1"}""".toByteArray()
                    it.setResponse(MockClientHttpResponse(body, status))
                }
            }.requestInterceptor(NfseLoggingInterceptor())
            .build()

    @Test
    fun `logs method, uri, status and masked bodies, and keeps the response readable`(output: CapturedOutput) {
        val body =
            client()
                .post()
                .uri("https://stub/nfse")
                .body("""{"dpsXmlGZipB64":"H4sI"}""")
                .retrieve()
                .body(String::class.java)

        assertThat(body).contains("\"chaveAcesso\":\"1\"")
        assertThat(output).contains("--> POST https://stub/nfse").contains("<4 chars gzip+base64>")
        assertThat(output).contains("<-- 200 https://stub/nfse").contains("<3 chars gzip+base64>")
        assertThat(output).doesNotContain("H4sI")
    }

    @Test
    fun `does nothing when debug is off`(output: CapturedOutput) {
        logger.level = Level.INFO
        client()
            .get()
            .uri("https://stub/nfse/1")
            .retrieve()
            .toBodilessEntity()
        assertThat(output).doesNotContain("stub/nfse/1")
    }
}
