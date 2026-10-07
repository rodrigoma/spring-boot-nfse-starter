package io.github.rodrigoma.nfse.xml

import io.github.rodrigoma.nfse.model.response.CbsRates
import io.github.rodrigoma.nfse.model.response.CbsTotals
import io.github.rodrigoma.nfse.model.response.GovernmentPurchaseTotals
import io.github.rodrigoma.nfse.model.response.IbsCbsCalculation
import io.github.rodrigoma.nfse.model.response.IbsCbsTotals
import io.github.rodrigoma.nfse.model.response.IbsMunicipalRates
import io.github.rodrigoma.nfse.model.response.IbsSphereTotals
import io.github.rodrigoma.nfse.model.response.IbsStateRates
import io.github.rodrigoma.nfse.model.response.IbsTotals
import io.github.rodrigoma.nfse.model.response.NfseIbsCbs
import io.github.rodrigoma.nfse.model.response.PresumedCredit
import io.github.rodrigoma.nfse.model.response.RegularTaxationTotals
import org.w3c.dom.Element

/**
 * Reads the `IBSCBS` group of a generated NFS-e (`TCRTCIBSCBS`). Every value is optional here even where the XSD
 * makes it mandatory: a parser that threw on a missing element would turn a change on the government's side into
 * a failure to read notes the Sefin already accepted.
 */
internal object NfseIbsCbsParser {
    fun parse(infNfse: Element): NfseIbsCbs? {
        val root = infNfse.childElement("IBSCBS") ?: return null
        return NfseIbsCbs(
            incidenceMunicipalityIbge = root.childText("cLocalidadeIncid")?.toIntOrNull(),
            incidenceMunicipality = root.childText("xLocalidadeIncid"),
            governmentPurchaseReduction = root.childDecimal("pRedutor"),
            calculation = calculation(root.childElement("valores")),
            totals = totals(root.childElement("totCIBS")),
        )
    }

    private fun calculation(valores: Element?): IbsCbsCalculation {
        val uf = valores?.childElement("uf")
        val mun = valores?.childElement("mun")
        val fed = valores?.childElement("fed")
        return IbsCbsCalculation(
            calculationBase = valores?.childDecimal("vBC"),
            reimbursements = valores?.childDecimal("vCalcReeRepRes"),
            state =
                IbsStateRates(
                    rate = uf?.childDecimal("pIBSUF"),
                    reduction = uf?.childDecimal("pRedAliqUF"),
                    effectiveRate = uf?.childDecimal("pAliqEfetUF"),
                ),
            municipal =
                IbsMunicipalRates(
                    rate = mun?.childDecimal("pIBSMun"),
                    reduction = mun?.childDecimal("pRedAliqMun"),
                    effectiveRate = mun?.childDecimal("pAliqEfetMun"),
                ),
            federal =
                CbsRates(
                    rate = fed?.childDecimal("pCBS"),
                    reduction = fed?.childDecimal("pRedAliqCBS"),
                    effectiveRate = fed?.childDecimal("pAliqEfetCBS"),
                ),
        )
    }

    private fun totals(totCibs: Element?): IbsCbsTotals =
        IbsCbsTotals(
            netAmountWithIbsCbs = totCibs?.childDecimal("vTotNF"),
            ibs = ibs(totCibs?.childElement("gIBS")),
            cbs = cbs(totCibs?.childElement("gCBS")),
            regularTaxation = totCibs?.childElement("gTribRegular")?.let { regularTaxation(it) },
            governmentPurchase = totCibs?.childElement("gTribCompraGov")?.let { governmentPurchase(it) },
        )

    private fun ibs(group: Element?): IbsTotals =
        IbsTotals(
            amount = group?.childDecimal("vIBSTot"),
            presumedCredit = presumedCredit(group?.childElement("gIBSCredPres"), "pCredPresIBS", "vCredPresIBS"),
            state = sphere(group?.childElement("gIBSUFTot"), "vDifUF", "vIBSUF"),
            municipal = sphere(group?.childElement("gIBSMunTot"), "vDifMun", "vIBSMun"),
        )

    private fun cbs(group: Element?): CbsTotals =
        CbsTotals(
            presumedCredit = presumedCredit(group?.childElement("gCBSCredPres"), "pCredPresCBS", "vCredPresCBS"),
            deferred = group?.childDecimal("vDifCBS"),
            amount = group?.childDecimal("vCBS"),
        )

    private fun presumedCredit(
        group: Element?,
        percentage: String,
        amount: String,
    ): PresumedCredit? =
        group?.let {
            PresumedCredit(percentage = it.childDecimal(percentage), amount = it.childDecimal(amount))
        }

    private fun sphere(
        group: Element?,
        deferred: String,
        amount: String,
    ): IbsSphereTotals = IbsSphereTotals(deferred = group?.childDecimal(deferred), amount = group?.childDecimal(amount))

    private fun regularTaxation(group: Element): RegularTaxationTotals =
        RegularTaxationTotals(
            stateRate = group.childDecimal("pAliqEfeRegIBSUF"),
            stateAmount = group.childDecimal("vTribRegIBSUF"),
            municipalRate = group.childDecimal("pAliqEfeRegIBSMun"),
            municipalAmount = group.childDecimal("vTribRegIBSMun"),
            cbsRate = group.childDecimal("pAliqEfeRegCBS"),
            cbsAmount = group.childDecimal("vTribRegCBS"),
        )

    private fun governmentPurchase(group: Element): GovernmentPurchaseTotals =
        GovernmentPurchaseTotals(
            stateRate = group.childDecimal("pIBSUF"),
            stateAmount = group.childDecimal("vIBSUF"),
            municipalRate = group.childDecimal("pIBSMun"),
            municipalAmount = group.childDecimal("vIBSMun"),
            cbsRate = group.childDecimal("pCBS"),
            cbsAmount = group.childDecimal("vCBS"),
        )
}
