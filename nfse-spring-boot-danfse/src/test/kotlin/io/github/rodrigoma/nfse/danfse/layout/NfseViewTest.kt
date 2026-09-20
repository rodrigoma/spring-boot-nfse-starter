package io.github.rodrigoma.nfse.danfse.layout

import io.github.rodrigoma.nfse.danfse.Fixtures
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class NfseViewTest {
    private val minimal = NfseView(Fixtures.minimal)
    private val complete = NfseView(Fixtures.complete)

    @Test
    fun `completes the provider with the emit group of the note`() {
        val provider = minimal.provider!!
        assertThat(provider.name).isEqualTo("EMPRESA DE TESTE LTDA")
        assertThat(provider.municipality).isEqualTo("São Caetano do Sul / SP")
        assertThat(provider.ibgeCep).isEqualTo("3548807 / 09510-101")
        assertThat(provider.phone).isEqualTo("11999998888")
        assertThat(provider.email).isEqualTo("financeiro@empresa.com.br")
        assertThat(minimal.recipient).isNull()
        assertThat(minimal.recipientIsTaker).isFalse()
        assertThat(minimal.intermediary).isNull()
    }

    @Test
    fun `derives the sums and rules of NT 008 for the taxation blocks`() {
        assertThat(complete.socialContributions).isEqualTo("R$ 465,00")
        assertThat(complete.pis).isEqualTo("R$ 0,00")
        assertThat(complete.cofins).isEqualTo("R$ 0,00")
        assertThat(complete.deductions).isEqualTo("R$ 300,00")
        assertThat(complete.benefitCalculation).isEqualTo("R$ 50,00")
        assertThat(complete.baseExclusions).isEqualTo("R$ 1.107,50")
        assertThat(complete.ibsCbsTotal).isEqualTo("R$ 91,88")
        assertThat(complete.printFederalTaxation).isTrue()
        assertThat(complete.recipientIsTaker).isFalse()
        assertThat(complete.recipient?.document).isEqualTo("11.222.333/0001-81")
        assertThat(complete.taxCodeDescription).startsWith("Serviços de engenharia consultiva")
        assertThat(complete.serviceLocation).isEqualTo("Rio de Janeiro / RJ / BR")
        assertThat(complete.issqnIncidence).isEqualTo("Rio de Janeiro / RJ / BR")
    }

    @Test
    fun `handles the choices the fixtures do not cover`() {
        val variant =
            Fixtures.minimal
                .replace("<cLocPrestacao>3548807</cLocPrestacao>", "<cPaisPrestacao>US</cPaisPrestacao>")
                .replace("<tribISSQN>1</tribISSQN>", "<tribISSQN>3</tribISSQN><cPaisResult>US</cPaisResult>")
                .replace("<xLocIncid>São Caetano do Sul</xLocIncid>", "")
                .replace("<cLocIncid>3548807</cLocIncid>", "")
                .replace(
                    "<pTotTribSN>6.00</pTotTribSN>",
                    "<pTotTrib><pTotTribFed>10.00</pTotTribFed><pTotTribEst>0</pTotTribEst>" +
                        "<pTotTribMun>5.00</pTotTribMun></pTotTrib>",
                ).replace("<dCompet>2026-09-20</dCompet>", "<dCompet>2027-01-10</dCompet>")
                .replace("<CPF>12345678909</CPF>", "<cNaoNIF>1</cNaoNIF>")
                .replace("<tpEmit>1</tpEmit>", "<tpEmit>1</tpEmit>")
        val view = NfseView(variant)
        assertThat(view.serviceLocation).isEqualTo("Estados Unidos")
        assertThat(view.subjectToIssqn).isFalse()
        assertThat(view.issqnIncidence).isEqualTo("Estados Unidos")
        assertThat(view.printFederalTaxation).isFalse()
        assertThat(view.taker?.document).isEqualTo("NIF não informado (1)")
        assertThat(view.additionalInformation).contains("Federais: 10,00 %; Estaduais: 0,00 %; Municipais: 5,00 %")

        val noTotals =
            NfseView(
                Fixtures.minimal.replace(
                    "<totTrib><pTotTribSN>6.00</pTotTribSN></totTrib>",
                    "<totTrib><indTotTrib>0</indTotTrib></totTrib>",
                ),
            )
        assertThat(noTotals.additionalInformation).endsWith("Federais: -; Estaduais: -; Municipais: -")

        val takerAsRecipient =
            NfseView(
                Fixtures.complete
                    .replace(
                        "<indDest>1</indDest>",
                        "<indDest>0</indDest>",
                    ).replace(Regex("<dest>.*?</dest>"), ""),
            )
        assertThat(takerAsRecipient.recipient).isNull()
        assertThat(takerAsRecipient.recipientIsTaker).isTrue()
    }

    @Test
    fun `refuses documents that are not an NFS-e`() {
        assertThatThrownBy { NfseView("<x/>") }.hasMessageContaining("infNFSe")
        assertThatThrownBy {
            NfseView("""<NFSe xmlns="http://www.sped.fazenda.gov.br/nfse"><infNFSe/></NFSe>""")
        }.hasMessageContaining("infDPS")
    }
}
