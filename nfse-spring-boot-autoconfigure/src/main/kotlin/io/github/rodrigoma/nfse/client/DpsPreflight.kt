package io.github.rodrigoma.nfse.client

import io.github.rodrigoma.nfse.exception.NfseError
import io.github.rodrigoma.nfse.exception.NfseException
import io.github.rodrigoma.nfse.model.dps.Dps
import io.github.rodrigoma.nfse.model.dps.FederalId
import io.github.rodrigoma.nfse.model.dps.SimplesNacionalOption
import io.github.rodrigoma.nfse.model.dps.TaxIdCheckDigits
import io.github.rodrigoma.nfse.model.dps.TotalTaxes

/**
 * Cheap checks the Sefin would reject anyway, run before the XML is even built: CPF/CNPJ check digits of every
 * party (rules E0080/E0096 and their taker/intermediary counterparts) and the total-taxes group of a Simples
 * Nacional provider (E0712). Failures surface as [NfseException.Validation] with the official codes.
 */
internal object DpsPreflight {
    fun check(dps: Dps) {
        val errors = mutableListOf<NfseError>()

        fun check(
            id: FederalId,
            party: String,
            cnpjCode: String,
            cpfCode: String,
        ) {
            when (id) {
                is FederalId.Cnpj ->
                    if (!TaxIdCheckDigits.isValidCnpj(id.value)) {
                        errors +=
                            NfseError(cnpjCode, "CNPJ do $party informado na DPS é inválido.")
                    }
                is FederalId.Cpf ->
                    if (!TaxIdCheckDigits.isValidCpf(id.value)) {
                        errors +=
                            NfseError(cpfCode, "CPF do $party informado na DPS é inválido.")
                    }
                else -> Unit
            }
        }
        check(dps.provider.id, "prestador", "E0080", "E0096")
        dps.taker?.let { check(it.id, "tomador", "E0200", "E0210") }
        dps.intermediary?.let { check(it.id, "intermediário", "E0260", "E0270") }
        totalTaxesOfSimplesNacional(dps)?.let { errors += it }
        if (errors.isNotEmpty()) throw NfseException.Validation(errors)
    }

    /**
     * `totTrib` is a mandatory choice in the XSD (`vTotTrib` | `pTotTrib` | `indTotTrib` | `pTotTribSN`), and a
     * **ME/EPP** provider may not pick `indTotTrib` — the Sefin answers **E0712**. Since the default of
     * [io.github.rodrigoma.nfse.model.dps.Taxes.total] is exactly that, the combination would otherwise reach the
     * wire on every first attempt. The percentage itself is a fiscal value the library will not invent, so this
     * says what to inform instead of guessing.
     */
    private fun totalTaxesOfSimplesNacional(dps: Dps): NfseError? {
        val meEpp = dps.provider.taxRegime.simplesNacional == SimplesNacionalOption.ME_EPP
        if (!meEpp || dps.amounts.taxes.total != TotalTaxes.NotInformed) return null
        return NfseError(
            "E0712",
            "Para ME/EPP o indicador de informação de valor total de tributos não pode ser informado. " +
                "Informe TotalTaxes.SimplesNacionalPercentage com o percentual aproximado dos tributos da " +
                "alíquota do Simples Nacional (pTotTribSN), ou os totais em TotalTaxes.Amounts/Percentages.",
        )
    }
}
