package io.github.rodrigoma.nfse.client

import io.github.rodrigoma.nfse.model.response.MunicipalParameters
import java.time.LocalDate

/**
 * ADN Parâmetros Municipais (`adn-parametrizacao.openapi.json`). Every call answers `{ "mensagem", <payload> }`;
 * the payload is exposed in [MunicipalParameters.raw] under its own key (`parametrosConvenio`, `aliquotas`,
 * `beneficio`, `regimesEspeciais`, `retencoes`). A 404 means the municipality/service has no such parameter.
 *
 * **`serviceCode` is not the `cTribNac` of the DPS.** This service identifies a service by its *complete* code —
 * the six digits of `cTribNac` plus the three of the municipal complement (`cTribMun`), written with separators:
 * `01.09.02.001`. The Emissor Nacional shows both fields side by side ("Código de Tributação Nacional" and
 * "Código Complementar Municipal"). Anything else is answered with HTTP 400 and
 * *"Chamada mal formada. O código do serviço deve ser composto por nove dígitos"* — which counts digits, not
 * characters, so the separators are required. [ServiceCodes.complete] accepts either spelling and normalises it.
 */
interface MunicipalParametersClient {
    /** `GET /{codigoMunicipio}/convenio`. */
    fun agreement(municipalityIbge: Int): MunicipalParameters

    /**
     * `GET /{codigoMunicipio}/{codigoServico}/{competencia}/aliquota` — ISSQN rates in force at [competence].
     *
     * @param serviceCode The complete code, `01.09.02.001` or `010902001`; see the class documentation.
     * @throws IllegalArgumentException when [serviceCode] is not nine digits.
     */
    fun rates(
        municipalityIbge: Int,
        serviceCode: String,
        competence: LocalDate,
    ): MunicipalParameters

    /**
     * `GET /{codigoMunicipio}/{codigoServico}/historicoaliquotas`.
     *
     * @param serviceCode The complete code, `01.09.02.001` or `010902001`; see the class documentation.
     * @throws IllegalArgumentException when [serviceCode] is not nine digits.
     */
    fun rateHistory(
        municipalityIbge: Int,
        serviceCode: String,
    ): MunicipalParameters

    /** `GET /{codigoMunicipio}/{numeroBeneficio}/{competencia}/beneficio`. */
    fun benefit(
        municipalityIbge: Int,
        benefitNumber: String,
        competence: LocalDate,
    ): MunicipalParameters

    /**
     * `GET /{codigoMunicipio}/{codigoServico}/{competencia}/regimes_especiais`.
     *
     * @param serviceCode The complete code, `01.09.02.001` or `010902001`; see the class documentation.
     * @throws IllegalArgumentException when [serviceCode] is not nine digits.
     */
    fun specialRegimes(
        municipalityIbge: Int,
        serviceCode: String,
        competence: LocalDate,
    ): MunicipalParameters

    /** `GET /{codigoMunicipio}/{competencia}/retencoes`. */
    fun withholdings(
        municipalityIbge: Int,
        competence: LocalDate,
    ): MunicipalParameters
}
