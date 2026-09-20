package io.github.rodrigoma.nfse.client

import io.github.rodrigoma.nfse.exception.NfseError
import io.github.rodrigoma.nfse.exception.NfseException
import io.github.rodrigoma.nfse.model.event.NfseEvent
import io.github.rodrigoma.nfse.model.response.DistributedDocument
import io.github.rodrigoma.nfse.model.response.DistributedDocumentType
import io.github.rodrigoma.nfse.model.response.DistributionBatch
import io.github.rodrigoma.nfse.model.response.DistributionStatus
import io.github.rodrigoma.nfse.xml.GzipBase64
import io.github.rodrigoma.nfse.xml.NfseXmlParser
import io.github.rodrigoma.nfse.xml.XmlSupport
import org.springframework.http.HttpStatus
import org.springframework.web.client.RestClient
import org.springframework.web.util.UriComponentsBuilder
import java.net.URI

/**
 * ADN Contribuintes calls. The ADN answers **400 (rejection) and 404 (nothing found) with the full
 * `LoteDistribuicaoNSUResponse` body** — the real outcome is `StatusProcessamento`, so those statuses are read,
 * not thrown; everything else goes through the normal error mapping.
 */
internal class AdnClient(
    private val restClient: RestClient,
    private val baseUrl: String,
) {
    fun events(accessKey: String): List<NfseEvent> {
        val response = fetch(uri(NfseApiPaths.ADN_EVENTS.replace("{chaveAcesso}", accessKey)))
        return response.loteDFe
            .orEmpty()
            .mapNotNull { it.arquivoXml }
            .map { NfseXmlParser.parseEvent(GzipBase64.decode(it)) }
    }

    fun distribution(
        nsu: Long,
        cnpj: String?,
    ): DistributionBatch {
        val builder =
            UriComponentsBuilder
                .fromUriString(baseUrl.trimEnd('/') + NfseApiPaths.ADN_DISTRIBUTION.replace("{nsu}", nsu.toString()))
                .queryParam("lote", true)
        cnpj?.let { builder.queryParam("cnpjConsulta", it) }
        val response = fetch(builder.build().toUri())
        val documents =
            response.loteDFe.orEmpty().map {
                DistributedDocument(
                    nsu = it.nsu ?: 0,
                    accessKey = it.chaveAcesso,
                    type = DistributedDocumentType.fromWire(it.tipoDocumento),
                    eventType = it.tipoEvento,
                    xml = it.arquivoXml?.let(GzipBase64::decode).orEmpty(),
                    generatedAt = XmlSupport.parseDateTime(it.dataHoraGeracao),
                )
            }
        return DistributionBatch(
            status = DistributionStatus.fromWire(response.statusProcessamento),
            documents = documents,
            alerts = response.alertas.orEmpty().map(::toError),
            nextNsu = (documents.maxOfOrNull { it.nsu } ?: (nsu - 1)) + 1,
        )
    }

    private fun uri(path: String): URI = URI.create(baseUrl.trimEnd('/') + path)

    private fun fetch(uri: URI): ApiPayloads.DistributionResponse =
        nfseCall {
            restClient.get().uri(uri).exchange({ _, response ->
                val status = response.statusCode.value()
                when {
                    status == HttpStatus.OK.value() || status in MEANINGFUL_ERRORS ->
                        response.bodyTo(ApiPayloads.DistributionResponse::class.java)
                            ?: throw NfseException.Unavailable("Empty response from ADN $uri", status)
                    else -> throw NfseException.Unavailable("ADN returned HTTP $status for $uri", status)
                }
            }, true)
        }.also { response ->
            if (DistributionStatus.fromWire(response.statusProcessamento) == DistributionStatus.REJECTED) {
                val errors =
                    response.erros.orEmpty().map(::toError).ifEmpty {
                        listOf(NfseError("REJEICAO", "ADN rejected the request"))
                    }
                throw NfseException.Rejected(errors, HttpStatus.BAD_REQUEST.value())
            }
        }

    private fun toError(message: ApiPayloads.Message): NfseError =
        NfseError(message.codigo ?: "ADN", message.descricao ?: "no description", message.complemento)

    private companion object {
        val MEANINGFUL_ERRORS = setOf(HttpStatus.BAD_REQUEST.value(), HttpStatus.NOT_FOUND.value())
    }
}
