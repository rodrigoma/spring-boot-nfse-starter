package io.github.rodrigoma.nfse.model.response

import java.math.BigDecimal

/**
 * `IBSCBS` of the generated NFS-e (`TCRTCIBSCBS`) — the tax-reform values **computed by the Sefin** from the
 * `IBSCBS` group of the DPS, which is why nothing here is informed by the application.
 *
 * `null` on [Nfse.ibsCbs] when the note carries no group: the standard tolerates its omission until 2026-12-31
 * and the highlighting becomes mandatory in waves (see `docs/standards-watch.md`).
 *
 * @property incidenceMunicipalityIbge `cLocalidadeIncid` — where the IBS/CBS is due, not necessarily the emitter's.
 * @property incidenceMunicipality `xLocalidadeIncid`.
 * @property governmentPurchaseReduction `pRedutor` — rate reduction of a government purchase.
 */
data class NfseIbsCbs(
    val incidenceMunicipalityIbge: Int? = null,
    val incidenceMunicipality: String? = null,
    val governmentPurchaseReduction: BigDecimal? = null,
    val calculation: IbsCbsCalculation = IbsCbsCalculation(),
    val totals: IbsCbsTotals = IbsCbsTotals(),
)

/**
 * `valores` (`TCRTCValoresIBSCBS`) — the base and the rates the three spheres applied.
 *
 * @property calculationBase `vBC`, after the exclusions and before the reductions.
 * @property reimbursements `vCalcReeRepRes` — reimbursed/transferred amounts kept out of the base.
 */
data class IbsCbsCalculation(
    val calculationBase: BigDecimal? = null,
    val reimbursements: BigDecimal? = null,
    val state: IbsStateRates = IbsStateRates(),
    val municipal: IbsMunicipalRates = IbsMunicipalRates(),
    val federal: CbsRates = CbsRates(),
)

/** `uf` (`TCRTCValoresIBSCBSUF`) — `pIBSUF`, `pRedAliqUF`, `pAliqEfetUF`. */
data class IbsStateRates(
    val rate: BigDecimal? = null,
    val reduction: BigDecimal? = null,
    val effectiveRate: BigDecimal? = null,
)

/** `mun` (`TCRTCValoresIBSCBSMun`) — `pIBSMun`, `pRedAliqMun`, `pAliqEfetMun`. */
data class IbsMunicipalRates(
    val rate: BigDecimal? = null,
    val reduction: BigDecimal? = null,
    val effectiveRate: BigDecimal? = null,
)

/** `fed` (`TCRTCValoresIBSCBSFed`) — `pCBS`, `pRedAliqCBS`, `pAliqEfetCBS`. */
data class CbsRates(
    val rate: BigDecimal? = null,
    val reduction: BigDecimal? = null,
    val effectiveRate: BigDecimal? = null,
)

/**
 * `totCIBS` (`TCRTCTotalCIBS`) — the totals an application posts to its ledger.
 *
 * @property netAmountWithIbsCbs `vTotNF` — IBS and CBS are charged on top (`por fora`), so this is `vLiq` plus
 * them from 2027 on and equal to `vLiq` during 2026.
 * @property regularTaxation `gTribRegular` — what the operation would have paid under the regular regime.
 * @property governmentPurchase `gTribCompraGov` — present only in a government purchase.
 */
data class IbsCbsTotals(
    val netAmountWithIbsCbs: BigDecimal? = null,
    val ibs: IbsTotals = IbsTotals(),
    val cbs: CbsTotals = CbsTotals(),
    val regularTaxation: RegularTaxationTotals? = null,
    val governmentPurchase: GovernmentPurchaseTotals? = null,
)

/** `gIBS` (`TCRTCTotalIBS`) — `vIBSTot` is the state and municipal shares together. */
data class IbsTotals(
    val amount: BigDecimal? = null,
    val presumedCredit: PresumedCredit? = null,
    val state: IbsSphereTotals = IbsSphereTotals(),
    val municipal: IbsSphereTotals = IbsSphereTotals(),
)

/**
 * `gIBSUFTot` / `gIBSMunTot` (`TCRTCTotalIBSUF`, `TCRTCTotalIBSMun`) — one sphere's share.
 *
 * @property deferred `vDifUF` / `vDifMun`.
 * @property amount `vIBSUF` / `vIBSMun`.
 */
data class IbsSphereTotals(
    val deferred: BigDecimal? = null,
    val amount: BigDecimal? = null,
)

/** `gCBS` (`TCRTCTotalCBS`) — `vDifCBS` deferred, `vCBS` due. */
data class CbsTotals(
    val presumedCredit: PresumedCredit? = null,
    val deferred: BigDecimal? = null,
    val amount: BigDecimal? = null,
)

/** `gIBSCredPres` / `gCBSCredPres` — the presumed credit granted, as a percentage and an amount. */
data class PresumedCredit(
    val percentage: BigDecimal? = null,
    val amount: BigDecimal? = null,
)

/** `gTribRegular` (`TCRTCTotalTribRegular`) — effective rate and amount per sphere under the regular regime. */
data class RegularTaxationTotals(
    val stateRate: BigDecimal? = null,
    val stateAmount: BigDecimal? = null,
    val municipalRate: BigDecimal? = null,
    val municipalAmount: BigDecimal? = null,
    val cbsRate: BigDecimal? = null,
    val cbsAmount: BigDecimal? = null,
)

/** `gTribCompraGov` (`TCRTCTotalTribCompraGov`) — the rates and amounts of a government purchase. */
data class GovernmentPurchaseTotals(
    val stateRate: BigDecimal? = null,
    val stateAmount: BigDecimal? = null,
    val municipalRate: BigDecimal? = null,
    val municipalAmount: BigDecimal? = null,
    val cbsRate: BigDecimal? = null,
    val cbsAmount: BigDecimal? = null,
)
