package io.github.rodrigoma.nfse.client

/**
 * JSON bodies of the Sefin Nacional API. Names follow the Swagger of the restricted-production environment; the
 * mapper matches them case-insensitively and ignores unknown properties, so small variations still bind.
 */
internal object ApiPayloads {
    const val DPS_FIELD = "dpsXmlGZipB64"
    const val EVENT_REQUEST_FIELD = "pedidoRegistroEventoXmlGZipB64"

    data class NfseResponse(
        val tipoAmbiente: Int? = null,
        val versaoAplicativo: String? = null,
        val dataHoraProcessamento: String? = null,
        val idDps: String? = null,
        val chaveAcesso: String? = null,
        val nfseXmlGZipB64: String? = null,
    )

    data class DpsResponse(
        val chaveAcesso: String? = null,
    )

    data class EventResponse(
        val dataHoraProcessamento: String? = null,
        val idEvento: String? = null,
        val tipoEvento: String? = null,
        val numSeqEvento: Int? = null,
        val eventoXmlGZipB64: String? = null,
    )

    /** Either a list under `eventos` or a single event object. */
    data class EventListResponse(
        val eventos: List<EventResponse>? = null,
        val idEvento: String? = null,
        val tipoEvento: String? = null,
        val numSeqEvento: Int? = null,
        val eventoXmlGZipB64: String? = null,
    ) {
        fun items(): List<EventResponse> =
            eventos ?: listOfNotNull(
                eventoXmlGZipB64?.let {
                    EventResponse(
                        idEvento = idEvento,
                        tipoEvento = tipoEvento,
                        numSeqEvento = numSeqEvento,
                        eventoXmlGZipB64 = it,
                    )
                },
            )
    }
}
