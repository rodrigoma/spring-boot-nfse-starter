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
        fetch(municipalityIbge, null, null, "$municipalityIbge/convenio")

    override fun rates(
        municipalityIbge: Int,
        serviceCode: String,
        competence: LocalDate,
    ): MunicipalParameters =
        fetch(municipalityIbge, serviceCode, competence, "$municipalityIbge/$serviceCode/${competence.iso()}/aliquota")

    override fun rateHistory(
        municipalityIbge: Int,
        serviceCode: String,
    ): MunicipalParameters =
        fetch(municipalityIbge, serviceCode, null, "$municipalityIbge/$serviceCode/historicoaliquotas")

    override fun benefit(
        municipalityIbge: Int,
        benefitNumber: String,
        competence: LocalDate,
    ): MunicipalParameters =
        fetch(municipalityIbge, null, competence, "$municipalityIbge/$benefitNumber/${competence.iso()}/beneficio")

    override fun specialRegimes(
        municipalityIbge: Int,
        serviceCode: String,
        competence: LocalDate,
    ): MunicipalParameters =
        fetch(
            municipalityIbge,
            serviceCode,
            competence,
            "$municipalityIbge/$serviceCode/${competence.iso()}/regimes_especiais",
        )

    override fun withholdings(
        municipalityIbge: Int,
        competence: LocalDate,
    ): MunicipalParameters =
        fetch(municipalityIbge, null, competence, "$municipalityIbge/${competence.iso()}/retencoes")

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
