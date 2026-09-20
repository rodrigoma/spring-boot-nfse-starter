package io.github.rodrigoma.nfse.xml

import io.github.rodrigoma.nfse.exception.NfseException
import io.github.rodrigoma.nfse.model.dps.Amounts
import io.github.rodrigoma.nfse.model.dps.BenefitReduction
import io.github.rodrigoma.nfse.model.dps.DeductionDocument
import io.github.rodrigoma.nfse.model.dps.DeductionDocumentReference
import io.github.rodrigoma.nfse.model.dps.DeductionType
import io.github.rodrigoma.nfse.model.dps.Deductions
import io.github.rodrigoma.nfse.model.dps.DpsEmitterType
import io.github.rodrigoma.nfse.model.dps.FederalId
import io.github.rodrigoma.nfse.model.dps.MunicipalBenefit
import io.github.rodrigoma.nfse.model.dps.MunicipalTax
import io.github.rodrigoma.nfse.model.dps.NoNifReason
import io.github.rodrigoma.nfse.model.dps.Person
import io.github.rodrigoma.nfse.model.dps.TakerEmissionReason
import io.github.rodrigoma.nfse.model.dps.Taxes
import io.github.rodrigoma.nfse.model.dps.TotalTaxes
import io.github.rodrigoma.nfse.support.TestDps
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset

class DpsXmlBuilderTest {
    private val builder = DpsXmlBuilder()

    @Test
    fun `minimal DPS matches the expected XML exactly`() {
        val xml = XmlSupport.serialize(builder.build(TestDps.minimal()))

        assertThat(xml).isEqualTo(
            """<?xml version="1.0" encoding="UTF-8"?>""" +
                """<DPS versao="1.01" xmlns="http://www.sped.fazenda.gov.br/nfse">""" +
                """<infDPS Id="DPS355030821234567800019500001000000000000001">""" +
                "<tpAmb>2</tpAmb><dhEmi>2026-09-19T10:00:00-03:00</dhEmi><verAplic>test/1.0</verAplic>" +
                "<serie>00001</serie><nDPS>1</nDPS><dCompet>2026-09-19</dCompet><tpEmit>1</tpEmit>" +
                "<cLocEmi>3550308</cLocEmi>" +
                "<prest><CNPJ>12345678000195</CNPJ><IM>12345</IM>" +
                "<regTrib><opSimpNac>1</opSimpNac><regEspTrib>0</regEspTrib></regTrib></prest>" +
                "<toma><CPF>12345678909</CPF><xNome>Fulano de Tal</xNome></toma>" +
                "<serv><locPrest><cLocPrestacao>3550308</cLocPrestacao></locPrest>" +
                "<cServ><cTribNac>010701</cTribNac><xDescServ>Consultoria</xDescServ></cServ></serv>" +
                "<valores><vServPrest><vServ>100.00</vServ></vServPrest>" +
                "<trib><tribMun><tribISSQN>1</tribISSQN><tpRetISSQN>1</tpRetISSQN><pAliq>2.00</pAliq></tribMun>" +
                "<totTrib><indTotTrib>0</indTotTrib></totTrib></trib></valores>" +
                "</infDPS></DPS>",
        )
    }

    @Test
    fun `complete DPS is valid against the XSD and writes every group`() {
        val document = builder.build(TestDps.complete())
        val root = document.documentElement
        val xml = XmlSupport.serialize(document)

        assertThat(root.firstText("chSubstda")).isEqualTo("1".repeat(50))
        assertThat(root.firstText("cMotivo")).isEqualTo("99")
        assertThat(root.firstText("CAEPF")).isEqualTo("12345678901234")
        assertThat(root.firstText("regApTribSN")).isEqualTo("1")
        assertThat(root.firstText("NIF")).isEqualTo("GB123456789")
        assertThat(root.firstText("cPais")).isEqualTo("GB")
        assertThat(root.firstText("cPaisPrestacao")).isEqualTo("GB")
        assertThat(root.firstText("xDescServ")).isEqualTo("Consultoria em TI\nLinha 2")
        assertThat(root.firstText("mdic")).isEqualTo("1")
        assertThat(root.firstText("vServMoeda")).isEqualTo("1500.50")
        assertThat(root.firstText("inscImobFisc")).isEqualTo("IMOB-1")
        assertThat(root.firstText("idAtvEvt")).isEqualTo("EVT-1")
        assertThat(root.firstText("xItemPed")).isEqualTo("1")
        assertThat(root.firstText("vReceb")).isEqualTo("900.00")
        assertThat(root.firstText("vDescCond")).isEqualTo("25.00")
        assertThat(root.firstText("cVerifNFSeMun")).isEqualTo("ABC123")
        assertThat(root.firstText("xDescOutDed")).isEqualTo("Outra dedução")
        assertThat(root.firstText("nProcesso")).isEqualTo("123456".padStart(30, '0'))
        assertThat(root.firstText("pRedBCBM")).isEqualTo("10.00")
        assertThat(root.firstText("tpRetISSQN")).isEqualTo("2")
        assertThat(root.firstText("pAliqPis")).isEqualTo("0.65")
        assertThat(root.firstText("vRetCSLL")).isEqualTo("10.00")
        assertThat(root.firstText("pTotTribFed")).isEqualTo("13.45")
        assertThat(root.firstText("indFinal")).isEqualTo("0")
        assertThat(root.firstText("refNFSe")).isEqualTo("3".repeat(50))
        assertThat(root.firstText("chaveDFe")).isEqualTo("4".repeat(44))
        assertThat(root.firstText("pDifCBS")).isEqualTo("3.00")
        assertThat(xml).contains("<interm><CNPJ>98765432000198</CNPJ><IM>777</IM><xNome>Intermediária SA</xNome>")
        assertThat(xml).doesNotContain("ds:").doesNotContain("xmlns:")
    }

    @Test
    fun `writes the remaining choices - amount deduction, benefit amount, total amounts, no NIF, taker emitter`() {
        val dps =
            TestDps.minimal().copy(
                emitterType = DpsEmitterType.TAKER,
                takerEmissionReason = TakerEmissionReason.PROVIDER_NFSE_REJECTED,
                rejectedNfseAccessKey = "5".repeat(50),
                provider = TestDps.provider.copy(id = FederalId.NoNif(NoNifReason.EXEMPT), name = "Foreign Provider"),
                taker = Person(FederalId.Cnpj(TestDps.CNPJ), name = "Nós"),
                amounts =
                    Amounts(
                        serviceAmount = BigDecimal("10"),
                        deductions = Deductions.Amount(BigDecimal("1")),
                        taxes =
                            Taxes(
                                municipal =
                                    MunicipalTax(
                                        municipalBenefit =
                                            MunicipalBenefit("2".repeat(14), BenefitReduction.Amount(BigDecimal("3"))),
                                    ),
                                total = TotalTaxes.Amounts(BigDecimal("1"), BigDecimal("2"), BigDecimal("3")),
                            ),
                    ),
            )
        val root = builder.build(dps).documentElement
        assertThat(root.firstText("tpEmit")).isEqualTo("2")
        assertThat(root.firstText("cMotivoEmisTI")).isEqualTo("4")
        assertThat(root.firstText("chNFSeRej")).isEqualTo("5".repeat(50))
        assertThat(root.firstText("cNaoNIF")).isEqualTo("1")
        assertThat(root.firstText("vDR")).isEqualTo("1.00")
        assertThat(root.firstText("vRedBCBM")).isEqualTo("3.00")
        assertThat(root.firstText("vTotTribMun")).isEqualTo("3.00")
        assertThat(dps.id.value).startsWith("DPS3550308212345678000195")
    }

    @Test
    fun `writes percentage deductions, simples nacional total and remaining document references`() {
        val dps =
            TestDps.minimal().copy(
                amounts =
                    Amounts(
                        serviceAmount = BigDecimal("10"),
                        deductions = Deductions.Percentage(BigDecimal("5")),
                        taxes = Taxes(total = TotalTaxes.SimplesNacionalPercentage(BigDecimal("4.5"))),
                    ),
            )
        assertThat(builder.build(dps).documentElement.firstText("pDR")).isEqualTo("5.00")
        assertThat(builder.build(dps).documentElement.firstText("pTotTribSN")).isEqualTo("4.50")

        val references =
            listOf(
                DeductionDocumentReference.NfeKey("7".repeat(44)) to "chNFe",
                DeductionDocumentReference.NfNfs("1234567", "000000000000055", "A1") to "serieNFS",
                DeductionDocumentReference.FiscalDocumentNumber("F-1") to "nDocFisc",
                DeductionDocumentReference.DocumentNumber("D-1") to "nDoc",
            )
        references.forEach { (reference, element) ->
            val withDocuments =
                dps.copy(
                    amounts =
                        dps.amounts.copy(
                            deductions =
                                Deductions.Documents(
                                    listOf(
                                        DeductionDocument(
                                            reference,
                                            DeductionType.SERVICES,
                                            issueDate = LocalDate.of(2026, 1, 1),
                                            deductibleAmount = BigDecimal.ONE,
                                            deductedAmount = BigDecimal.ONE,
                                        ),
                                    ),
                                ),
                        ),
                )
            assertThat(builder.build(withDocuments).documentElement.firstText(element)).isNotNull()
        }
    }

    @Test
    fun `formats the emission date with a numeric offset even for UTC`() {
        val dps = TestDps.minimal().copy(issuedAt = OffsetDateTime.of(2026, 1, 2, 3, 4, 5, 999, ZoneOffset.UTC))
        assertThat(builder.build(dps).documentElement.firstText("dhEmi")).isEqualTo("2026-01-02T03:04:05+00:00")
    }

    @Test
    fun `refuses a DPS that does not pass the schema`() {
        val dps =
            TestDps.minimal().copy(
                service =
                    TestDps.minimal().service.copy(
                        code =
                            TestDps
                                .minimal()
                                .service.code
                                .copy(nationalTaxCode = "1"),
                    ),
            )
        assertThatThrownBy { builder.build(dps) }
            .isInstanceOf(NfseException.Validation::class.java)
            .satisfies({ assertThat((it as NfseException.Validation).errors).isNotEmpty() })
    }

    @Test
    fun `rejects negative amounts before touching the schema`() {
        val dps = TestDps.minimal().copy(amounts = Amounts(serviceAmount = BigDecimal("-1")))
        assertThatThrownBy { builder.build(dps) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("negative")
    }
}
