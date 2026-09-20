package io.github.rodrigoma.nfse.exception

/**
 * One error entry returned by the Sefin Nacional when it rejects a DPS or an event request, or produced by the
 * local XSD validation before anything is sent.
 *
 * @property code Error code (`E0010`, `E1235`, …) as listed in Anexo I / Anexo II of the official manual.
 * @property description Human-readable message.
 * @property detail Optional complement sent by the API (`complemento`).
 */
data class NfseError(
    val code: String,
    val description: String,
    val detail: String? = null,
) {
    override fun toString(): String = "$code: $description" + (detail?.let { " ($it)" } ?: "")
}

sealed class NfseException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause) {
    /**
     * The Sefin Nacional refused the DPS or the event request (HTTP 400/422). The request is wrong — fix it before
     * retrying; [errors] carries the codes and messages exactly as returned.
     */
    class Rejected(
        val errors: List<NfseError>,
        val httpStatus: Int,
    ) : NfseException("NFS-e request rejected (HTTP $httpStatus): ${errors.joinToString("; ")}")

    /** The DPS or event XML did not pass the embedded XSD — nothing was sent. */
    class Validation(
        val errors: List<NfseError>,
    ) : NfseException("XML failed schema validation: ${errors.joinToString("; ")}")

    /** The certificate could not be loaded or does not meet the ICP-Brasil rules. Raised at startup, never per note. */
    class Certificate(
        message: String,
        cause: Throwable? = null,
    ) : NfseException(message, cause)

    /** Network failure, timeout, HTTP 5xx or 429 — the Sefin Nacional or the DANFSE service is not reachable. */
    class Unavailable(
        message: String,
        val statusCode: Int? = null,
        cause: Throwable? = null,
    ) : NfseException(message, cause)

    /** HTTP 401/403 — the connection certificate was not accepted for this call. */
    class Unauthorized(
        val httpStatus: Int,
        message: String,
    ) : NfseException(message)

    /** HTTP 404 — the NFS-e, DPS or event does not exist (or is not visible to this certificate). */
    class NotFound(
        message: String = "Resource not found",
    ) : NfseException(message)
}
