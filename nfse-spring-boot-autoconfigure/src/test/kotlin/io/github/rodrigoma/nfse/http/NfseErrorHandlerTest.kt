package io.github.rodrigoma.nfse.http

import io.github.rodrigoma.nfse.exception.NfseError
import io.github.rodrigoma.nfse.exception.NfseException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.mock.http.client.MockClientHttpResponse
import tools.jackson.databind.MapperFeature
import tools.jackson.module.kotlin.jacksonMapperBuilder
import java.time.Duration

class NfseErrorHandlerTest {
    private val handler =
        NfseErrorHandler(jacksonMapperBuilder().enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES).build())

    private fun response(
        status: HttpStatus,
        body: String = "",
    ) = MockClientHttpResponse(body.toByteArray(), status)

    @Test
    fun `400 and 422 with an error list become Rejected`() {
        val body =
            """{"erros":[{"codigo":"E0010","descricao":"Série inválida","complemento":"faixa 1-49999"},""" +
                """{"Codigo":"E0015","Descricao":"Competência"}]}"""
        assertThatThrownBy { handler.handle(response(HttpStatus.BAD_REQUEST, body)) }
            .isInstanceOf(NfseException.Rejected::class.java)
            .satisfies({
                val rejected = it as NfseException.Rejected
                assertThat(rejected.httpStatus).isEqualTo(400)
                assertThat(rejected.errors).hasSize(2)
                assertThat(rejected.errors[0].code).isEqualTo("E0010")
                assertThat(rejected.errors[0].detail).isEqualTo("faixa 1-49999")
                assertThat(rejected.errors[1].description).isEqualTo("Competência")
                assertThat(rejected.message).contains("E0010: Série inválida (faixa 1-49999)")
            })
        assertThatThrownBy { handler.handle(response(HttpStatus.UNPROCESSABLE_CONTENT, body)) }
            .isInstanceOf(NfseException.Rejected::class.java)
    }

    @Test
    fun `a 4xx without the documented body still becomes Rejected with the raw text`() {
        assertThatThrownBy { handler.handle(response(HttpStatus.BAD_REQUEST, "Bad Request")) }
            .isInstanceOf(NfseException.Rejected::class.java)
            .satisfies({
                assertThat((it as NfseException.Rejected).errors).containsExactly(NfseError("HTTP_400", "Bad Request"))
            })
        assertThatThrownBy { handler.handle(response(HttpStatus.BAD_REQUEST, """{"erros":[]}""")) }
            .hasMessageContaining("""HTTP_400: {"erros":[]}""")
            .isInstanceOf(NfseException.Rejected::class.java)
        assertThatThrownBy { handler.handle(response(HttpStatus.UNPROCESSABLE_CONTENT)) }
            .hasMessageContaining("empty response body")
        val messages = """{"mensagens":[{"mensagem":"duplicada"}]}"""
        assertThatThrownBy { handler.handle(response(HttpStatus.CONFLICT, messages)) }
            .hasMessageContaining("HTTP_409: duplicada")
    }

    @Test
    fun `the single erro object of ResponseErro is parsed too`() {
        val body = """{"tipoAmbiente":2,"erro":{"codigo":"E0002","descricao":"Chave inválida","complemento":null}}"""
        assertThatThrownBy { handler.handle(response(HttpStatus.BAD_REQUEST, body)) }
            .isInstanceOf(NfseException.Rejected::class.java)
            .satisfies(
                {
                    assertThat(
                        (it as NfseException.Rejected).errors,
                    ).containsExactly(NfseError("E0002", "Chave inválida"))
                },
            )
        assertThatThrownBy { handler.handle(response(HttpStatus.NOT_FOUND, body)) }
            .isInstanceOf(NfseException.NotFound::class.java)
            .hasMessage("Chave inválida")
        assertThatThrownBy { handler.handle(response(HttpStatus.NOT_FOUND, """{"mensagem":"Nada aqui"}""")) }
            .hasMessage("Nada aqui")
        assertThatThrownBy {
            handler.handle(
                response(HttpStatus.BAD_REQUEST, """{"mensagem":"Competência inválida"}"""),
            )
        }.hasMessageContaining("HTTP_400: Competência inválida")
    }

    @Test
    fun `429 becomes Unavailable with Retry-After in seconds or as an HTTP date`() {
        val seconds = response(HttpStatus.TOO_MANY_REQUESTS).apply { headers.add("Retry-After", "30") }
        assertThatThrownBy { handler.handle(seconds) }
            .isInstanceOf(NfseException.Unavailable::class.java)
            .satisfies({ assertThat((it as NfseException.Unavailable).retryAfter).isEqualTo(Duration.ofSeconds(30)) })
        val past =
            response(
                HttpStatus.TOO_MANY_REQUESTS,
            ).apply { headers.add("Retry-After", "Wed, 21 Oct 2015 07:28:00 GMT") }
        assertThatThrownBy { handler.handle(past) }
            .satisfies({ assertThat((it as NfseException.Unavailable).retryAfter).isEqualTo(Duration.ZERO) })
        val garbage = response(HttpStatus.TOO_MANY_REQUESTS).apply { headers.add("Retry-After", "soon") }
        assertThatThrownBy { handler.handle(garbage) }
            .satisfies({ assertThat((it as NfseException.Unavailable).retryAfter).isNull() })
    }

    @Test
    fun `401 and 403 become Unauthorized`() {
        assertThatThrownBy { handler.handle(response(HttpStatus.UNAUTHORIZED)) }
            .isInstanceOf(NfseException.Unauthorized::class.java)
            .hasMessageContaining("401")
        assertThatThrownBy { handler.handle(response(HttpStatus.FORBIDDEN, "certificado inválido")) }
            .isInstanceOf(NfseException.Unauthorized::class.java)
            .hasMessageContaining("certificado inválido")
            .satisfies({ assertThat((it as NfseException.Unauthorized).httpStatus).isEqualTo(403) })
    }

    @Test
    fun `404 becomes NotFound and 429 or 5xx become Unavailable`() {
        assertThatThrownBy { handler.handle(response(HttpStatus.NOT_FOUND)) }
            .isInstanceOf(NfseException.NotFound::class.java)
        assertThatThrownBy { handler.handle(response(HttpStatus.TOO_MANY_REQUESTS)) }
            .isInstanceOf(NfseException.Unavailable::class.java)
            .satisfies({ assertThat((it as NfseException.Unavailable).statusCode).isEqualTo(429) })
            .satisfies({ assertThat((it as NfseException.Unavailable).retryAfter).isNull() })
        assertThatThrownBy { handler.handle(response(HttpStatus.BAD_GATEWAY)) }
            .isInstanceOf(NfseException.Unavailable::class.java)
            .hasMessageContaining("502")
    }
}
