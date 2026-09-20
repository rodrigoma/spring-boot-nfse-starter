package io.github.rodrigoma.nfse.model.dps

import java.math.BigDecimal
import java.time.LocalDate

/** `dest` — the recipient when it is not the taker (`TCRTCInfoDest`). */
data class Recipient(
    val id: FederalId,
    val name: String,
    val address: Address? = null,
    val phone: String? = null,
    val email: String? = null,
)

/** `imovel` reference (`cCIB` | `end`). */
sealed interface PropertyReference {
    data class Cib(
        val cib: String,
    ) : PropertyReference

    data class Location(
        val address: SimpleAddress,
    ) : PropertyReference
}

/** `imovel` (`TCRTCInfoImovel`). */
data class PropertyInfo(
    val propertyRegistration: String? = null,
    val reference: PropertyReference,
)

/** Document referenced by a reimbursement/transfer entry (`dFeNacional` | `docFiscalOutro` | `docOutro`). */
sealed interface ReimbursementDocumentReference {
    data class NationalDocument(
        val keyType: NationalDocumentKeyType,
        val keyTypeDescription: String? = null,
        val key: String,
    ) : ReimbursementDocumentReference

    data class OtherFiscalDocument(
        val municipalityIbge: Int,
        val number: String,
        val description: String,
    ) : ReimbursementDocumentReference

    data class OtherDocument(
        val number: String,
        val description: String,
    ) : ReimbursementDocumentReference
}

/** `fornec` inside a reimbursement entry. */
data class ReimbursementSupplier(
    val id: FederalId,
    val name: String,
)

/** One `documentos` entry of `gReeRepRes`. */
data class ReimbursementDocument(
    val reference: ReimbursementDocumentReference,
    val supplier: ReimbursementSupplier? = null,
    val issueDate: LocalDate,
    val competenceDate: LocalDate,
    val type: ReimbursementType,
    /** Required when [type] is [ReimbursementType.OTHER]. */
    val typeDescription: String? = null,
    val amount: BigDecimal,
)

/** `gTribRegular`. */
data class RegularTaxation(
    val cst: String,
    val classificationCode: String,
)

/** `gDif` — deferral percentages. */
data class Deferral(
    val statePercentage: BigDecimal,
    val municipalPercentage: BigDecimal,
    val cbsPercentage: BigDecimal,
)

/** `gIBSCBS` (`TCRTCInfoTributosSitClas`). */
data class IbsCbsTax(
    /** `CST` — 3 digits. */
    val cst: String,
    /** `cClassTrib` — 6 digits. */
    val classificationCode: String,
    /** `cCredPres` — 2 digits. */
    val presumedCreditCode: String? = null,
    val regularTaxation: RegularTaxation? = null,
    val deferral: Deferral? = null,
)

/** `valores` of the IBS/CBS group. */
data class IbsCbsAmounts(
    val reimbursements: List<ReimbursementDocument> = emptyList(),
    val tax: IbsCbsTax,
)

/** `IBSCBS` — the tax reform group (`TCRTCInfoIBSCBS`). Optional in the DPS; modelled in full. */
data class IbsCbs(
    val purpose: NfsePurpose = NfsePurpose.REGULAR,
    /** `indFinal` — personal use/consumption. */
    val personalUse: Boolean? = null,
    /** `cIndOp` — 6-digit operation indicator (Anexo C). */
    val operationIndicatorCode: String,
    val governmentOperationType: GovernmentOperationType? = null,
    /** `gRefNFSe` — referenced NFS-e access keys. */
    val referencedNfse: List<String> = emptyList(),
    val governmentEntityType: GovernmentEntityType? = null,
    val recipientIndicator: RecipientIndicator = RecipientIndicator.TAKER_IS_RECIPIENT,
    val recipient: Recipient? = null,
    val property: PropertyInfo? = null,
    val amounts: IbsCbsAmounts,
)
