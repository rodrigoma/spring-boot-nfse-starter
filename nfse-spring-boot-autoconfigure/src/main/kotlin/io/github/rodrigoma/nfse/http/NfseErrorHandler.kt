package io.github.rodrigoma.nfse.http

import io.github.rodrigoma.nfse.exception.NfseError
import io.github.rodrigoma.nfse.exception.NfseException
import org.springframework.http.client.ClientHttpResponse
import tools.jackson.core.JacksonException
import tools.jackson.databind.json.JsonMapper

/** Maps 4xx/5xx responses of the Sefin Nacional / DANFSE services to [NfseException]s. */
class NfseErrorHandler(
    private val objectMapper: JsonMapper,
) {
    /** `{"erros":[{"codigo","descricao","complemento"}]}` — property names are matched case-insensitively. */
    internal data class ErrorEntry(
        val codigo: String? = null,
        val descricao: String? = null,
        val complemento: String? = null,
        val mensagem: String? = null,
    )

    internal data class ErrorBody(
        val erros: List<ErrorEntry>? = null,
        val mensagens: List<ErrorEntry>? = null,
    )

    fun handle(response: ClientHttpResponse) {
        val status = response.statusCode.value()
        val body = runCatching { response.body.readBytes() }.getOrDefault(ByteArray(0))
        throw when (status) {
            HTTP_UNAUTHORIZED, HTTP_FORBIDDEN -> NfseException.Unauthorized(status, unauthorizedMessage(status, body))
            HTTP_NOT_FOUND -> NfseException.NotFound()
            in HTTP_CLIENT_ERROR_RANGE -> NfseException.Rejected(parseErrors(body, status), status)
            else -> NfseException.Unavailable("NFS-e service returned HTTP $status", status)
        }
    }

    /** Parses the error list; a body that is not the documented JSON becomes a single error with the raw text. */
    fun parseErrors(
        body: ByteArray,
        status: Int,
    ): List<NfseError> {
        val parsed =
            try {
                objectMapper.readValue(body, ErrorBody::class.java)
            } catch (_: JacksonException) {
                null
            }
        val entries = parsed?.erros ?: parsed?.mensagens
        return entries
            ?.takeIf { it.isNotEmpty() }
            ?.map {
                NfseError(it.codigo ?: "HTTP_$status", it.descricao ?: it.mensagem ?: "no description", it.complemento)
            }
            ?: listOf(NfseError("HTTP_$status", body.decodeToString().ifBlank { "empty response body" }))
    }

    private fun unauthorizedMessage(
        status: Int,
        body: ByteArray,
    ): String =
        "The certificate was not accepted by the NFS-e service (HTTP $status)" +
            body
                .decodeToString()
                .takeIf { it.isNotBlank() }
                ?.let { ": $it" }
                .orEmpty()

    companion object {
        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_FORBIDDEN = 403
        private const val HTTP_NOT_FOUND = 404
        private val HTTP_CLIENT_ERROR_RANGE = 400..428
    }
}
