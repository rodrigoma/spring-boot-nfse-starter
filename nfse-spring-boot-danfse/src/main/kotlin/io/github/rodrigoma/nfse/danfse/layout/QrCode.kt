package io.github.rodrigoma.nfse.danfse.layout

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/** QR Code of the public query URL, drawn as vector squares. */
internal object QrCode {
    const val QUERY_URL = "https://www.nfse.gov.br/ConsultaPublica/?tpc=1&chave="

    fun draw(
        canvas: PdfCanvas,
        accessKey: String,
        x: Float,
        y: Float,
        size: Float,
    ) {
        val hints = mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.MARGIN to 0)
        val matrix = QRCodeWriter().encode(QUERY_URL + accessKey, BarcodeFormat.QR_CODE, 0, 0, hints)
        val module = size / matrix.width
        for (row in 0 until matrix.height) {
            for (column in 0 until matrix.width) {
                if (matrix.get(column, row)) canvas.module(x + column * module, y + row * module, module)
            }
        }
    }
}
