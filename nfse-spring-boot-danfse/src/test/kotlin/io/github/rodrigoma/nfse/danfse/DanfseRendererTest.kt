package io.github.rodrigoma.nfse.danfse

import io.github.rodrigoma.nfse.model.event.NfseEvent
import io.github.rodrigoma.nfse.xml.NfseXmlParser
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.text.PDFTextStripper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class DanfseRendererTest {
    private val renderer = DanfseRenderer()

    /** The page text with line breaks folded into spaces, so wrapped values can be asserted as one string. */
    private fun text(pdf: ByteArray): String = rawText(pdf).replace('\n', ' ')

    /** The page text without any whitespace: the rotated watermark is extracted in pieces. */
    private fun watermarkText(pdf: ByteArray): String = rawText(pdf).replace(Regex("\\s"), "")

    private fun rawText(pdf: ByteArray): String = Loader.loadPDF(pdf).use { PDFTextStripper().getText(it) }

    private fun document(pdf: ByteArray): PDDocument = Loader.loadPDF(pdf)

    @Test
    fun `renders a minimal note on one A4 page with the identification, QR note and dashes for empty fields`() {
        val pdf = renderer.render(Fixtures.minimal)

        document(pdf).use {
            assertThat(it.numberOfPages).isEqualTo(1)
            assertThat(it.getPage(0).mediaBox.width).isCloseTo(
                595.28f,
                org.assertj.core.data.Offset
                    .offset(0.1f),
            )
            assertThat(
                it.documentInformation.title,
            ).isEqualTo("DANFSe 1 - 35488072212345678000195000000000000126092195420230")
        }
        val text = text(pdf)
        assertThat(text)
            .contains("DANFSe v2.0")
            .contains("Documento Auxiliar da NFS-e")
            .contains("NFS-e SEM VALIDADE JURÍDICA")
            .contains("Município: São Caetano do Sul / SP")
            .contains("Tipo de Ambiente: Produção Restrita (Homologação)")
            .contains("35488072212345678000195000000000000126092195420230")
            .contains("20/09/2026 13:07:42")
            .contains("Prestador")
            .contains("NFS-e Gerada")
            .contains("12.345.678/0001-95")
            .contains("EMPRESA DE TESTE LTDA")
            .contains("Rua Manoel Coelho, 600, Centro")
            .contains("3548807 / 09510-101")
            .contains("Optante - Microempresa ou Empresa de P…")
            .contains("123.456.789-09")
            .contains("São Paulo / SP")
            .contains("DESTINATÁRIO DA OPERAÇÃO NÃO IDENTIFICADO NA NFS-e")
            .contains("INTERMEDIÁRIO DA OPERAÇÃO NÃO IDENTIFICADO NA NFS-e")
            .contains("01.07.01")
            .contains("São Caetano do Sul / SP / BR")
            .contains("Operação tributável")
            .contains("R$ 1.500,00")
            .contains("2,00 %")
            .contains("R$ 30,00")
            .contains("Não Retido")
            .contains(
                "Totais Aproximados dos Tributos cfe. Lei nº 12.741/2012: " +
                    "Federais: -; Estaduais: -; Municipais: 6,00 %",
            ).contains("A autenticidade desta NFS-e pode ser verificada")
            .doesNotContain("DATA CIENTIFICAÇÃO")
            .doesNotContain("CANCELADA")
    }

    @Test
    fun `renders a complete note with every block, the composed additional information and the stub`() {
        val pdf = DanfseRenderer(DanfseOptions(stub = true)).render(Fixtures.complete)

        document(pdf).use { assertThat(it.numberOfPages).isEqualTo(1) }
        val text = text(pdf)
        assertThat(text)
            .doesNotContain("SEM VALIDADE JURÍDICA")
            .contains("Tipo de Ambiente: Produção")
            .contains("12.ABC.345/01DE-35")
            .contains("Não Optante")
            .contains("GB123456789")
            .contains("London / England / Reino Unido")
            .contains("SW1A 1AA")
            .contains("11.222.333/0001-81")
            .contains("DESTINATÁRIA DA OBRA LTDA")
            .contains("98.765.432/0001-98")
            .contains("07.01.01 / 123")
            .contains("1.1501.10.00")
            .contains("Rio de Janeiro / RJ / BR")
            .contains("Serviços de engenharia consultiva - código municipal 123")
            .contains("Projeto executivo de estrutura metálica")
            .contains("Exigibilidade Suspensa por Decisão Judicial")
            .contains("000000000000000000000000123456")
            .contains("Redução da BC em %")
            .contains("R$ 50,00")
            .contains("R$ 300,00")
            .contains("R$ 9.850,00")
            .contains("5,00 %")
            .contains("Retido pelo Tomador")
            .contains("R$ 492,50")
            .contains("R$ 150,00")
            .contains("R$ 110,00")
            .contains("R$ 465,00")
            .contains("PIS/COFINS Retidos")
            .contains("000 / 000001")
            .contains("010101 / 3304557 / Rio de Janeiro / RJ")
    }

    @Test
    fun `renders the IBS CBS block, the totals and the composed additional information of a complete note`() {
        val text = text(DanfseRenderer(DanfseOptions(stub = true)).render(Fixtures.complete))
        assertThat(text)
            .contains("R$ 1.107,50")
            .contains("R$ 9.187,50")
            .contains("0,00 % / 0,00 % / 0,00 %")
            .contains("0,10 % / 0,00 %")
            .contains("R$ 9,19")
            .contains("R$ 82,69")
            .contains("R$ 91,88")
            .contains("R$ 9.834,38")
            .contains("R$ 10.000,00")
            .contains("R$ 25,00")
            .contains("R$ 1.207,50")
            .contains("R$ 8.742,50")
            .contains("Inf. Cont.: Pagamento em 30 dias.")
            .contains("NFS-e Subst.: 35503081212ABC34501DE350000000004562610123456780")
            .contains("Doc. Ref.: Contrato 2026/0042")
            .contains("Cod. Obra: OBRA-2026-17")
            .contains("Insc. Imob.: IMOB-9981")
            .contains("Doc. Tec.: ART-SP-2026-1234")
            .contains("Núm. Ped.: PO-7788")
            .contains("Item Ped.: 1, 2")
            .contains("Inf. A. T. Mun.: Contribuinte em situação regular")
            .contains("Federais: R$ 1.345,00; Estaduais: R$ 0,00; Municipais: R$ 500,00")
            .contains("DATA CIENTIFICAÇÃO")
            .contains("457 / 35503081212ABC34501DE350000000004572610123456789")
    }

    @Test
    fun `draws the watermark for cancelled and substituted notes`() {
        val cancelled = renderer.render(Fixtures.minimal, NoteStatus.CANCELLED)
        val substituted = renderer.render(Fixtures.complete, NoteStatus.SUBSTITUTED)
        assertThat(watermarkText(cancelled)).contains("CANCELADA")
        assertThat(watermarkText(substituted)).contains("SUBSTITUÍDA")
    }

    @Test
    fun `reads the status from the events when rendering an Nfse`() {
        val nfse = NfseXmlParser.parseNfse(Fixtures.minimal)
        val cancellation =
            NfseEvent(
                id = "EVT",
                typeCode = "101101",
                sequence = 1,
                processedAt = null,
                accessKey = nfse.accessKey,
                xml = "",
            )
        val substitution = cancellation.copy(typeCode = "105102")
        val manifestation = cancellation.copy(typeCode = "203202")

        assertThat(NoteStatus.of(emptyList())).isEqualTo(NoteStatus.ACTIVE)
        assertThat(NoteStatus.of(listOf(manifestation))).isEqualTo(NoteStatus.ACTIVE)
        assertThat(NoteStatus.of(listOf(cancellation))).isEqualTo(NoteStatus.CANCELLED)
        assertThat(NoteStatus.of(listOf(cancellation.copy(typeCode = "305101")))).isEqualTo(NoteStatus.CANCELLED)
        assertThat(NoteStatus.of(listOf(cancellation, substitution))).isEqualTo(NoteStatus.SUBSTITUTED)
        assertThat(watermarkText(renderer.render(nfse, listOf(cancellation)))).contains("CANCELADA")
        assertThat(watermarkText(renderer.render(nfse, emptyList()))).doesNotContain("CANCELADA")
    }

    @Test
    fun `fits an oversized description and additional information on the single page`() {
        val huge =
            Fixtures.complete
                .replace("Projeto executivo", "X".repeat(50) + " " + "palavra ".repeat(900) + "Projeto executivo")
                .replace("Pagamento em 30 dias.", "info ".repeat(800))
        val pdf = DanfseRenderer(DanfseOptions(stub = true)).render(huge, NoteStatus.SUBSTITUTED)
        document(pdf).use { assertThat(it.numberOfPages).isEqualTo(1) }
        assertThat(text(pdf)).contains("…").contains("Totais Aproximados dos Tributos")
    }
}
