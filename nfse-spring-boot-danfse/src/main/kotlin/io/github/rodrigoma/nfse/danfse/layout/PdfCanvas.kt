package io.github.rodrigoma.nfse.danfse.layout

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDFont
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import org.apache.pdfbox.pdmodel.font.encoding.GlyphList
import org.apache.pdfbox.pdmodel.font.encoding.WinAnsiEncoding
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject
import org.apache.pdfbox.util.Matrix
import java.awt.Color

/**
 * Thin drawing layer over PDFBox in the coordinate system of NT 008: centimetres from the top-left corner of an A4
 * page. Fonts are the standard Helvetica pair — metric-compatible with the Arial the NT names; text is limited to
 * the WinAnsi repertoire (accented Portuguese is fine), other characters print as `?`.
 */
internal class PdfCanvas(
    document: PDDocument,
    page: PDPage,
) : AutoCloseable {
    private val stream = PDPageContentStream(document, page)
    private val pageHeight = page.mediaBox.height
    private val regular: PDFont = PDType1Font(Standard14Fonts.FontName.HELVETICA)
    private val bold: PDFont = PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD)

    /** The 1 pt page border. */
    fun pageBorder(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
    ) {
        stream.setLineWidth(BORDER_LINE)
        stream.addRect(x.pt, (y + height).fromTop, width.pt, height.pt)
        stream.stroke()
    }

    fun rect(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        fill: Float? = null,
    ) {
        stream.setLineWidth(THIN_LINE)
        stream.addRect(x.pt, (y + height).fromTop, width.pt, height.pt)
        if (fill != null) {
            stream.setNonStrokingColor(fill, fill, fill)
            stream.fillAndStroke()
            stream.setNonStrokingColor(Color.BLACK)
        } else {
            stream.stroke()
        }
    }

    /** Draws [text] with its baseline at [y] (from the top) and left edge at [x]. */
    fun text(
        x: Float,
        y: Float,
        text: String,
        style: TextStyle,
    ) {
        stream.beginText()
        stream.setFont(font(style.bold), style.size)
        stream.setNonStrokingColor(style.color)
        stream.newLineAtOffset(x.pt, y.fromTop)
        stream.showText(sanitize(text))
        stream.endText()
        stream.setNonStrokingColor(Color.BLACK)
    }

    fun centeredText(
        centerX: Float,
        y: Float,
        text: String,
        style: TextStyle,
    ) = text(centerX - width(text, style.size, style.bold) / 2, y, text, style)

    /** Width of [text] in centimetres. */
    fun width(
        text: String,
        size: Float,
        bold: Boolean = false,
    ): Float = font(bold).getStringWidth(sanitize(text)) / THOUSAND * size / POINTS_PER_CM

    /** Greedy word wrap into lines no wider than [maxWidth] centimetres; long words are split. */
    fun wrap(
        text: String,
        maxWidth: Float,
        size: Float,
        bold: Boolean = false,
    ): List<String> {
        val lines = mutableListOf<String>()
        text.split('\n').forEach { paragraph ->
            var line = ""
            paragraph.split(' ').filter { it.isNotEmpty() }.forEach { word ->
                line = appendWord(lines, line, word, maxWidth, size, bold)
            }
            lines += line
        }
        return lines
    }

    /** Adds [word] to [line], flushing full lines into [lines]; returns the new open line. */
    @Suppress("LongParameterList")
    private fun appendWord(
        lines: MutableList<String>,
        line: String,
        word: String,
        maxWidth: Float,
        size: Float,
        bold: Boolean,
    ): String {
        val candidate = if (line.isEmpty()) word else "$line $word"
        if (width(candidate, size, bold) <= maxWidth) return candidate
        if (line.isNotEmpty()) lines += line
        val pieces = breakWord(word, maxWidth, size, bold)
        lines += pieces.dropLast(1)
        return pieces.last()
    }

    private fun breakWord(
        word: String,
        maxWidth: Float,
        size: Float,
        bold: Boolean,
    ): List<String> {
        val pieces = mutableListOf<String>()
        var current = ""
        word.forEach { c ->
            if (width(current + c, size, bold) > maxWidth && current.isNotEmpty()) {
                pieces += current
                current = ""
            }
            current += c
        }
        pieces += current
        return pieces
    }

    fun image(
        image: PDImageXObject,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
    ) = stream.drawImage(image, x.pt, (y + height).fromTop, width.pt, height.pt)

    /** Fills a square module of a QR Code. */
    fun module(
        x: Float,
        y: Float,
        size: Float,
    ) {
        stream.addRect(x.pt, (y + size).fromTop, size.pt, size.pt)
        stream.fill()
    }

    /** Diagonal grey watermark across the page (NT 008 §2.5: ≥ 50 pt, K35). */
    fun watermark(text: String) {
        val size = WATERMARK_SIZE
        val textWidth = width(text, size) * POINTS_PER_CM
        val centerX = PDRectangle.A4.width / 2
        val centerY = pageHeight / 2
        stream.saveGraphicsState()
        stream.beginText()
        stream.setFont(regular, size)
        stream.setNonStrokingColor(WATERMARK_GRAY, WATERMARK_GRAY, WATERMARK_GRAY)
        stream.setTextMatrix(
            Matrix.getRotateInstance(
                Math.toRadians(WATERMARK_ANGLE),
                centerX - textWidth / 2 * COS45,
                centerY - textWidth / 2 * COS45,
            ),
        )
        stream.showText(sanitize(text))
        stream.endText()
        stream.restoreGraphicsState()
    }

    override fun close() = stream.close()

    private fun font(bold: Boolean): PDFont = if (bold) this.bold else regular

    private val Float.pt: Float get() = this * POINTS_PER_CM
    private val Float.fromTop: Float get() = pageHeight - this * POINTS_PER_CM

    private fun sanitize(text: String): String =
        buildString {
            for (c in text) {
                val name = GlyphList.getAdobeGlyphList().codePointToName(c.code)
                append(
                    if (c.isWhitespace()) {
                        ' '
                    } else if (WinAnsiEncoding.INSTANCE.contains(name)) {
                        c
                    } else {
                        '?'
                    },
                )
            }
        }

    /** Font size in points, weight and colour of a run of text. */
    data class TextStyle(
        val size: Float,
        val bold: Boolean = false,
        val color: Color = Color.BLACK,
    )

    companion object {
        const val POINTS_PER_CM = 28.3465f
        const val THIN_LINE = 0.5f
        const val BORDER_LINE = 1f
        private const val THOUSAND = 1000f
        private const val WATERMARK_SIZE = 60f
        private const val WATERMARK_GRAY = 0.65f
        private const val WATERMARK_ANGLE = 45.0
        private const val COS45 = 0.7071f
    }
}
