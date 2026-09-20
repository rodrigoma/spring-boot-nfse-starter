package io.github.rodrigoma.nfse.client

/**
 * JSON bodies of the Sefin Nacional and ADN services, after the OpenAPI specs under `docs/specs`. The Sefin uses
 * camelCase and the ADN PascalCase; the mapper matches property names case-insensitively and ignores unknown ones.
 */
internal object ApiPayloads {
    const val DPS_FIELD = "dpsXmlGZipB64"
    const val EVENT_REQUEST_FIELD = "pedidoRegistroEventoXmlGZipB64"

    /** `MensagemProcessamento`. */
    data class Message(
        val codigo: String? = null,
        val descricao: String? = null,
        val complemento: String? = null,
    )

    /** `NFSePostResponseSucesso` / `NFSeGetResponseSucesso`. */
    data class NfseResponse(
        val tipoAmbiente: Int? = null,
        val versaoAplicativo: String? = null,
        val dataHoraProcessamento: String? = null,
        val idDps: String? = null,
        val chaveAcesso: String? = null,
        val nfseXmlGZipB64: String? = null,
        val alertas: List<Message>? = null,
    )

    /** `DpsGetResponse`. */
    data class DpsResponse(
        val chaveAcesso: String? = null,
    )

    /** `EventosPostResponseSucesso` — also the body of `GET …/eventos/{tipo}/{seq}`. */
    data class EventResponse(
        val dataHoraProcessamento: String? = null,
        val eventoXmlGZipB64: String? = null,
    )

    /** `DistribuicaoNSU` (ADN, PascalCase on the wire). */
    data class DistributionItem(
        val nsu: Long? = null,
        val chaveAcesso: String? = null,
        val tipoDocumento: String? = null,
        val tipoEvento: String? = null,
        val arquivoXml: String? = null,
        val dataHoraGeracao: String? = null,
    )

    /** `LoteDistribuicaoNSUResponse` (ADN) — returned with HTTP 200, and with 400/404 carrying the same body. */
    data class DistributionResponse(
        val statusProcessamento: String? = null,
        val loteDFe: List<DistributionItem>? = null,
        val alertas: List<Message>? = null,
        val erros: List<Message>? = null,
        val dataHoraProcessamento: String? = null,
    )

    /** Any ADN Parâmetros Municipais result: `mensagem` + one payload property. */
    data class MunicipalParametersResponse(
        val mensagem: String? = null,
    )
}
