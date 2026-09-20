package io.github.rodrigoma.nfse.xml

import io.github.rodrigoma.nfse.model.dps.Amounts
import io.github.rodrigoma.nfse.model.dps.BenefitReduction
import io.github.rodrigoma.nfse.model.dps.DeductionDocument
import io.github.rodrigoma.nfse.model.dps.DeductionDocumentReference
import io.github.rodrigoma.nfse.model.dps.Deductions
import io.github.rodrigoma.nfse.model.dps.FederalTax
import io.github.rodrigoma.nfse.model.dps.MunicipalTax
import io.github.rodrigoma.nfse.model.dps.TotalTaxes
import org.w3c.dom.Element

/** Writer for the `valores` group (`TCInfoValores`). */
internal object AmountsXml {
    private const val PROCESS_NUMBER_LENGTH = 30

    fun write(
        parent: Element,
        amounts: Amounts,
    ) {
        val valores = parent.child("valores")
        valores.child("vServPrest").apply {
            decimalIfPresent("vReceb", amounts.receivedAmount)
            decimal("vServ", amounts.serviceAmount)
        }
        if (amounts.unconditionalDiscount != null || amounts.conditionalDiscount != null) {
            valores.child("vDescCondIncond").apply {
                decimalIfPresent("vDescIncond", amounts.unconditionalDiscount)
                decimalIfPresent("vDescCond", amounts.conditionalDiscount)
            }
        }
        amounts.deductions?.let { deductions(valores, it) }
        val trib = valores.child("trib")
        municipalTax(trib, amounts.taxes.municipal)
        amounts.taxes.federal?.let { federalTax(trib, it) }
        totalTaxes(trib, amounts.taxes.total)
    }

    private fun deductions(
        parent: Element,
        deductions: Deductions,
    ) {
        val vDedRed = parent.child("vDedRed")
        when (deductions) {
            is Deductions.Percentage -> vDedRed.decimal("pDR", deductions.percentage)
            is Deductions.Amount -> vDedRed.decimal("vDR", deductions.amount)
            is Deductions.Documents ->
                vDedRed.child("documentos").apply { deductions.documents.forEach { deductionDocument(this, it) } }
        }
    }

    private fun deductionDocument(
        parent: Element,
        document: DeductionDocument,
    ) {
        val doc = parent.child("docDedRed")
        when (val reference = document.reference) {
            is DeductionDocumentReference.NfseKey -> doc.text("chNFSe", reference.accessKey)
            is DeductionDocumentReference.NfeKey -> doc.text("chNFe", reference.accessKey)
            is DeductionDocumentReference.MunicipalNfse ->
                doc.child("NFSeMun").apply {
                    text("cMunNFSeMun", PartyXml.ibge(reference.municipalityIbge))
                    text("nNFSeMun", reference.number)
                    text("cVerifNFSeMun", reference.verificationCode)
                }
            is DeductionDocumentReference.NfNfs ->
                doc.child("NFNFS").apply {
                    text("nNFS", reference.number)
                    text("modNFS", reference.model)
                    text("serieNFS", reference.series)
                }
            is DeductionDocumentReference.FiscalDocumentNumber -> doc.text("nDocFisc", reference.number)
            is DeductionDocumentReference.DocumentNumber -> doc.text("nDoc", reference.number)
        }
        doc.code("tpDedRed", document.type)
        doc.textIfPresent("xDescOutDed", document.otherDescription)
        doc.date("dtEmiDoc", document.issueDate)
        doc.decimal("vDedutivelRedutivel", document.deductibleAmount)
        doc.decimal("vDeducaoReducao", document.deductedAmount)
        document.supplier?.let { PartyXml.person(doc, "fornec", it) }
    }

    private fun municipalTax(
        parent: Element,
        tax: MunicipalTax,
    ) {
        val tribMun = parent.child("tribMun")
        tribMun.code("tribISSQN", tax.taxation)
        tribMun.textIfPresent("cPaisResult", tax.resultCountryIso)
        tribMun.codeIfPresent("tpImunidade", tax.immunityType)
        tax.suspendedEnforceability?.let {
            tribMun.child("exigSusp").apply {
                code("tpSusp", it.type)
                text("nProcesso", it.processNumber.filter(Char::isDigit).padStart(PROCESS_NUMBER_LENGTH, '0'))
            }
        }
        tax.municipalBenefit?.let { benefit ->
            tribMun.child("BM").apply {
                text("nBM", benefit.number)
                when (val reduction = benefit.reduction) {
                    is BenefitReduction.Amount -> decimal("vRedBCBM", reduction.amount)
                    is BenefitReduction.Percentage -> decimal("pRedBCBM", reduction.percentage)
                    null -> Unit
                }
            }
        }
        tribMun.code("tpRetISSQN", tax.withholding)
        tribMun.decimalIfPresent("pAliq", tax.rate)
    }

    private fun federalTax(
        parent: Element,
        tax: FederalTax,
    ) {
        val tribFed = parent.child("tribFed")
        tax.pisCofins?.let {
            tribFed.child("piscofins").apply {
                text("CST", it.cst)
                decimalIfPresent("vBCPisCofins", it.calculationBase)
                decimalIfPresent("pAliqPis", it.pisRate)
                decimalIfPresent("pAliqCofins", it.cofinsRate)
                decimalIfPresent("vPis", it.pisAmount)
                decimalIfPresent("vCofins", it.cofinsAmount)
                codeIfPresent("tpRetPisCofins", it.withholding)
            }
        }
        tribFed.decimalIfPresent("vRetCP", tax.withheldSocialSecurity)
        tribFed.decimalIfPresent("vRetIRRF", tax.withheldIncomeTax)
        tribFed.decimalIfPresent("vRetCSLL", tax.withheldCsll)
    }

    private fun totalTaxes(
        parent: Element,
        total: TotalTaxes,
    ) {
        val totTrib = parent.child("totTrib")
        when (total) {
            is TotalTaxes.Amounts ->
                totTrib.child("vTotTrib").apply {
                    decimal("vTotTribFed", total.federal)
                    decimal("vTotTribEst", total.state)
                    decimal("vTotTribMun", total.municipal)
                }
            is TotalTaxes.Percentages ->
                totTrib.child("pTotTrib").apply {
                    decimal("pTotTribFed", total.federal)
                    decimal("pTotTribEst", total.state)
                    decimal("pTotTribMun", total.municipal)
                }
            TotalTaxes.NotInformed -> totTrib.text("indTotTrib", "0")
            is TotalTaxes.SimplesNacionalPercentage -> totTrib.decimal("pTotTribSN", total.percentage)
        }
    }
}
