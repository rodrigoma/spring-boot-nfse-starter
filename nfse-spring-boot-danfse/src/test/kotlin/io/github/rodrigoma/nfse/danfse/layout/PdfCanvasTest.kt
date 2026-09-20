package io.github.rodrigoma.nfse.danfse.layout

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PdfCanvasTest {
    private fun <T> withCanvas(block: (PdfCanvas) -> T): T =
        PDDocument().use { document ->
            val page = PDPage(PDRectangle.A4)
            document.addPage(page)
            PdfCanvas(document, page).use(block)
        }

    @Test
    fun `wraps words, breaks words longer than the line and keeps paragraphs`() {
        withCanvas { canvas ->
            val lines = canvas.wrap("um dois três quatro cinco seis sete oito nove dez", 2f, 7f)
            assertThat(lines).hasSizeGreaterThan(2)
            assertThat(lines.all { canvas.width(it, 7f) <= 2f }).isTrue()

            val broken = canvas.wrap("x".repeat(100), 1f, 7f)
            assertThat(broken).hasSizeGreaterThan(3)
            assertThat(canvas.wrap("a\nb", 5f, 7f)).containsExactly("a", "b")
            assertThat(canvas.wrap("", 5f, 7f)).containsExactly("")
            assertThat(canvas.wrap("curta " + "y".repeat(60), 1.5f, 7f).size).isGreaterThan(2)
        }
    }

    @Test
    fun `replaces characters outside WinAnsi and measures text`() {
        withCanvas { canvas ->
            assertThat(canvas.width("ação", 7f, bold = true)).isGreaterThan(0f)
            canvas.text(1f, 1f, "tab\tand emoji 😀 and … ellipsis", PdfCanvas.TextStyle(7f))
            canvas.centeredText(5f, 2f, "centro", PdfCanvas.TextStyle(7f, bold = true))
            canvas.rect(1f, 1f, 2f, 2f, fill = 0.9f)
            canvas.pageBorder(0.2f, 0.2f, 20f, 29f)
            canvas.module(1f, 1f, 0.1f)
            canvas.watermark("TESTE")
        }
    }
}
