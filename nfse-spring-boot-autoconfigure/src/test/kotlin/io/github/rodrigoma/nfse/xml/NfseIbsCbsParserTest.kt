package io.github.rodrigoma.nfse.xml

import io.github.rodrigoma.nfse.support.TestXml
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal

/**
 * The `IBSCBS` the Sefin computes is what an emitter posts to its ledger from 2026-10-01 on, so it is read into
 * typed fields instead of leaving every application to parse the XML again.
 */
class NfseIbsCbsParserTest {
    private val ibsCbs = requireNotNull(NfseXmlParser.parseNfse(TestXml.nfse(ibsCbs = TestXml.IBS_CBS)).ibsCbs)

    @Test
    fun `a note without the group parses to null, which is every note until the highlighting is mandatory`() {
        assertThat(NfseXmlParser.parseNfse(TestXml.nfse()).ibsCbs).isNull()
    }

    @Test
    fun `reads the incidence locality and the government purchase reduction`() {
        assertThat(ibsCbs.incidenceMunicipalityIbge).isEqualTo(3550308)
        assertThat(ibsCbs.incidenceMunicipality).isEqualTo("São Paulo")
        assertThat(ibsCbs.governmentPurchaseReduction).isEqualByComparingTo(BigDecimal("30.00"))
    }

    @Test
    fun `reads the base and the three spheres' rates`() {
        val calculation = ibsCbs.calculation

        assertThat(calculation.calculationBase).isEqualByComparingTo(BigDecimal("1000.00"))
        assertThat(calculation.reimbursements).isEqualByComparingTo(BigDecimal("50.00"))
        assertThat(calculation.state.rate).isEqualByComparingTo(BigDecimal("0.10"))
        assertThat(calculation.state.reduction).isEqualByComparingTo(BigDecimal("0.001"))
        assertThat(calculation.state.effectiveRate).isEqualByComparingTo(BigDecimal("0.09"))
        assertThat(calculation.municipal.rate).isEqualByComparingTo(BigDecimal("0.20"))
        assertThat(calculation.municipal.reduction).isEqualByComparingTo(BigDecimal("0.002"))
        assertThat(calculation.municipal.effectiveRate).isEqualByComparingTo(BigDecimal("0.18"))
        assertThat(calculation.federal.rate).isEqualByComparingTo(BigDecimal("0.90"))
        assertThat(calculation.federal.reduction).isEqualByComparingTo(BigDecimal("0.003"))
        assertThat(calculation.federal.effectiveRate).isEqualByComparingTo(BigDecimal("0.87"))
    }

    @Test
    fun `reads the IBS totals, including the deferred amounts and the presumed credit`() {
        val ibs = ibsCbs.totals.ibs

        assertThat(ibsCbs.totals.netAmountWithIbsCbs).isEqualByComparingTo(BigDecimal("1234.56"))
        assertThat(ibs.amount).isEqualByComparingTo(BigDecimal("2.70"))
        assertThat(ibs.presumedCredit?.percentage).isEqualByComparingTo(BigDecimal("1.50"))
        assertThat(ibs.presumedCredit?.amount).isEqualByComparingTo(BigDecimal("0.04"))
        assertThat(ibs.state.deferred).isEqualByComparingTo(BigDecimal("0.01"))
        assertThat(ibs.state.amount).isEqualByComparingTo(BigDecimal("0.90"))
        assertThat(ibs.municipal.deferred).isEqualByComparingTo(BigDecimal("0.02"))
        assertThat(ibs.municipal.amount).isEqualByComparingTo(BigDecimal("1.80"))
    }

    @Test
    fun `reads the CBS totals`() {
        val cbs = ibsCbs.totals.cbs

        assertThat(cbs.presumedCredit?.percentage).isEqualByComparingTo(BigDecimal("2.50"))
        assertThat(cbs.presumedCredit?.amount).isEqualByComparingTo(BigDecimal("0.22"))
        assertThat(cbs.deferred).isEqualByComparingTo(BigDecimal("0.03"))
        assertThat(cbs.amount).isEqualByComparingTo(BigDecimal("8.70"))
    }

    @Test
    fun `reads the regular taxation totals`() {
        val regular = requireNotNull(ibsCbs.totals.regularTaxation)

        assertThat(regular.stateRate).isEqualByComparingTo(BigDecimal("0.11"))
        assertThat(regular.stateAmount).isEqualByComparingTo(BigDecimal("1.10"))
        assertThat(regular.municipalRate).isEqualByComparingTo(BigDecimal("0.22"))
        assertThat(regular.municipalAmount).isEqualByComparingTo(BigDecimal("2.20"))
        assertThat(regular.cbsRate).isEqualByComparingTo(BigDecimal("0.99"))
        assertThat(regular.cbsAmount).isEqualByComparingTo(BigDecimal("9.90"))
    }

    @Test
    fun `reads the government purchase totals from their own group, not from the IBS totals`() {
        // `pIBSUF`, `vIBSUF`, `pIBSMun`, `vIBSMun`, `pCBS` and `vCBS` all exist elsewhere in the document with
        // different values — a descendant lookup would pick those up instead.
        val purchase = requireNotNull(ibsCbs.totals.governmentPurchase)

        assertThat(purchase.stateRate).isEqualByComparingTo(BigDecimal("0.12"))
        assertThat(purchase.stateAmount).isEqualByComparingTo(BigDecimal("1.20"))
        assertThat(purchase.municipalRate).isEqualByComparingTo(BigDecimal("0.23"))
        assertThat(purchase.municipalAmount).isEqualByComparingTo(BigDecimal("2.30"))
        assertThat(purchase.cbsRate).isEqualByComparingTo(BigDecimal("0.98"))
        assertThat(purchase.cbsAmount).isEqualByComparingTo(BigDecimal("9.80"))
    }

    @Test
    fun `a group the Sefin filled only partially reads as nulls instead of failing`() {
        val minimal = "<IBSCBS><cLocalidadeIncid>3550308</cLocalidadeIncid><valores><vBC>10.00</vBC></valores></IBSCBS>"

        val parsed = requireNotNull(NfseXmlParser.parseNfse(TestXml.nfse(ibsCbs = minimal)).ibsCbs)

        assertThat(parsed.calculation.calculationBase).isEqualByComparingTo(BigDecimal("10.00"))
        assertThat(parsed.calculation.state.rate).isNull()
        assertThat(parsed.totals.netAmountWithIbsCbs).isNull()
        assertThat(parsed.totals.ibs.amount).isNull()
        assertThat(parsed.totals.regularTaxation).isNull()
        assertThat(parsed.totals.governmentPurchase).isNull()
    }
}
