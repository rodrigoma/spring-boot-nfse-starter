package io.github.rodrigoma.nfse.client

import io.github.rodrigoma.nfse.autoconfigure.NfseEnvironment
import io.github.rodrigoma.nfse.model.dps.AdditionalInfo
import io.github.rodrigoma.nfse.model.dps.AddressLocation
import io.github.rodrigoma.nfse.model.dps.Amounts
import io.github.rodrigoma.nfse.model.dps.DpsEmitterType
import io.github.rodrigoma.nfse.model.dps.FederalId
import io.github.rodrigoma.nfse.model.dps.ServiceLocation
import io.github.rodrigoma.nfse.model.dps.SimplesNacionalOption
import io.github.rodrigoma.nfse.model.request.DpsRequest
import io.github.rodrigoma.nfse.model.request.ServiceRequest
import io.github.rodrigoma.nfse.support.TestDps
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class DpsAssemblerTest {
    private val clock = Clock.fixed(Instant.parse("2026-09-19T13:00:00Z"), ZoneOffset.ofHours(-3))
    private val properties = TestDps.properties(pfxPath = "/tmp/unused.pfx")
    private val assembler = DpsAssembler(properties, clock)

    private val request =
        DpsRequest(
            number = 7,
            competenceDate = TestDps.competence,
            taker = TestDps.taker,
            service = ServiceRequest(nationalTaxCode = "010701", description = "Consultoria", additionalText = "Obs"),
            amounts = Amounts(serviceAmount = BigDecimal("100")),
        )

    @Test
    fun `fills the provider, environment, series, version and location from the properties`() {
        val dps = assembler.assemble(request)

        assertThat(dps.environment).isEqualTo(NfseEnvironment.RESTRICTED_PRODUCTION)
        assertThat(dps.issuedAt.toInstant()).isEqualTo(clock.instant())
        assertThat(dps.issuedAt.offset).isEqualTo(ZoneOffset.ofHours(-3))
        assertThat(dps.applicationVersion).isEqualTo("test/1.0")
        assertThat(dps.series).isEqualTo(1)
        assertThat(dps.number).isEqualTo(7)
        assertThat(dps.emitterType).isEqualTo(DpsEmitterType.PROVIDER)
        assertThat(dps.emitterMunicipalityIbge).isEqualTo(TestDps.MUNICIPALITY)
        assertThat(dps.provider.id).isEqualTo(FederalId.Cnpj(TestDps.CNPJ))
        assertThat(dps.provider.name).isNull()
        assertThat(dps.provider.municipalRegistration).isEqualTo("12345")
        assertThat(dps.provider.address?.location).isEqualTo(AddressLocation.National(TestDps.MUNICIPALITY, "01310100"))
        assertThat(dps.provider.taxRegime.simplesNacional).isEqualTo(SimplesNacionalOption.NOT_OPTING)
        assertThat(dps.service.location).isEqualTo(ServiceLocation.Municipality(TestDps.MUNICIPALITY))
        assertThat(dps.service.additionalInfo).isEqualTo(AdditionalInfo(text = "Obs"))
        assertThat(dps.id.value).isEqualTo("DPS355030821234567800019500001000000000000007")
    }

    @Test
    fun `request values override the defaults`() {
        val overridden =
            request.copy(
                series = 5,
                issuedAt = TestDps.issuedAt,
                provider = TestDps.provider.copy(id = FederalId.Cnpj("98765432000198")),
                service =
                    request.service.copy(
                        municipalityIbge = 3304557,
                        additionalInfo = AdditionalInfo(purchaseOrder = "PO"),
                    ),
            )
        val dps = assembler.assemble(overridden)
        assertThat(dps.series).isEqualTo(5)
        assertThat(dps.issuedAt).isEqualTo(TestDps.issuedAt)
        assertThat(dps.provider.id).isEqualTo(FederalId.Cnpj("98765432000198"))
        assertThat(dps.service.location).isEqualTo(ServiceLocation.Municipality(3304557))
        assertThat(dps.service.additionalInfo?.purchaseOrder).isEqualTo("PO")

        val abroadService = request.service.copy(countryIso = "US", municipalityIbge = 1)
        val abroad = assembler.assemble(request.copy(service = abroadService))
        assertThat(abroad.service.location).isEqualTo(ServiceLocation.Country("US"))
    }
}
