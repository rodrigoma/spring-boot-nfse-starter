package io.github.rodrigoma.nfse.model.dps

/** Marker for the XSD code tables: [code] is the literal written into the XML. */
interface XmlCode {
    val code: String
}

/** `tpEmit` — who is issuing the DPS. */
enum class DpsEmitterType(
    override val code: String,
) : XmlCode {
    PROVIDER("1"),
    TAKER("2"),
    INTERMEDIARY("3"),
}

/** `cMotivoEmisTI` — why the taker/intermediary (not the provider) is issuing. */
enum class TakerEmissionReason(
    override val code: String,
) : XmlCode {
    SERVICE_IMPORT("1"),
    REQUIRED_BY_MUNICIPAL_LAW("2"),
    PROVIDER_REFUSED_TO_ISSUE("3"),
    PROVIDER_NFSE_REJECTED("4"),
}

/** `cNaoNIF` — why a foreign party has no tax identification number. */
enum class NoNifReason(
    override val code: String,
) : XmlCode {
    NOT_INFORMED("0"),
    EXEMPT("1"),
    NOT_REQUIRED("2"),
}

/** `opSimpNac` — Simples Nacional status of the provider. */
enum class SimplesNacionalOption(
    override val code: String,
) : XmlCode {
    NOT_OPTING("1"),
    MEI("2"),
    ME_EPP("3"),
}

/** `regApTribSN` — assessment regime for ME/EPP that exceeded a Simples Nacional limit. */
enum class SimplesNacionalAssessment(
    override val code: String,
) : XmlCode {
    /** Federal taxes and ISSQN assessed inside the Simples Nacional. */
    SIMPLES_NACIONAL("1"),

    /** Federal taxes inside the Simples Nacional, ISSQN outside (municipal law). */
    FEDERAL_ONLY("2"),

    /** Federal taxes and ISSQN both outside the Simples Nacional. */
    NONE("3"),
}

/** `regEspTrib` — special municipal tax regime. */
enum class SpecialTaxRegime(
    override val code: String,
) : XmlCode {
    NONE("0"),
    COOPERATIVE("1"),
    ESTIMATE("2"),
    MUNICIPAL_MICRO_ENTERPRISE("3"),
    NOTARY("4"),
    SELF_EMPLOYED_PROFESSIONAL("5"),
    PROFESSIONAL_COMPANY("6"),
    OTHER("9"),
}

/** `cMotivo` of a substitution (`subst`). */
enum class SubstitutionReason(
    override val code: String,
) : XmlCode {
    LEFT_SIMPLES_NACIONAL("01"),
    JOINED_SIMPLES_NACIONAL("02"),
    RETROACTIVE_IMMUNITY_OR_EXEMPTION_INCLUDED("03"),
    RETROACTIVE_IMMUNITY_OR_EXEMPTION_EXCLUDED("04"),
    REJECTED_BY_TAKER_OR_INTERMEDIARY("05"),
    OTHER("99"),
}

/** `tribISSQN`. */
enum class IssqnTaxation(
    override val code: String,
) : XmlCode {
    TAXABLE("1"),
    IMMUNE("2"),
    EXPORT("3"),
    NOT_LEVIED("4"),
}

/** `tpRetISSQN`. */
enum class IssqnWithholding(
    override val code: String,
) : XmlCode {
    NOT_WITHHELD("1"),
    WITHHELD_BY_TAKER("2"),
    WITHHELD_BY_INTERMEDIARY("3"),
}

/** `tpImunidade` — only when [IssqnTaxation.IMMUNE]. */
enum class IssqnImmunityType(
    override val code: String,
) : XmlCode {
    NOT_INFORMED("0"),
    PUBLIC_ENTITIES("1"),
    TEMPLES("2"),
    PARTIES_UNIONS_EDUCATION_ASSISTANCE("3"),
    BOOKS_NEWSPAPERS_PERIODICALS("4"),
    BRAZILIAN_MUSIC_RECORDINGS("5"),
}

/** `tpSusp` — why the ISSQN enforceability is suspended. */
enum class SuspensionType(
    override val code: String,
) : XmlCode {
    JUDICIAL_DECISION("1"),
    ADMINISTRATIVE_PROCESS("2"),
}

/** `tpRetPisCofins`. */
enum class PisCofinsWithholding(
    override val code: String,
) : XmlCode {
    NONE_WITHHELD("0"),
    PIS_COFINS_WITHHELD("1"),
    PIS_COFINS_NOT_WITHHELD("2"),
    PIS_COFINS_CSLL_WITHHELD("3"),
    PIS_COFINS_WITHHELD_CSLL_NOT("4"),
    PIS_WITHHELD_COFINS_CSLL_NOT("5"),
    COFINS_WITHHELD_PIS_CSLL_NOT("6"),
    PIS_NOT_COFINS_CSLL_WITHHELD("7"),
    PIS_COFINS_NOT_CSLL_WITHHELD("8"),
    COFINS_NOT_PIS_CSLL_WITHHELD("9"),
}

/** `tpDedRed`. */
enum class DeductionType(
    override val code: String,
) : XmlCode {
    FOOD_AND_BEVERAGES("1"),
    MATERIALS("2"),
    EXTERNAL_PRODUCTION("3"),
    EXPENSE_REIMBURSEMENT("4"),
    CONSORTIUM_TRANSFER("5"),
    HEALTH_PLAN_TRANSFER("6"),
    SERVICES("7"),
    LABOR_SUBCONTRACTING("8"),
    PARTNER_PROFESSIONAL("9"),
    OTHER("99"),
}

/** `mdPrestacao` — foreign trade provision mode. */
enum class ProvisionMode(
    override val code: String,
) : XmlCode {
    UNKNOWN("0"),
    CROSS_BORDER("1"),
    CONSUMPTION_IN_BRAZIL("2"),
    COMMERCIAL_PRESENCE_ABROAD("3"),
    MOVEMENT_OF_PERSONS("4"),
}

/** `vincPrest` — relationship between the parties in a foreign-trade operation. */
enum class PartiesRelationship(
    override val code: String,
) : XmlCode {
    NONE("0"),
    SUBSIDIARY("1"),
    PARENT("2"),
    AFFILIATE("3"),
    HEAD_OFFICE("4"),
    BRANCH("5"),
    OTHER("6"),
    UNKNOWN("9"),
}

/** `movTempBens`. */
enum class TemporaryGoodsMovement(
    override val code: String,
) : XmlCode {
    UNKNOWN("0"),
    NO("1"),
    LINKED_IMPORT_DECLARATION("2"),
    LINKED_EXPORT_REGISTRATION("3"),
}

/** `finNFSe` (IBS/CBS group). */
enum class NfsePurpose(
    override val code: String,
) : XmlCode {
    REGULAR("0"),
}

/** `tpOper` (IBS/CBS group). */
enum class GovernmentOperationType(
    override val code: String,
) : XmlCode {
    SUPPLY_WITH_LATER_PAYMENT("1"),
    PAYMENT_FOR_PREVIOUS_SUPPLY("2"),
    SUPPLY_ALREADY_PAID("3"),
    PAYMENT_BEFORE_SUPPLY("4"),
    SIMULTANEOUS("5"),
}

/** `tpEnteGov`. */
enum class GovernmentEntityType(
    override val code: String,
) : XmlCode {
    FEDERAL("1"),
    STATE("2"),
    FEDERAL_DISTRICT("3"),
    MUNICIPAL("4"),
}

/** `indDest`. */
enum class RecipientIndicator(
    override val code: String,
) : XmlCode {
    TAKER_IS_RECIPIENT("0"),
    OTHER_RECIPIENT("1"),
}

/** `tipoChaveDFe`. */
enum class NationalDocumentKeyType(
    override val code: String,
) : XmlCode {
    NFSE("1"),
    NFE("2"),
    CTE("3"),
    OTHER("9"),
}

/** `tpReeRepRes`. */
enum class ReimbursementType(
    override val code: String,
) : XmlCode {
    REAL_ESTATE_BROKERAGE_TRANSFER("01"),
    TRAVEL_AGENCY_SUPPLIER_TRANSFER("02"),
    ADVERTISING_EXTERNAL_PRODUCTION("03"),
    ADVERTISING_MEDIA("04"),
    OTHER("99"),
}

/** Brazilian states (`UF`). */
@Suppress("EnumNaming")
enum class BrazilianState {
    AC,
    AL,
    AM,
    AP,
    BA,
    CE,
    DF,
    ES,
    GO,
    MA,
    MG,
    MS,
    MT,
    PA,
    PB,
    PE,
    PI,
    PR,
    RJ,
    RN,
    RO,
    RR,
    RS,
    SC,
    SE,
    SP,
    TO,
}
