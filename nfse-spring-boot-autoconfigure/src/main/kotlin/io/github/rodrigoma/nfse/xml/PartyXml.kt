package io.github.rodrigoma.nfse.xml

import io.github.rodrigoma.nfse.model.dps.Address
import io.github.rodrigoma.nfse.model.dps.AddressLocation
import io.github.rodrigoma.nfse.model.dps.FederalId
import io.github.rodrigoma.nfse.model.dps.Person
import io.github.rodrigoma.nfse.model.dps.ServiceProvider
import io.github.rodrigoma.nfse.model.dps.SimpleAddress
import io.github.rodrigoma.nfse.model.dps.SimpleAddressLocation
import io.github.rodrigoma.nfse.model.dps.TaxRegime
import org.w3c.dom.Element

/** Writers for the party-related groups (`prest`, `toma`, `interm`, addresses). Element order follows the XSD. */
internal object PartyXml {
    private const val IBGE_LENGTH = 7

    fun federalId(
        parent: Element,
        id: FederalId,
    ) {
        when (id) {
            is FederalId.Cnpj -> parent.text("CNPJ", id.value)
            is FederalId.Cpf -> parent.text("CPF", id.value)
            is FederalId.Nif -> parent.text("NIF", id.value)
            is FederalId.NoNif -> parent.code("cNaoNIF", id.reason)
        }
    }

    fun ibge(value: Int): String = value.toString().padStart(IBGE_LENGTH, '0')

    /** `TCEndereco`. */
    fun address(
        parent: Element,
        address: Address,
    ) {
        val end = parent.child("end")
        when (val location = address.location) {
            is AddressLocation.National ->
                end.child("endNac").apply {
                    text("cMun", ibge(location.municipalityIbge))
                    text("CEP", location.zipCode)
                }
            is AddressLocation.Foreign ->
                end.child("endExt").apply {
                    text("cPais", location.countryIso)
                    text("cEndPost", location.postalCode)
                    text("xCidade", location.city)
                    text("xEstProvReg", location.stateOrProvince)
                }
        }
        streetLines(end, address.street, address.number, address.complement, address.district)
    }

    /** `TCEnderecoSimples` / `TCEnderObraEvento`. */
    fun simpleAddress(
        parent: Element,
        address: SimpleAddress,
    ) {
        val end = parent.child("end")
        when (val location = address.location) {
            is SimpleAddressLocation.ZipCode -> end.text("CEP", location.zipCode)
            is SimpleAddressLocation.Foreign ->
                end.child("endExt").apply {
                    text("cEndPost", location.postalCode)
                    text("xCidade", location.city)
                    text("xEstProvReg", location.stateOrProvince)
                }
        }
        streetLines(end, address.street, address.number, address.complement, address.district)
    }

    private fun streetLines(
        end: Element,
        street: String,
        number: String,
        complement: String?,
        district: String,
    ) {
        end.text("xLgr", street)
        end.text("nro", number)
        end.textIfPresent("xCpl", complement)
        end.text("xBairro", district)
    }

    /** `prest` (`TCInfoPrestador`). */
    fun provider(
        parent: Element,
        provider: ServiceProvider,
    ) {
        val prest = parent.child("prest")
        federalId(prest, provider.id)
        prest.textIfPresent("CAEPF", provider.caepf)
        prest.textIfPresent("IM", provider.municipalRegistration)
        prest.textIfPresent("xNome", provider.name)
        provider.address?.let { address(prest, it) }
        prest.textIfPresent("fone", provider.phone)
        prest.textIfPresent("email", provider.email)
        taxRegime(prest, provider.taxRegime)
    }

    private fun taxRegime(
        parent: Element,
        regime: TaxRegime,
    ) {
        parent.child("regTrib").apply {
            code("opSimpNac", regime.simplesNacional)
            codeIfPresent("regApTribSN", regime.simplesNacionalAssessment)
            code("regEspTrib", regime.specialRegime)
        }
    }

    /** `toma` / `interm` / `fornec` (`TCInfoPessoa`). */
    fun person(
        parent: Element,
        name: String,
        person: Person,
    ) {
        val element = parent.child(name)
        federalId(element, person.id)
        element.textIfPresent("CAEPF", person.caepf)
        element.textIfPresent("IM", person.municipalRegistration)
        element.text("xNome", person.name)
        person.address?.let { address(element, it) }
        element.textIfPresent("fone", person.phone)
        element.textIfPresent("email", person.email)
    }
}
