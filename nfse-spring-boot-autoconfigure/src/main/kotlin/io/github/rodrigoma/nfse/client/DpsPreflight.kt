package io.github.rodrigoma.nfse.client

import io.github.rodrigoma.nfse.exception.NfseError
import io.github.rodrigoma.nfse.exception.NfseException
import io.github.rodrigoma.nfse.model.dps.Dps
import io.github.rodrigoma.nfse.model.dps.FederalId
import io.github.rodrigoma.nfse.model.dps.TaxIdCheckDigits

/**
 * Cheap checks the Sefin would reject anyway, run before the XML is even built: CPF/CNPJ check digits of every
 * party (rules E0080/E0096 and their taker/intermediary counterparts). Failures surface as
 * [NfseException.Validation] with the official codes.
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
        if (errors.isNotEmpty()) throw NfseException.Validation(errors)
    }
}
