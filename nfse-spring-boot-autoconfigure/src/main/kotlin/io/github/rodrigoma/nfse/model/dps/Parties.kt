package io.github.rodrigoma.nfse.model.dps

/**
 * Identification of a party (`CNPJ` | `CPF` | `NIF` | `cNaoNIF` choice in the XSD).
 * Digits only for [Cnpj] and [Cpf]; the constructors strip punctuation so `12.345.678/0001-90` is accepted.
 */
sealed interface FederalId {
    /** Fourteen digits. Equality is by [value], so `Cnpj("12.345.678/0001-95") == Cnpj("12345678000195")`. */
    class Cnpj(
        raw: String,
    ) : FederalId {
        val value: String = raw.filter(Char::isDigit)

        init {
            require(value.length == CNPJ_LENGTH) { "CNPJ must have $CNPJ_LENGTH digits, got '$raw'" }
        }

        /** The first eight digits — what the Sefin compares with the certificate. */
        val base: String get() = value.take(CNPJ_BASE_LENGTH)

        override fun equals(other: Any?): Boolean = other is Cnpj && other.value == value

        override fun hashCode(): Int = value.hashCode()

        override fun toString(): String = "Cnpj($value)"
    }

    /** Eleven digits. Equality is by [value]. */
    class Cpf(
        raw: String,
    ) : FederalId {
        val value: String = raw.filter(Char::isDigit)

        init {
            require(value.length == CPF_LENGTH) { "CPF must have $CPF_LENGTH digits, got '$raw'" }
        }

        override fun equals(other: Any?): Boolean = other is Cpf && other.value == value

        override fun hashCode(): Int = value.hashCode()

        override fun toString(): String = "Cpf($value)"
    }

    /** Foreign tax identification number (1–40 characters). */
    data class Nif(
        val value: String,
    ) : FederalId {
        init {
            require(value.length in 1..NIF_MAX_LENGTH) { "NIF must have 1 to $NIF_MAX_LENGTH characters" }
        }
    }

    /** Foreign party without a NIF. */
    data class NoNif(
        val reason: NoNifReason,
    ) : FederalId

    companion object {
        const val CNPJ_LENGTH = 14
        const val CNPJ_BASE_LENGTH = 8
        const val CPF_LENGTH = 11
        const val NIF_MAX_LENGTH = 40

        /** Builds a [Cnpj] or a [Cpf] from the digit count. */
        fun cnpjOrCpf(document: String): FederalId {
            val digits = document.filter(Char::isDigit)
            return when (digits.length) {
                CNPJ_LENGTH -> Cnpj(digits)
                CPF_LENGTH -> Cpf(digits)
                else -> throw IllegalArgumentException("Expected a CNPJ (14 digits) or a CPF (11 digits): '$document'")
            }
        }
    }
}

/** Where an address is: national (IBGE municipality + CEP) or foreign. `endNac` | `endExt`. */
sealed interface AddressLocation {
    data class National(
        val municipalityIbge: Int,
        val zipCode: String,
    ) : AddressLocation

    data class Foreign(
        val countryIso: String,
        val postalCode: String,
        val city: String,
        val stateOrProvince: String,
    ) : AddressLocation
}

/** `TCEndereco`. */
data class Address(
    val location: AddressLocation,
    val street: String,
    val number: String,
    val complement: String? = null,
    val district: String,
)

/** Location of a `TCEnderecoSimples` / `TCEnderObraEvento`: only the CEP for national addresses. */
sealed interface SimpleAddressLocation {
    data class ZipCode(
        val zipCode: String,
    ) : SimpleAddressLocation

    data class Foreign(
        val postalCode: String,
        val city: String,
        val stateOrProvince: String,
    ) : SimpleAddressLocation
}

/** `TCEnderecoSimples` / `TCEnderObraEvento` — used by constructions, events and IBS/CBS property info. */
data class SimpleAddress(
    val location: SimpleAddressLocation,
    val street: String,
    val number: String,
    val complement: String? = null,
    val district: String,
)

/** `regTrib` of the provider. */
data class TaxRegime(
    val simplesNacional: SimplesNacionalOption,
    /** Mandatory when [simplesNacional] is [SimplesNacionalOption.ME_EPP], forbidden otherwise (rules E0162/E0166). */
    val simplesNacionalAssessment: SimplesNacionalAssessment? = null,
    val specialRegime: SpecialTaxRegime = SpecialTaxRegime.NONE,
)

/** `prest` (`TCInfoPrestador`). */
data class ServiceProvider(
    val id: FederalId,
    val caepf: String? = null,
    val municipalRegistration: String? = null,
    /** Must be **absent** when the provider is the emitter of the DPS (rule E0121). */
    val name: String? = null,
    val address: Address? = null,
    val phone: String? = null,
    val email: String? = null,
    val taxRegime: TaxRegime,
)

/** `toma` / `interm` / `fornec` (`TCInfoPessoa`). */
data class Person(
    val id: FederalId,
    val caepf: String? = null,
    val municipalRegistration: String? = null,
    val name: String,
    val address: Address? = null,
    val phone: String? = null,
    val email: String? = null,
)
