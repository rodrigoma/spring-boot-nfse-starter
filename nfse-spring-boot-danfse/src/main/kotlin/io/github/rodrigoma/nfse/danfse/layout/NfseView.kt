package io.github.rodrigoma.nfse.danfse.layout

import io.github.rodrigoma.nfse.danfse.layout.Formats.DASH
import io.github.rodrigoma.nfse.danfse.layout.Formats.orDash
import io.github.rodrigoma.nfse.xml.XmlSupport
import org.w3c.dom.Element
import java.math.BigDecimal

/**
 * Read-only view over the `NFSe` XML with the values the DANFSe prints, already formatted. Every accessor walks
 * direct children by local name (the paths of table 2.4.5 of NT 008), so nothing depends on a full object model.
 */
@Suppress("TooManyFunctions")
internal class NfseView(
    xml: String,
) {
    val infNfse: Element = XmlSupport.parse(xml).documentElement.child("infNFSe") ?: error("infNFSe is missing")
    val infDps: Element = infNfse.child("DPS")?.child("infDPS") ?: error("infDPS is missing")
    private val emit: Element? = infNfse.child("emit")
    private val values: Element? = infNfse.child("valores")
    private val dpsValues: Element? = infDps.child("valores")
    private val regTrib: Element? = infDps.child("prest")?.child("regTrib")
    private val tribMun: Element? = dpsValues?.child("trib")?.child("tribMun")
    private val tribFed: Element? = dpsValues?.child("trib")?.child("tribFed")
    private val pisCofins: Element? = tribFed?.child("piscofins")
    private val serviceCode: Element? = infDps.child("serv")?.child("cServ")
    private val ibsCbs: Element? = infNfse.child("IBSCBS")
    private val ibsCbsValues: Element? = ibsCbs?.child("valores")
    private val ibsCbsTotals: Element? = ibsCbs?.child("totCIBS")
    private val dpsIbsCbs: Element? = infDps.child("IBSCBS")
    private val gIbsCbs: Element? = dpsIbsCbs?.child("valores")?.child("trib")?.child("gIBSCBS")

    data class Party(
        val document: String,
        val municipalRegistration: String,
        val phone: String,
        val name: String,
        val municipality: String,
        val ibgeCep: String,
        val address: String,
        val email: String,
    ) {
        /** Fills the dashes of this party with [other]'s values. */
        fun completedWith(other: Party): Party =
            Party(
                document.or(other.document),
                municipalRegistration.or(other.municipalRegistration),
                phone.or(other.phone),
                name.or(other.name),
                municipality.or(other.municipality),
                ibgeCep.or(other.ibgeCep),
                address.or(other.address),
                email.or(other.email),
            )

        private fun String.or(other: String): String = if (this == DASH) other else this
    }

    // --- identification ------------------------------------------------------------------------------------

    val accessKey: String get() = infNfse.getAttribute("Id").removePrefix("NFS")
    val restrictedProduction: Boolean get() = infDps.text("tpAmb") == "2"
    val emitterMunicipality: String get() = infNfse.text("xLocEmi").orEmpty()
    val emitterUf: String? get() = emit?.child("enderNac")?.text("UF")
    val generatingEnvironment: String
        get() = describe(Descriptions.generatingEnvironment, infNfse.text("ambGer")).orEmpty()
    val environment: String get() = describe(Descriptions.environment, infDps.text("tpAmb")).orEmpty()
    val number: String get() = infNfse.text("nNFSe").orEmpty()
    val competence: String get() = Formats.date(infDps.text("dCompet"))
    val processedAt: String get() = Formats.dateTime(infNfse.text("dhProc"))
    val dpsNumber: String get() = infDps.text("nDPS").orEmpty()
    val dpsSeries: String get() = infDps.text("serie").orEmpty()
    val dpsIssuedAt: String get() = Formats.dateTime(infDps.text("dhEmi"))
    val emitterType: String get() = orDash(describe(Descriptions.emitter, infDps.text("tpEmit")))
    val status: String get() = orDash(describe(Descriptions.status, infNfse.text("cStat")))
    val purpose: String get() = orDash(describe(Descriptions.purpose, dpsIbsCbs?.text("finNFSe")))

    // --- parties -------------------------------------------------------------------------------------------

    /** The provider: the DPS `prest` group completed with the NFS-e `emit` group (name and address `prest` omits). */
    val provider: Party?
        get() {
            val fromDps = infDps.child("prest")?.let { party(it) } ?: return null
            val fromEmit = emit?.let { party(it, national = it.child("enderNac")) } ?: return fromDps
            return fromDps.completedWith(fromEmit)
        }
    val taker: Party? get() = infDps.child("toma")?.let { party(it) }
    val intermediary: Party? get() = infDps.child("interm")?.let { party(it) }
    val recipient: Party? get() = dpsIbsCbs?.child("dest")?.let { party(it) }

    /** `true` when the IBS/CBS group says the recipient is the taker (`indDest = 0`). */
    val recipientIsTaker: Boolean get() = dpsIbsCbs?.text("indDest") == "0"
    val simplesNacional: String get() = orDash(describe(Descriptions.simplesNacional, regTrib?.text("opSimpNac")))
    val simplesNacionalAssessment: String
        get() = orDash(describe(Descriptions.simplesNacionalAssessment, regTrib?.text("regApTribSN")))

    private fun party(
        element: Element,
        national: Element? = element.child("end")?.child("endNac"),
    ): Party {
        val end = element.child("end") ?: national
        val foreign = element.child("end")?.child("endExt")
        val location = if (national != null) nationalLocation(national) else foreign?.let { foreignLocation(it) }
        return Party(
            document = orDash(documentOf(element)),
            municipalRegistration = orDash(element.text("IM")),
            phone = orDash(element.text("fone")),
            name = orDash(element.text("xNome")),
            municipality = orDash(location?.first),
            ibgeCep = orDash(location?.second),
            address = orDash(end?.let { streetOf(it) }),
            email = orDash(element.text("email")),
        )
    }

    private fun streetOf(end: Element): String? =
        join(", ", end.text("xLgr"), end.text("nro"), end.text("xCpl"), end.text("xBairro"))

    private fun documentOf(element: Element): String? =
        element.text("CNPJ")?.let { Formats.cnpj(it) }
            ?: element.text("CPF")?.let { Formats.cpf(it) }
            ?: element.text("NIF")
            ?: element.text("cNaoNIF")?.let { "NIF não informado ($it)" }

    /** `Município / UF` and `IBGE / CEP` of a national address. */
    private fun nationalLocation(national: Element): Pair<String?, String?> =
        Places.municipalityLabel(national.text("cMun")) to
            join(" / ", national.text("cMun"), national.text("CEP")?.let { Formats.cep(it) })

    /** `City / Region / Country` and the postal code of a foreign address. */
    private fun foreignLocation(foreign: Element): Pair<String?, String?> =
        join(" / ", foreign.text("xCidade"), foreign.text("xEstProvReg"), Places.country(foreign.text("cPais"))) to
            foreign.text("cEndPost")

    // --- service ---------------------------------------------------------------------------------------------

    val taxCodes: String
        get() {
            val national = serviceCode?.text("cTribNac")?.let { Formats.nationalTaxCode(it) }
            return orDash(join(" / ", national, serviceCode?.text("cTribMun")))
        }
    val nbs: String get() = orDash(serviceCode?.text("cNBS")?.let { Formats.nbs(it) })
    val serviceLocation: String
        get() {
            val locPrest = infDps.child("serv")?.child("locPrest")
            locPrest?.text("cPaisPrestacao")?.let { return orDash(Places.country(it)) }
            val uf = Places.municipality(locPrest?.text("cLocPrestacao"))?.uf ?: emitterUf.orEmpty()
            return orDash(infNfse.text("xLocPrestacao")?.let { "$it / $uf / BR" })
        }
    val taxCodeDescription: String get() = infNfse.text("xTribMun") ?: infNfse.text("xTribNac").orEmpty()
    val serviceDescription: String get() = serviceCode?.text("xDescServ").orEmpty()

    // --- municipal taxation ---------------------------------------------------------------------------------

    val subjectToIssqn: Boolean get() = tribMun?.text("tribISSQN") == "1"
    val issqnTaxation: String get() = orDash(describe(Descriptions.issqnTaxation, tribMun?.text("tribISSQN")))
    val issqnIncidence: String
        get() {
            val uf = Places.municipality(infNfse.text("cLocIncid"))?.uf.orEmpty()
            val incidence = infNfse.text("xLocIncid")?.let { "$it / $uf / BR" }
            return orDash(incidence ?: tribMun?.text("cPaisResult")?.let { Places.country(it) })
        }
    val specialRegime: String? get() = describe(Descriptions.specialRegime, regTrib?.text("regEspTrib"))
    val immunity: String? get() = describe(Descriptions.immunity, tribMun?.text("tpImunidade"))
    val suspension: String? get() = describe(Descriptions.suspension, tribMun?.child("exigSusp")?.text("tpSusp"))
    val suspensionProcess: String? get() = tribMun?.child("exigSusp")?.text("nProcesso")
    val municipalBenefit: String? get() = describe(Descriptions.municipalBenefit, values?.text("tpBM"))
    val benefitCalculation: String?
        get() = (values?.text("vCalcBM") ?: tribMun?.child("BM")?.text("vRedBCBM"))?.let { Formats.money(it) }

    /** `vDR`/`vCalcDR` plus the IBS/CBS reimbursements (`vCalcReeRepRes`). */
    val deductions: String?
        get() {
            val direct = dpsValues?.child("vDedRed")?.text("vDR") ?: values?.text("vCalcDR")
            return sum(direct, ibsCbsValues?.text("vCalcReeRepRes"))?.let { Formats.money(it) }
        }
    val unconditionalDiscount: String?
        get() = dpsValues?.child("vDescCondIncond")?.text("vDescIncond")?.let { Formats.money(it) }
    val conditionalDiscount: String get() = Formats.money(dpsValues?.child("vDescCondIncond")?.text("vDescCond"))
    val calculationBase: String get() = Formats.money(values?.text("vBC"))
    val appliedRate: String get() = Formats.percent(values?.text("pAliqAplic"))
    val issqnWithholding: String get() = orDash(describe(Descriptions.issqnWithholding, tribMun?.text("tpRetISSQN")))
    val issqnAmount: String get() = Formats.money(values?.text("vISSQN"))

    // --- federal taxation -----------------------------------------------------------------------------------

    private val pisCofinsWithheld: Boolean get() = pisCofins?.text("tpRetPisCofins") == "1"
    val irrf: String get() = Formats.money(tribFed?.text("vRetIRRF"))
    val socialSecurity: String get() = Formats.money(tribFed?.text("vRetCP"))

    /** `vRetCSLL` (+ `vPis` + `vCofins` when PIS/COFINS are withheld). */
    val socialContributions: String
        get() {
            val pis = if (pisCofinsWithheld) pisCofins?.text("vPis") else null
            val cofins = if (pisCofinsWithheld) pisCofins?.text("vCofins") else null
            return Formats.money(sum(tribFed?.text("vRetCSLL"), pis, cofins))
        }
    val pis: String get() = Formats.money(unlessWithheld(pisCofins?.text("vPis")))
    val cofins: String get() = Formats.money(unlessWithheld(pisCofins?.text("vCofins")))

    private fun unlessWithheld(amount: String?): BigDecimal? =
        if (pisCofinsWithheld) BigDecimal.ZERO else amount?.toBigDecimalOrNull()

    val pisCofinsWithholding: String
        get() = orDash(describe(Descriptions.pisCofinsWithholding, pisCofins?.text("tpRetPisCofins")))

    /** Federal taxation is printed for competences up to 2026 (NT 008, note 6). */
    val printFederalTaxation: Boolean
        get() = (infDps.text("dCompet")?.take(YEAR_LENGTH)?.toIntOrNull() ?: 0) <= LAST_FEDERAL_YEAR

    // --- IBS / CBS ------------------------------------------------------------------------------------------

    val cstClassification: String get() = orDash(join(" / ", gIbsCbs?.text("CST"), gIbsCbs?.text("cClassTrib")))
    val ibsCbsIncidence: String
        get() {
            val municipality = ibsCbs?.text("cLocalidadeIncid")
            return orDash(join(" / ", dpsIbsCbs?.text("cIndOp"), municipality, Places.municipalityLabel(municipality)))
        }

    /** Sum of the amounts excluded from the IBS/CBS base (discount, reimbursements, ISSQN, PIS, COFINS). */
    val baseExclusions: String
        get() {
            if (ibsCbs == null) return DASH
            val discount = dpsValues?.child("vDescCondIncond")?.text("vDescIncond")
            val reimbursements = ibsCbsValues?.text("vCalcReeRepRes")
            val issqn = values?.text("vISSQN")
            val sum = sum(discount, reimbursements, issqn, pisCofins?.text("vPis"), pisCofins?.text("vCofins"))
            return Formats.money(sum ?: BigDecimal.ZERO)
        }
    val ibsCbsBase: String get() = Formats.money(ibsCbsValues?.text("vBC"))
    val rateReductions: String
        get() =
            ibsCbsValues?.let {
                val rates =
                    listOf(
                        it.child("uf")?.text("pRedAliqUF"),
                        it.child("mun")?.text("pRedAliqMun"),
                        it.child("fed")?.text("pRedAliqCBS"),
                    )
                rates.joinToString(" / ") { rate -> Formats.percent(rate) }
            } ?: DASH
    val ibsRates: String
        get() =
            ibsCbsValues?.let {
                val state = Formats.percent(it.child("uf")?.text("pIBSUF"))
                val municipal = Formats.percent(it.child("mun")?.text("pIBSMun"))
                "$state / $municipal"
            } ?: DASH
    val ibsMunicipalEffectiveRate: String get() = Formats.percent(ibsCbsValues?.child("mun")?.text("pAliqEfetMun"))
    val ibsMunicipalAmount: String
        get() = Formats.money(ibsCbsTotals?.child("gIBS")?.child("gIBSMunTot")?.text("vIBSMun"))
    val ibsStateEffectiveRate: String get() = Formats.percent(ibsCbsValues?.child("uf")?.text("pAliqEfetUF"))
    val ibsStateAmount: String get() = Formats.money(ibsCbsTotals?.child("gIBS")?.child("gIBSUFTot")?.text("vIBSUF"))
    val ibsTotal: String get() = Formats.money(ibsCbsTotals?.child("gIBS")?.text("vIBSTot"))
    val cbsRate: String get() = Formats.percent(ibsCbsValues?.child("fed")?.text("pCBS"))
    val cbsEffectiveRate: String get() = Formats.percent(ibsCbsValues?.child("fed")?.text("pAliqEfetCBS"))
    val cbsTotal: String get() = Formats.money(ibsCbsTotals?.child("gCBS")?.text("vCBS"))

    // --- totals ----------------------------------------------------------------------------------------------

    val serviceAmount: String get() = Formats.money(dpsValues?.child("vServPrest")?.text("vServ"))
    val totalWithholdings: String get() = Formats.money(values?.text("vTotalRet"))
    val netAmount: String get() = Formats.money(values?.text("vLiq"))
    val ibsCbsTotal: String
        get() =
            sum(ibsCbsTotals?.child("gIBS")?.text("vIBSTot"), ibsCbsTotals?.child("gCBS")?.text("vCBS"))
                ?.let { Formats.money(it) } ?: DASH
    val netAmountWithIbsCbs: String get() = Formats.money(ibsCbsTotals?.text("vTotNF"))

    // --- additional information ------------------------------------------------------------------------------

    /** The composed "Informações Complementares" text, in the order and with the labels NT 008 prescribes. */
    val additionalInformation: String
        get() = listOf(additionalNotes, approximateTaxes).filter { it.isNotEmpty() }.joinToString(" | ")

    /** The optional part of "Informações Complementares": everything before the approximate taxes line. */
    val additionalNotes: String
        get() {
            val info = infDps.child("serv")?.child("infoCompl")
            val obra = infDps.child("serv")?.child("obra")
            val items =
                info
                    ?.child("gItemPed")
                    ?.children("xItemPed")
                    ?.joinToString(", ") { it.textContent }
                    ?.ifEmpty { null }
            val parts =
                listOfNotNull(
                    info?.text("xInfComp")?.let { "Inf. Cont.: $it" },
                    infDps.child("subst")?.text("chSubstda")?.let { "NFS-e Subst.: $it" },
                    info?.text("docRef")?.let { "Doc. Ref.: $it" },
                    obra?.text("cObra")?.let { "Cod. Obra: $it" },
                    propertyRegistration(obra)?.let { "Insc. Imob.: $it" },
                    infDps
                        .child("serv")
                        ?.child("atvEvento")
                        ?.text("idAtvEvt")
                        ?.let { "Cod. Evt.: $it" },
                    info?.text("idDocTec")?.let { "Doc. Tec.: $it" },
                    info?.text("xPed")?.let { "Núm. Ped.: $it" },
                    items?.let { "Item Ped.: $it" },
                    infNfse.text("xOutInf")?.let { "Inf. A. T. Mun.: $it" },
                )
            return parts.joinToString(" | ")
        }

    private fun propertyRegistration(obra: Element?): String? =
        obra?.text("inscImobFisc") ?: dpsIbsCbs?.child("imovel")?.text("inscImobFisc")

    /** The mandatory "Totais Aproximados dos Tributos" line (Lei nº 12.741/2012). */
    val approximateTaxes: String
        get() {
            val tot = dpsValues?.child("trib")?.child("totTrib")
            val monetary = tot?.child("vTotTrib")
            val percentage = tot?.child("pTotTrib")
            val (federal, state, municipal) =
                when {
                    monetary != null ->
                        monetary.triple("vTotTribFed", "vTotTribEst", "vTotTribMun") { Formats.money(it) }
                    percentage != null ->
                        percentage.triple("pTotTribFed", "pTotTribEst", "pTotTribMun") { Formats.percent(it) }
                    tot?.text("pTotTribSN") != null -> Triple(DASH, DASH, Formats.percent(tot.text("pTotTribSN")))
                    else -> Triple(DASH, DASH, DASH)
                }
            return "Totais Aproximados dos Tributos cfe. Lei nº 12.741/2012: " +
                "Federais: $federal; Estaduais: $state; Municipais: $municipal"
        }

    // --- helpers ---------------------------------------------------------------------------------------------

    private fun describe(
        table: Map<String, String>,
        code: String?,
    ): String? = Descriptions.of(table, code)

    private fun join(
        separator: String,
        vararg parts: String?,
    ): String? = parts.filterNotNull().joinToString(separator).ifEmpty { null }

    /** Sum of the parseable decimals among [parts], or `null` when there is none. */
    private fun sum(vararg parts: String?): BigDecimal? =
        parts.mapNotNull { it?.toBigDecimalOrNull() }.ifEmpty { null }?.fold(BigDecimal.ZERO) { a, b -> a + b }

    private fun Element.triple(
        first: String,
        second: String,
        third: String,
        format: (String?) -> String,
    ): Triple<String, String, String> = Triple(format(text(first)), format(text(second)), format(text(third)))

    private companion object {
        const val LAST_FEDERAL_YEAR = 2026
        const val YEAR_LENGTH = 4
    }
}

internal fun Element.child(name: String): Element? = children(name).firstOrNull()

internal fun Element.children(name: String): List<Element> =
    (0 until childNodes.length).mapNotNull { childNodes.item(it) as? Element }.filter { it.localName == name }

internal fun Element.text(name: String): String? = child(name)?.textContent?.takeIf { it.isNotBlank() }
