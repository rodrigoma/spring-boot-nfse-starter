package io.github.rodrigoma.nfse.model.dps

import java.math.BigDecimal
import java.time.LocalDate

/** `locPrest` — where the service was provided. Use IBGE code `0` for "Águas Marítimas". */
sealed interface ServiceLocation {
    data class Municipality(
        val ibge: Int,
    ) : ServiceLocation

    data class Country(
        val iso: String,
    ) : ServiceLocation
}

/** `cServ`. */
data class ServiceCode(
    /** `cTribNac` — 6 digits: LC 116 item (2) + sub-item (2) + national breakdown (2), e.g. `010701`. */
    val nationalTaxCode: String,
    /** `cTribMun` — 3 digits, when the municipality requires a complementary code. */
    val municipalTaxCode: String? = null,
    /** `xDescServ` — free text, up to 2000 characters, line breaks allowed. */
    val description: String,
    /** `cNBS` — 9 digits. */
    val nbsCode: String? = null,
    /** `cIntContrib` — the caller's own code for the service (alphanumeric, ≤ 20). */
    val internalCode: String? = null,
)

/** `comExt` — foreign trade group. */
data class ForeignTrade(
    val provisionMode: ProvisionMode,
    val partiesRelationship: PartiesRelationship,
    /** BACEN currency code, 3 digits. */
    val currencyCode: String,
    val amountInCurrency: BigDecimal,
    /** `mecAFComexP` — 2-digit code from the XSD table. */
    val providerSupportMechanism: String,
    /** `mecAFComexT` — 2-digit code from the XSD table. */
    val takerSupportMechanism: String,
    val temporaryGoodsMovement: TemporaryGoodsMovement,
    val importDeclaration: String? = null,
    val exportRegistration: String? = null,
    /** `mdic` — share the NFS-e with the foreign trade secretariat. */
    val shareWithMdic: Boolean,
)

/** `obra` — the construction site reference (`cObra` | `cCIB` | `end`). */
sealed interface ConstructionReference {
    data class Code(
        val code: String,
    ) : ConstructionReference

    data class Cib(
        val cib: String,
    ) : ConstructionReference

    data class Location(
        val address: SimpleAddress,
    ) : ConstructionReference
}

data class Construction(
    val propertyRegistration: String? = null,
    val reference: ConstructionReference,
)

/** `atvEvento` reference (`idAtvEvt` | `end`). */
sealed interface EventActivityReference {
    data class Id(
        val id: String,
    ) : EventActivityReference

    data class Location(
        val address: SimpleAddress,
    ) : EventActivityReference
}

data class EventActivity(
    val name: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val reference: EventActivityReference,
)

/** `infoCompl`. */
data class AdditionalInfo(
    val technicalDocumentId: String? = null,
    val referenceDocument: String? = null,
    val purchaseOrder: String? = null,
    val purchaseOrderItems: List<String> = emptyList(),
    /** `xInfComp` — free text, up to 2000 characters. */
    val text: String? = null,
)

/** `serv` (`TCServ`). */
data class ServiceInfo(
    val location: ServiceLocation,
    val code: ServiceCode,
    val foreignTrade: ForeignTrade? = null,
    val construction: Construction? = null,
    val eventActivity: EventActivity? = null,
    val additionalInfo: AdditionalInfo? = null,
)
