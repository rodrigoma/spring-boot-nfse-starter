package io.github.rodrigoma.nfse.client

import io.github.rodrigoma.nfse.autoconfigure.NfseProperties
import io.github.rodrigoma.nfse.certificate.NfseCertificate
import io.github.rodrigoma.nfse.exception.NfseError
import io.github.rodrigoma.nfse.exception.NfseException
import io.github.rodrigoma.nfse.model.dps.Dps
import io.github.rodrigoma.nfse.model.dps.DpsId
import io.github.rodrigoma.nfse.model.dps.FederalId
import io.github.rodrigoma.nfse.model.event.CancellationReason
import io.github.rodrigoma.nfse.model.event.NfseEvent
import io.github.rodrigoma.nfse.model.event.NfseEventType
import io.github.rodrigoma.nfse.model.request.DpsRequest
import io.github.rodrigoma.nfse.model.response.DistributionBatch
import io.github.rodrigoma.nfse.model.response.Nfse
import io.github.rodrigoma.nfse.model.response.NfseResult
import io.github.rodrigoma.nfse.xml.CancellationRequest
import io.github.rodrigoma.nfse.xml.DpsXmlBuilder
import io.github.rodrigoma.nfse.xml.EventXmlBuilder
import io.github.rodrigoma.nfse.xml.GzipBase64
import io.github.rodrigoma.nfse.xml.NfseXmlParser
import io.github.rodrigoma.nfse.xml.XmlSigner
import io.github.rodrigoma.nfse.xml.XmlSupport
import org.springframework.http.MediaType
import org.springframework.web.client.RestClient
import org.springframework.web.client.body
import java.net.URI
import java.time.Clock
import java.time.OffsetDateTime

/**
 * [NfseClient] over Spring's `RestClient`. The client is configured with the Sefin Nacional base URL, the mTLS
 * request factory and [io.github.rodrigoma.nfse.http.NfseErrorHandler]; ADN calls use absolute URLs.
 */
@Suppress("LongParameterList")
class DefaultNfseClient(
    private val restClient: RestClient,
    private val properties: NfseProperties,
    private val certificate: NfseCertificate,
    private val dpsXmlBuilder: DpsXmlBuilder = DpsXmlBuilder(),
    private val eventXmlBuilder: EventXmlBuilder = EventXmlBuilder(),
    private val clock: Clock = Clock.systemDefaultZone(),
    private val danfseRenderer: DanfsePdfRenderer? = null,
) : NfseClient {
    private val assembler = DpsAssembler(properties, clock)
    private val signer: XmlSigner = certificate.signer()
    private val adn = AdnClient(restClient, properties.resolvedAdnBaseUrl())

    override val municipalParameters: MunicipalParametersClient =
        DefaultMunicipalParametersClient(restClient, properties.resolvedMunicipalParametersBaseUrl())

    override fun emit(request: DpsRequest): NfseResult = emit(assembler.assemble(request))

    override fun emit(dps: Dps): NfseResult {
        certificate.requireUsable()
        DpsPreflight.check(dps)
        val document = dpsXmlBuilder.build(dps)
        signer.sign(document, DpsXmlBuilder.infDps(document), document.documentElement)
        val dpsXml = XmlSupport.serialize(document)
        val response =
            nfseCall {
                restClient
                    .post()
                    .uri(NfseApiPaths.NFSE)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(mapOf(ApiPayloads.DPS_FIELD to GzipBase64.encode(dpsXml)))
                    .retrieve()
                    .body<ApiPayloads.NfseResponse>()
            } ?: throw NfseException.Unavailable("Empty response from POST ${NfseApiPaths.NFSE}")
        val nfse = NfseXmlParser.parseNfse(decode(response.nfseXmlGZipB64, "nfseXmlGZipB64"))
        return NfseResult(
            accessKey = response.chaveAcesso ?: nfse.accessKey,
            nfseNumber = nfse.number,
            processedAt = XmlSupport.parseDateTime(response.dataHoraProcessamento) ?: nfse.processedAt,
            dpsId = response.idDps ?: dps.id.value,
            nfse = nfse,
            dpsXml = dpsXml,
            alerts =
                response.alertas.orEmpty().map {
                    NfseError(
                        it.codigo ?: "ALERTA",
                        it.descricao ?: "",
                        it.complemento,
                    )
                },
        )
    }

    override fun get(accessKey: String): Nfse {
        val response =
            nfseCall {
                restClient
                    .get()
                    .uri(NfseApiPaths.NFSE_BY_KEY, accessKey)
                    .retrieve()
                    .body<ApiPayloads.NfseResponse>()
            } ?: throw NfseException.Unavailable("Empty response from GET ${NfseApiPaths.NFSE_BY_KEY}")
        return NfseXmlParser.parseNfse(decode(response.nfseXmlGZipB64, "nfseXmlGZipB64"))
    }

    /** The Sefin expects the full identifier, `DPS` literal included (`docs/specs/sefin-nacional.openapi.json`). */
    override fun accessKeyOf(dpsId: DpsId): String? =
        try {
            val response =
                nfseCall {
                    restClient
                        .get()
                        .uri(NfseApiPaths.DPS_BY_ID, dpsId.value)
                        .retrieve()
                        .body<ApiPayloads.DpsResponse>()
                }
            response?.chaveAcesso
                ?: throw NfseException.Unavailable("Response of GET ${NfseApiPaths.DPS_BY_ID} without chaveAcesso")
        } catch (_: NfseException.NotFound) {
            null
        }

    override fun exists(dpsId: DpsId): Boolean =
        try {
            nfseCall {
                restClient
                    .head()
                    .uri(NfseApiPaths.DPS_BY_ID, dpsId.value)
                    .retrieve()
                    .toBodilessEntity()
            }
            true
        } catch (_: NfseException.NotFound) {
            false
        }

    override fun cancel(
        accessKey: String,
        reason: CancellationReason,
        justification: String,
    ): NfseEvent {
        certificate.requireUsable()
        val request =
            CancellationRequest(
                environment = properties.environment,
                applicationVersion = properties.resolvedApplicationVersion(),
                eventAt = OffsetDateTime.now(clock),
                author = properties.emitterFederalId(),
                accessKey = accessKey,
                reason = reason,
                justification = justification,
            )
        val document = eventXmlBuilder.buildCancellation(request)
        signer.sign(document, EventXmlBuilder.infPedReg(document), document.documentElement)
        val eventXml = XmlSupport.serialize(document)
        val response =
            nfseCall {
                restClient
                    .post()
                    .uri(NfseApiPaths.EVENTS, accessKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(mapOf(ApiPayloads.EVENT_REQUEST_FIELD to GzipBase64.encode(eventXml)))
                    .retrieve()
                    .body<ApiPayloads.EventResponse>()
            } ?: throw NfseException.Unavailable("Empty response from POST ${NfseApiPaths.EVENTS}")
        return NfseXmlParser.parseEvent(decode(response.eventoXmlGZipB64, "eventoXmlGZipB64"))
    }

    override fun event(
        accessKey: String,
        type: NfseEventType,
        sequence: Int,
    ): NfseEvent {
        val response =
            nfseCall {
                restClient
                    .get()
                    .uri(NfseApiPaths.EVENT, accessKey, type.code, sequence)
                    .retrieve()
                    .body<ApiPayloads.EventResponse>()
            } ?: throw NfseException.Unavailable("Empty response from GET ${NfseApiPaths.EVENT}")
        return NfseXmlParser.parseEvent(decode(response.eventoXmlGZipB64, "eventoXmlGZipB64"))
    }

    override fun events(accessKey: String): List<NfseEvent> = adn.events(accessKey)

    override fun distribution(
        nsu: Long,
        cnpj: String?,
    ): DistributionBatch = adn.distribution(nsu, cnpj?.let { FederalId.Cnpj(it).value })

    override fun danfse(accessKey: String): ByteArray =
        danfseRenderer?.render(get(accessKey), events(accessKey)) ?: fetchDanfse(accessKey)

    private fun fetchDanfse(accessKey: String): ByteArray =
        nfseCall {
            restClient
                .get()
                .uri(URI.create("${properties.resolvedDanfseBaseUrl().trimEnd('/')}/$accessKey"))
                .accept(MediaType.APPLICATION_PDF)
                .retrieve()
                .body<ByteArray>()
        } ?: throw NfseException.Unavailable("Empty DANFSE response")

    private fun decode(
        payload: String?,
        field: String,
    ): String = GzipBase64.decode(payload ?: throw NfseException.Unavailable("Response without $field"))
}
