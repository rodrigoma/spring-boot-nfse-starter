package io.github.rodrigoma.nfse.sample.local

import java.util.Locale

/** The smallest valid PDF with a few lines of Helvetica text — enough for a DANFSE placeholder. */
internal object LocalPdf {
    private const val LINE_HEIGHT = 18
    private const val TOP = 760

    fun render(lines: List<String>): ByteArray {
        val content =
            buildString {
                append("BT /F1 12 Tf 50 $TOP Td ${LINE_HEIGHT} TL\n")
                lines.forEach { append("(${it.replace("(", "\\(").replace(")", "\\)")}) Tj T*\n") }
                append("ET\n")
            }
        val objects =
            listOf(
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Contents 4 0 R " +
                    "/Resources << /Font << /F1 5 0 R >> >> >>",
                "<< /Length ${content.toByteArray().size} >>\nstream\n${content}endstream",
                "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>",
            )
        val out = StringBuilder("%PDF-1.4\n")
        val offsets = mutableListOf<Int>()
        objects.forEachIndexed { index, body ->
            offsets += out.length
            out.append("${index + 1} 0 obj\n$body\nendobj\n")
        }
        val xref = out.length
        out.append("xref\n0 ${objects.size + 1}\n0000000000 65535 f \n")
        offsets.forEach { out.append(String.format(Locale.ROOT, "%010d 00000 n \n", it)) }
        out.append("trailer\n<< /Size ${objects.size + 1} /Root 1 0 R >>\nstartxref\n$xref\n%%EOF\n")
        return out.toString().toByteArray(Charsets.ISO_8859_1)
    }
}
