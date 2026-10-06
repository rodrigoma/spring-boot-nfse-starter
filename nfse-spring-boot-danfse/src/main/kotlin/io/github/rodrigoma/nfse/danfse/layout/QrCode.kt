package io.github.rodrigoma.nfse.danfse.layout

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/** QR Code of the public query URL, drawn as vector squares. */
internal object QrCode {
    /** The address NT 008 prescribes (item 2.4, QR Code). */
    const val QUERY_URL = "https://www.nfse.gov.br/ConsultaPublica/?tpc=1&chave="

    /**
     * The same query in restricted production. **This is a deliberate departure from the letter of NT 008**,
     * which names a single address: a note issued with `tpAmb = 2` exists only in that environment, so the
     * prescribed URL would answer "not found" and suggest to whoever scans it that the emission failed. The
     * document is already stamped "NFS-e SEM VALIDADE JURÍDICA", so nothing legally binding points anywhere new.
     */
    const val RESTRICTED_QUERY_URL = "https://www.producaorestrita.nfse.gov.br/ConsultaPublica/?tpc=1&chave="

    fun queryUrl(restrictedProduction: Boolean): String = if (restrictedProduction) RESTRICTED_QUERY_URL else QUERY_URL

    /** Where the code sits on the page: X/Y of its top-left corner and its side, all in centimetres. */
    data class Placement(
        val x: Float,
        val y: Float,
        val size: Float,
    )

    fun draw(
        canvas: PdfCanvas,
        accessKey: String,
        placement: Placement,
        restrictedProduction: Boolean = false,
    ) {
        val (x, y, size) = placement
        val hints = mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.MARGIN to 0)
        val url = queryUrl(restrictedProduction) + accessKey
        val matrix = QRCodeWriter().encode(url, BarcodeFormat.QR_CODE, 0, 0, hints)
        val module = size / matrix.width
        for (row in 0 until matrix.height) {
            for (column in 0 until matrix.width) {
                if (matrix.get(column, row)) canvas.module(x + column * module, y + row * module, module)
            }
        }
    }
}
