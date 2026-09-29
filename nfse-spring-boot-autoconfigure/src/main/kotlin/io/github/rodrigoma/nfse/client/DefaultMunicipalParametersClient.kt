package io.github.rodrigoma.nfse.client

import io.github.rodrigoma.nfse.model.response.MunicipalParameters
import org.springframework.web.client.RestClient
import org.springframework.web.client.body
import java.net.URI
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** [MunicipalParametersClient] over the ADN Parâmetros Municipais service. */
internal class DefaultMunicipalParametersClient(
    private val restClient: RestClient,
    private val baseUrl: String,
) : MunicipalParametersClient {
    override fun agreement(municipalityIbge: Int): MunicipalParameters =
        fetch(municipalityIbge, null, null, NfseApiPaths.PARAM_AGREEMENT.fill(municipalityIbge))

    override fun rates(
        municipalityIbge: Int,
        serviceCode: String,
        competence: LocalDate,
    ): MunicipalParameters =
        fetch(
            municipalityIbge,
            serviceCode,
            competence,
            NfseApiPaths.PARAM_RATES.fill(municipalityIbge, serviceCode, competence),
        )

    override fun rateHistory(
        municipalityIbge: Int,
        serviceCode: String,
    ): MunicipalParameters =
        fetch(
            municipalityIbge,
            serviceCode,
            null,
            NfseApiPaths.PARAM_RATE_HISTORY.fill(municipalityIbge, serviceCode),
        )

    override fun benefit(
        municipalityIbge: Int,
        benefitNumber: String,
        competence: LocalDate,
    ): MunicipalParameters =
        fetch(
            municipalityIbge,
            null,
            competence,
            NfseApiPaths.PARAM_BENEFIT.fill(municipalityIbge, competence = competence, benefit = benefitNumber),
        )

    override fun specialRegimes(
        municipalityIbge: Int,
        serviceCode: String,
        competence: LocalDate,
    ): MunicipalParameters =
        fetch(
            municipalityIbge,
            serviceCode,
            competence,
            NfseApiPaths.PARAM_SPECIAL_REGIMES.fill(municipalityIbge, serviceCode, competence),
        )

    override fun withholdings(
        municipalityIbge: Int,
        competence: LocalDate,
    ): MunicipalParameters =
        fetch(
            municipalityIbge,
            null,
            competence,
            NfseApiPaths.PARAM_WITHHOLDINGS.fill(municipalityIbge, competence = competence),
        )

    /** Fills the placeholders of a `NfseApiPaths.PARAM_*` template; the leading slash is added by the caller. */
    private fun String.fill(
        municipalityIbge: Int,
        serviceCode: String? = null,
        competence: LocalDate? = null,
        benefit: String? = null,
    ): String =
        trimStart('/')
            .replace("{codigoMunicipio}", municipalityIbge.toString())
            .replace("{codigoServico}", serviceCode?.let { ServiceCodes.complete(it) }.orEmpty())
            .replace("{numeroBeneficio}", benefit.orEmpty())
            .replace("{competencia}", competence?.iso().orEmpty())

    private fun fetch(
        municipalityIbge: Int,
        serviceCode: String?,
        competence: LocalDate?,
        path: String,
    ): MunicipalParameters {
        val raw =
            nfseCall {
                restClient
                    .get()
                    .uri(URI.create("${baseUrl.trimEnd('/')}/$path"))
                    .retrieve()
                    .body<Map<String, Any?>>()
            }.orEmpty()
        return MunicipalParameters(
            municipalityIbge = municipalityIbge,
            serviceCode = serviceCode,
            competence = competence,
            message = raw.entries.firstOrNull { it.key.equals("mensagem", ignoreCase = true) }?.value as? String,
            raw = raw,
        )
    }

    private fun LocalDate.iso(): String = format(DateTimeFormatter.ISO_LOCAL_DATE)
}
