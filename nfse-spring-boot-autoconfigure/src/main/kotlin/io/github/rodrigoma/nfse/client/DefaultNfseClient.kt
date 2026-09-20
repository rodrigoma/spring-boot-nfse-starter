package io.github.rodrigoma.nfse.client

import io.github.rodrigoma.nfse.autoconfigure.NfseProperties
import io.github.rodrigoma.nfse.certificate.NfseCertificate
import io.github.rodrigoma.nfse.exception.NfseException
import io.github.rodrigoma.nfse.model.dps.Dps
import io.github.rodrigoma.nfse.model.dps.DpsId
import io.github.rodrigoma.nfse.model.event.CancellationReason
import io.github.rodrigoma.nfse.model.event.NfseEvent
import io.github.rodrigoma.nfse.model.request.DpsRequest
import io.github.rodrigoma.nfse.model.response.MunicipalParameters
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
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.client.body
import java.net.URI
import java.time.Clock
import java.time.OffsetDateTime

/**
 * [NfseClient] over Spring's `RestClient`. The client is expected to be configured with the Sefin base URL, the mTLS
 * request factory and [io.github.rodrigoma.nfse.http.NfseErrorHandler]; DANFSE calls use an absolute URL.
 */
@Suppress("LongParameterList")
class DefaultNfseClient(
    private val restClient: RestClient,
    private val properties: NfseProperties,
    private val certificate: NfseCertificate,
    private val dpsXmlBuilder: DpsXmlBuilder = DpsXmlBuilder(),
    private val eventXmlBuilder: EventXmlBuilder = EventXmlBuilder(),
    private val clock: Clock = Clock.systemDefaultZone(),
) : NfseClient {
    private val assembler = DpsAssembler(properties, clock)
    private val signer: XmlSigner = certificate.signer()

    override fun emit(request: DpsRequest): NfseResult = emit(assembler.assemble(request))

    override fun emit(dps: Dps): NfseResult {
        val document = dpsXmlBuilder.build(dps)
        signer.sign(document, DpsXmlBuilder.infDps(document), document.documentElement)
        val dpsXml = XmlSupport.serialize(document)
        val response =
            execute {
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
        )
    }

    override fun get(accessKey: String): Nfse {
        val response =
            execute {
                restClient
                    .get()
                    .uri(NfseApiPaths.NFSE_BY_KEY, accessKey)
                    .retrieve()
                    .body<ApiPayloads.NfseResponse>()
            }
                ?: throw NfseException.Unavailable("Empty response from GET ${NfseApiPaths.NFSE_BY_KEY}")
        return NfseXmlParser.parseNfse(decode(response.nfseXmlGZipB64, "nfseXmlGZipB64"))
    }

    override fun accessKeyOf(dpsId: DpsId): String? =
        try {
            val response =
                execute {
                    restClient
                        .get()
                        .uri(NfseApiPaths.DPS_BY_ID, dpsId.digits)
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
            execute {
                restClient
                    .head()
                    .uri(NfseApiPaths.DPS_BY_ID, dpsId.digits)
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
            execute {
                restClient
                    .post()
                    .uri(NfseApiPaths.EVENTS, accessKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(mapOf(ApiPayloads.EVENT_REQUEST_FIELD to GzipBase64.encode(eventXml)))
                    .retrieve()
                    .body<ApiPayloads.EventResponse>()
            } ?: throw NfseException.Unavailable("Empty response from POST ${NfseApiPaths.EVENTS}")
        return toEvent(response, accessKey)
    }

    override fun events(accessKey: String): List<NfseEvent> =
        execute {
            restClient
                .get()
                .uri(NfseApiPaths.EVENTS, accessKey)
                .retrieve()
                .body<ApiPayloads.EventListResponse>()
        }?.items()
            ?.map { toEvent(it, accessKey) }
            .orEmpty()

    override fun danfse(accessKey: String): ByteArray =
        execute {
            restClient
                .get()
                .uri(URI.create("${properties.resolvedDanfseBaseUrl().trimEnd('/')}/$accessKey"))
                .accept(MediaType.APPLICATION_PDF)
                .retrieve()
                .body<ByteArray>()
        } ?: throw NfseException.Unavailable("Empty DANFSE response")

    override fun municipalAgreement(municipalityIbge: Int): MunicipalParameters =
        MunicipalParameters(municipalityIbge, null, fetchParameters(NfseApiPaths.MUNICIPAL_AGREEMENT, municipalityIbge))

    override fun municipalParameters(
        municipalityIbge: Int,
        serviceCode: String,
    ): MunicipalParameters =
        MunicipalParameters(
            municipalityIbge,
            serviceCode,
            fetchParameters(NfseApiPaths.MUNICIPAL_SERVICE_PARAMETERS, municipalityIbge, serviceCode),
        )

    private fun fetchParameters(
        path: String,
        vararg variables: Any,
    ): Map<String, Any?> =
        execute {
            restClient
                .get()
                .uri(path, *variables)
                .retrieve()
                .body<Map<String, Any?>>()
        }.orEmpty()

    private fun toEvent(
        response: ApiPayloads.EventResponse,
        accessKey: String,
    ): NfseEvent =
        response.eventoXmlGZipB64?.let { NfseXmlParser.parseEvent(GzipBase64.decode(it)) }
            ?: NfseEvent(
                id = response.idEvento.orEmpty(),
                typeCode = response.tipoEvento.orEmpty().removePrefix("e"),
                sequence = response.numSeqEvento ?: 1,
                processedAt = XmlSupport.parseDateTime(response.dataHoraProcessamento),
                accessKey = accessKey,
                xml = "",
            )

    private fun decode(
        payload: String?,
        field: String,
    ): String = GzipBase64.decode(payload ?: throw NfseException.Unavailable("Response without $field"))

    /** Runs a call, turning transport failures into [NfseException.Unavailable]; [NfseException]s pass through. */
    private fun <T> execute(call: () -> T): T =
        try {
            call()
        } catch (e: ResourceAccessException) {
            throw NfseException.Unavailable("Cannot reach the NFS-e service: ${e.message}", cause = e)
        } catch (e: RestClientException) {
            throw (e.cause as? NfseException) ?: NfseException.Unavailable("NFS-e call failed: ${e.message}", cause = e)
        }
}

/** Paths under the Sefin Nacional base URL. */
object NfseApiPaths {
    const val NFSE = "/nfse"
    const val NFSE_BY_KEY = "/nfse/{chaveAcesso}"
    const val DPS_BY_ID = "/dps/{id}"
    const val EVENTS = "/nfse/{chaveAcesso}/eventos"
    const val MUNICIPAL_AGREEMENT = "/parametros_municipais/{codigoMunicipio}/convenio"
    const val MUNICIPAL_SERVICE_PARAMETERS = "/parametros_municipais/{codigoMunicipio}/{codigoServico}"
}
