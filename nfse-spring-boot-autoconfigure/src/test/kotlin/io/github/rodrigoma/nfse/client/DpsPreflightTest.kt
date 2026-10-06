package io.github.rodrigoma.nfse.client

import io.github.rodrigoma.nfse.exception.NfseException
import io.github.rodrigoma.nfse.model.dps.SimplesNacionalAssessment
import io.github.rodrigoma.nfse.model.dps.SimplesNacionalOption
import io.github.rodrigoma.nfse.model.dps.TaxRegime
import io.github.rodrigoma.nfse.model.dps.TotalTaxes
import io.github.rodrigoma.nfse.support.TestDps
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class DpsPreflightTest {
    private val dps = TestDps.minimal()

    private fun asMeEpp() =
        dps.copy(
            provider =
                dps.provider.copy(
                    taxRegime =
                        TaxRegime(
                            simplesNacional = SimplesNacionalOption.ME_EPP,
                            simplesNacionalAssessment = SimplesNacionalAssessment.SIMPLES_NACIONAL,
                        ),
                ),
        )

    @Test
    fun `refuses the default total taxes for a ME-EPP provider, which the Sefin rejects with E0712`() {
        // `totTrib` is a mandatory choice in the XSD and ME/EPP may not pick `indTotTrib`, which is the default —
        // so without this check every Simples Nacional emitter would learn the rule from a rejection.
        assertThatThrownBy { DpsPreflight.check(asMeEpp()) }
            .isInstanceOf(NfseException.Validation::class.java)
            .hasMessageContaining("E0712")
            .hasMessageContaining("SimplesNacionalPercentage")
    }

    @Test
    fun `accepts a ME-EPP provider that informs the Simples Nacional percentage`() {
        val withPercentage =
            asMeEpp().let {
                it.copy(
                    amounts =
                        it.amounts.copy(
                            taxes =
                                it.amounts.taxes.copy(
                                    total = TotalTaxes.SimplesNacionalPercentage(BigDecimal("6.00")),
                                ),
                        ),
                )
            }

        assertThatCode { DpsPreflight.check(withPercentage) }.doesNotThrowAnyException()
    }

    @Test
    fun `leaves a provider outside the Simples alone`() {
        assertThat(dps.provider.taxRegime.simplesNacional).isEqualTo(SimplesNacionalOption.NOT_OPTING)
        assertThat(dps.amounts.taxes.total).isEqualTo(TotalTaxes.NotInformed)

        assertThatCode { DpsPreflight.check(dps) }.doesNotThrowAnyException()
    }
}
