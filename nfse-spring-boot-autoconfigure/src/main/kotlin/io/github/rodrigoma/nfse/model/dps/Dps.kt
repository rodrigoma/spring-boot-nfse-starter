package io.github.rodrigoma.nfse.model.dps

import io.github.rodrigoma.nfse.autoconfigure.NfseEnvironment
import java.time.LocalDate
import java.time.OffsetDateTime

/**
 * Identifier of a DPS (`infDPS/@Id` without the `DPS` prefix is what `GET /dps/{id}` expects).
 *
 * Formation rule (Anexo I): `"DPS"` + IBGE municipality (7) + federal id type (1: `1` = CPF, `2` = CNPJ) +
 * federal id (14, CPF left-padded with zeros) + series (5) + number (15) — 45 characters.
 */
data class DpsId(
    val municipalityIbge: Int,
    val emitter: FederalId,
    val series: Int,
    val number: Long,
) {
    init {
        require(emitter is FederalId.Cnpj || emitter is FederalId.Cpf) { "A DPS is identified by a CNPJ or a CPF" }
        require(municipalityIbge in 0..MAX_IBGE) { "Invalid IBGE municipality code $municipalityIbge" }
        require(series in 1..MAX_SERIES) { "DPS series must be between 1 and $MAX_SERIES, got $series" }
        require(number in 1..MAX_NUMBER) { "DPS number must be between 1 and $MAX_NUMBER, got $number" }
    }

    /** The 42-digit body: everything after the `DPS` literal. */
    val digits: String =
        municipalityIbge.toString().padStart(IBGE_LENGTH, '0') +
            federalIdType(emitter) +
            federalIdDigits(emitter).padStart(FEDERAL_ID_LENGTH, '0') +
            series.toString().padStart(SERIES_LENGTH, '0') +
            number.toString().padStart(NUMBER_LENGTH, '0')

    /** The full identifier, `DPS` + 42 digits. */
    val value: String = PREFIX + digits

    override fun toString(): String = value

    companion object {
        const val PREFIX = "DPS"
        const val IBGE_LENGTH = 7
        const val FEDERAL_ID_LENGTH = 14
        const val SERIES_LENGTH = 5
        const val NUMBER_LENGTH = 15
        const val MAX_IBGE = 9_999_999
        const val MAX_SERIES = 99_999
        const val MAX_NUMBER = 999_999_999_999_999L

        private fun federalIdType(id: FederalId): String = if (id is FederalId.Cpf) "1" else "2"

        private fun federalIdDigits(id: FederalId): String =
            when (id) {
                is FederalId.Cnpj -> id.value
                is FederalId.Cpf -> id.value
                else -> error("unreachable")
            }
    }
}

/** `subst` — the note being replaced by this DPS. */
data class Substitution(
    val substitutedAccessKey: String,
    val reason: SubstitutionReason,
    /** 15–255 characters; mandatory in practice when [reason] is [SubstitutionReason.OTHER]. */
    val justification: String? = null,
)

/**
 * The complete DPS (`TCInfDPS`, layout 1.01). Everything the schema allows is here;
 * [io.github.rodrigoma.nfse.model.request.DpsRequest] is the short form most applications use, and `DpsAssembler`
 * turns it into this.
 */
data class Dps(
    val environment: NfseEnvironment,
    /** `dhEmi` — the offset is written as `±hh:mm` (`Z` is not accepted by the schema). */
    val issuedAt: OffsetDateTime,
    /** `verAplic` — 1 to 20 characters. */
    val applicationVersion: String,
    val series: Int,
    val number: Long,
    /** `dCompet`. */
    val competenceDate: LocalDate,
    val emitterType: DpsEmitterType = DpsEmitterType.PROVIDER,
    val takerEmissionReason: TakerEmissionReason? = null,
    /** `chNFSeRej` — the rejected NFS-e for [TakerEmissionReason.PROVIDER_NFSE_REJECTED]. */
    val rejectedNfseAccessKey: String? = null,
    /** `cLocEmi` — IBGE code of the emitting municipality. */
    val emitterMunicipalityIbge: Int,
    val substitution: Substitution? = null,
    val provider: ServiceProvider,
    val taker: Person? = null,
    val intermediary: Person? = null,
    val service: ServiceInfo,
    val amounts: Amounts,
    val ibsCbs: IbsCbs? = null,
) {
    /** Identifier of this DPS, derived from the emitter (`tpEmit`) party. */
    val id: DpsId
        get() {
            val emitter =
                when (emitterType) {
                    DpsEmitterType.PROVIDER -> provider.id
                    DpsEmitterType.TAKER -> requireNotNull(taker) { "taker is required when tpEmit = 2" }.id
                    DpsEmitterType.INTERMEDIARY ->
                        requireNotNull(intermediary) { "intermediary is required when tpEmit = 3" }.id
                }
            return DpsId(emitterMunicipalityIbge, emitter, series, number)
        }
}
