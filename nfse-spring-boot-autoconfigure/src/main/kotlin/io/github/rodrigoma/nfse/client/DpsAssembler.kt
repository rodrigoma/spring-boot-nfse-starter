package io.github.rodrigoma.nfse.client

import io.github.rodrigoma.nfse.autoconfigure.NfseProperties
import io.github.rodrigoma.nfse.model.dps.AdditionalInfo
import io.github.rodrigoma.nfse.model.dps.Dps
import io.github.rodrigoma.nfse.model.dps.DpsEmitterType
import io.github.rodrigoma.nfse.model.dps.ServiceCode
import io.github.rodrigoma.nfse.model.dps.ServiceInfo
import io.github.rodrigoma.nfse.model.dps.ServiceLocation
import io.github.rodrigoma.nfse.model.request.DpsRequest
import java.time.Clock
import java.time.OffsetDateTime

/** Merges `nfse.emitter.*` with a [DpsRequest] into the full [Dps] (issued by the provider, `tpEmit = 1`). */
class DpsAssembler(
    private val properties: NfseProperties,
    private val clock: Clock = Clock.systemDefaultZone(),
) {
    fun assemble(request: DpsRequest): Dps {
        val emitterMunicipality = requireNotNull(properties.emitter.municipalityIbge)
        val service = request.service
        return Dps(
            environment = properties.environment,
            issuedAt = request.issuedAt ?: OffsetDateTime.now(clock),
            applicationVersion = properties.resolvedApplicationVersion(),
            series = request.series ?: properties.emitter.dpsSeries,
            number = request.number,
            competenceDate = request.competenceDate,
            emitterType = DpsEmitterType.PROVIDER,
            emitterMunicipalityIbge = emitterMunicipality,
            substitution = request.substitution,
            provider = request.provider ?: properties.emitterProvider(),
            taker = request.taker,
            intermediary = request.intermediary,
            service =
                ServiceInfo(
                    location =
                        when {
                            service.countryIso != null -> ServiceLocation.Country(service.countryIso)
                            else -> ServiceLocation.Municipality(service.municipalityIbge ?: emitterMunicipality)
                        },
                    code =
                        ServiceCode(
                            nationalTaxCode = service.nationalTaxCode,
                            municipalTaxCode = service.municipalTaxCode,
                            description = service.description,
                            nbsCode = service.nbsCode,
                            internalCode = service.internalCode,
                        ),
                    foreignTrade = service.foreignTrade,
                    construction = service.construction,
                    eventActivity = service.eventActivity,
                    additionalInfo =
                        service.additionalInfo ?: service.additionalText?.let { AdditionalInfo(text = it) },
                ),
            amounts = request.amounts,
            ibsCbs = request.ibsCbs,
        )
    }
}
