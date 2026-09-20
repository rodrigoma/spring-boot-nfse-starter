package io.github.rodrigoma.nfse.model.request

import io.github.rodrigoma.nfse.model.dps.AdditionalInfo
import io.github.rodrigoma.nfse.model.dps.Amounts
import io.github.rodrigoma.nfse.model.dps.Construction
import io.github.rodrigoma.nfse.model.dps.EventActivity
import io.github.rodrigoma.nfse.model.dps.ForeignTrade
import io.github.rodrigoma.nfse.model.dps.IbsCbs
import io.github.rodrigoma.nfse.model.dps.Person
import io.github.rodrigoma.nfse.model.dps.ServiceProvider
import io.github.rodrigoma.nfse.model.dps.Substitution
import java.time.LocalDate
import java.time.OffsetDateTime

/**
 * The service being invoiced. Only [nationalTaxCode] and [description] are mandatory; the place of provision
 * defaults to the emitter's municipality.
 */
data class ServiceRequest(
    /** `cTribNac` — 6 digits (LC 116 item + sub-item + national breakdown), e.g. `010701`. */
    val nationalTaxCode: String,
    /** `xDescServ`. */
    val description: String,
    /** `cTribMun` — 3 digits, when the municipality requires it. */
    val municipalTaxCode: String? = null,
    /** `cNBS` — 9 digits. */
    val nbsCode: String? = null,
    /** `cIntContrib` — your own code for the service. */
    val internalCode: String? = null,
    /** IBGE code of the municipality where the service was provided; defaults to the emitter's. */
    val municipalityIbge: Int? = null,
    /** ISO country code when the service was provided abroad (takes precedence over [municipalityIbge]). */
    val countryIso: String? = null,
    /** Free text for `infoCompl/xInfComp`; use [additionalInfo] for the other fields of that group. */
    val additionalText: String? = null,
    val additionalInfo: AdditionalInfo? = null,
    val foreignTrade: ForeignTrade? = null,
    val construction: Construction? = null,
    val eventActivity: EventActivity? = null,
)

/**
 * What changes from one invoice to the next. The provider comes from `nfse.emitter.*` unless [provider] overrides it.
 *
 * @property number `nDPS` — the application's sequential number; the library keeps no counter.
 * @property series Overrides `nfse.emitter.dps-series` for this DPS.
 * @property competenceDate `dCompet` — the date the service was provided / the competence month.
 * @property issuedAt `dhEmi`; defaults to now.
 * @property taker The service taker; foreign takers use [io.github.rodrigoma.nfse.model.dps.FederalId.Nif].
 * @property substitution When set, this DPS replaces the given NFS-e (the Sefin cancels it by substitution).
 * @property provider Overrides `nfse.emitter.*` for applications that issue for more than one CNPJ.
 * @property emitterMunicipalityIbge `cLocEmi` for that provider; defaults to `nfse.emitter.municipality-ibge`.
 */
data class DpsRequest(
    val number: Long,
    val series: Int? = null,
    val competenceDate: LocalDate,
    val issuedAt: OffsetDateTime? = null,
    val taker: Person? = null,
    val intermediary: Person? = null,
    val service: ServiceRequest,
    val amounts: Amounts,
    val substitution: Substitution? = null,
    val ibsCbs: IbsCbs? = null,
    val provider: ServiceProvider? = null,
    val emitterMunicipalityIbge: Int? = null,
)
