package io.github.rodrigoma.nfse.http

import io.github.rodrigoma.nfse.exception.NfseError
import io.github.rodrigoma.nfse.exception.NfseException
import org.springframework.http.client.ClientHttpResponse
import tools.jackson.core.JacksonException
import tools.jackson.databind.json.JsonMapper
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME

/**
 * Maps 4xx/5xx responses of the Sefin Nacional / ADN services to [NfseException]s.
 *
 * Error bodies come in two shapes (`sefin-nacional.openapi.json`): `NFSePostResponseErro` with `erros[]` on
 * `POST /nfse`, and `ResponseErro` with a single `erro` object everywhere else; both use `MensagemProcessamento`
 * (`codigo`, `descricao`, `complemento`). The ADN uses PascalCase (`Erros[]`, `Codigo`…), which the
 * case-insensitive mapper also binds.
 */
class NfseErrorHandler(
    private val objectMapper: JsonMapper,
) {
    internal data class ErrorEntry(
        val codigo: String? = null,
        val descricao: String? = null,
        val complemento: String? = null,
        val mensagem: String? = null,
    )

    internal data class ErrorBody(
        val erros: List<ErrorEntry>? = null,
        val erro: ErrorEntry? = null,
        val mensagens: List<ErrorEntry>? = null,
        val mensagem: String? = null,
    )

    fun handle(response: ClientHttpResponse) {
        val status = response.statusCode.value()
        val body = runCatching { response.body.readBytes() }.getOrDefault(ByteArray(0))
        throw when (status) {
            HTTP_UNAUTHORIZED, HTTP_FORBIDDEN -> NfseException.Unauthorized(status, unauthorizedMessage(status, body))
            HTTP_NOT_FOUND -> NfseException.NotFound(notFoundMessage(body))
            HTTP_TOO_MANY_REQUESTS ->
                NfseException.Unavailable(
                    "NFS-e service is rate limiting (HTTP 429)",
                    status,
                    retryAfter = retryAfter(response),
                )
            in HTTP_CLIENT_ERROR_RANGE -> NfseException.Rejected(parseErrors(body, status), status)
            else -> NfseException.Unavailable("NFS-e service returned HTTP $status", status)
        }
    }

    /** Parses the error list; a body that is not the documented JSON becomes a single error with the raw text. */
    fun parseErrors(
        body: ByteArray,
        status: Int,
    ): List<NfseError> {
        val parsed = parse(body)
        val entries = parsed?.erros ?: parsed?.erro?.let { listOf(it) } ?: parsed?.mensagens
        return entries
            ?.takeIf { it.isNotEmpty() }
            ?.map {
                NfseError(it.codigo ?: "HTTP_$status", it.descricao ?: it.mensagem ?: "no description", it.complemento)
            }
            ?: parsed?.mensagem?.let { listOf(NfseError("HTTP_$status", it)) }
            ?: listOf(NfseError("HTTP_$status", body.decodeToString().ifBlank { "empty response body" }))
    }

    private fun parse(body: ByteArray): ErrorBody? =
        try {
            objectMapper.readValue(body, ErrorBody::class.java)
        } catch (_: JacksonException) {
            null
        }

    private fun notFoundMessage(body: ByteArray): String =
        parse(body)?.let { it.erro?.descricao ?: it.mensagem } ?: "Resource not found"

    private fun unauthorizedMessage(
        status: Int,
        body: ByteArray,
    ): String =
        "The certificate was not accepted by the NFS-e service (HTTP $status)" +
            (parse(body)?.erro?.descricao ?: body.decodeToString())
                .takeIf { it.isNotBlank() }
                ?.let { ": $it" }
                .orEmpty()

    /** `Retry-After` may be a delay in seconds or an HTTP-date; anything else yields `null`. */
    private fun retryAfter(response: ClientHttpResponse): Duration? {
        val header = response.headers.getFirst("Retry-After")?.trim()
        return when {
            header == null -> null
            header.toLongOrNull() != null -> Duration.ofSeconds(header.toLong())
            else ->
                runCatching { ZonedDateTime.parse(header, RFC_1123_DATE_TIME).toInstant() }
                    .map { Duration.between(Instant.now(), it).takeIf { d -> !d.isNegative } ?: Duration.ZERO }
                    .getOrNull()
        }
    }

    companion object {
        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_FORBIDDEN = 403
        private const val HTTP_NOT_FOUND = 404
        private const val HTTP_TOO_MANY_REQUESTS = 429
        private val HTTP_CLIENT_ERROR_RANGE = 400..428
    }
}
