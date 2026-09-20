package io.github.rodrigoma.nfse.client

import io.github.rodrigoma.nfse.model.response.MunicipalParameters
import java.time.LocalDate

/**
 * ADN Parâmetros Municipais (`adn-parametrizacao.openapi.json`). Every call answers `{ "mensagem", <payload> }`;
 * the payload is exposed in [MunicipalParameters.raw] under its own key (`parametrosConvenio`, `aliquotas`,
 * `beneficio`, `regimesEspeciais`, `retencoes`). A 404 means the municipality/service has no such parameter.
 */
interface MunicipalParametersClient {
    /** `GET /{codigoMunicipio}/convenio`. */
    fun agreement(municipalityIbge: Int): MunicipalParameters

    /** `GET /{codigoMunicipio}/{codigoServico}/{competencia}/aliquota` — ISSQN rates in force at [competence]. */
    fun rates(
        municipalityIbge: Int,
        serviceCode: String,
        competence: LocalDate,
    ): MunicipalParameters

    /** `GET /{codigoMunicipio}/{codigoServico}/historicoaliquotas`. */
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

    /** `GET /{codigoMunicipio}/{codigoServico}/{competencia}/regimes_especiais`. */
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
