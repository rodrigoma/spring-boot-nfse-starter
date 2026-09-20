package io.github.rodrigoma.nfse.danfse

import io.github.rodrigoma.nfse.client.DanfsePdfRenderer
import io.github.rodrigoma.nfse.danfse.layout.DanfseLayout
import io.github.rodrigoma.nfse.danfse.layout.NfseView
import io.github.rodrigoma.nfse.danfse.layout.PdfCanvas
import io.github.rodrigoma.nfse.model.event.NfseEvent
import io.github.rodrigoma.nfse.model.event.NfseEventType
import io.github.rodrigoma.nfse.model.response.Nfse
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDDocumentInformation
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject
import java.io.ByteArrayOutputStream

/** Whether the note is still valid, read from its events. */
enum class NoteStatus {
    ACTIVE,
    CANCELLED,
    SUBSTITUTED,
    ;

    companion object {
        fun of(events: List<NfseEvent>): NoteStatus =
            when {
                events.any { it.type == NfseEventType.CANCELLATION_BY_SUBSTITUTION } -> SUBSTITUTED
                events.any { it.type in CANCELLING_EVENTS } -> CANCELLED
                else -> ACTIVE
            }

        private val CANCELLING_EVENTS =
            setOf(
                NfseEventType.CANCELLATION,
                NfseEventType.CANCELLATION_GRANTED_BY_ANALYSIS,
                NfseEventType.CANCELLATION_EX_OFFICIO,
            )
    }
}

/**
 * Rendering options.
 *
 * @property stub Prints the optional "Canhoto" block (acknowledgement date, signature, number/key) at the bottom.
 */
data class DanfseOptions(
    val stub: Boolean = false,
)

/**
 * Renders the DANFSe v2.0 (NT 008/2026) from the `NFSe` XML: one A4 page with the fixed model of Anexo I, the
 * QR Code of the public query, "NFS-e SEM VALIDADE JURÍDICA" for restricted production and the CANCELADA /
 * SUBSTITUÍDA watermark when the events say so. Everything printed comes from the XML.
 */
class DanfseRenderer(
    private val options: DanfseOptions = DanfseOptions(),
) : DanfsePdfRenderer {
    private val logoBytes: ByteArray? =
        DanfseRenderer::class.java.getResourceAsStream(LOGO_RESOURCE)?.use {
            it.readBytes()
        }

    override fun render(
        nfse: Nfse,
        events: List<NfseEvent>,
    ): ByteArray = render(nfse.xml, NoteStatus.of(events))

    /** Renders the `NFSe` XML with an explicit [status]. */
    fun render(
        nfseXml: String,
        status: NoteStatus = NoteStatus.ACTIVE,
    ): ByteArray {
        val view = NfseView(nfseXml)
        PDDocument().use { document ->
            document.documentInformation =
                PDDocumentInformation().apply {
                    title = "DANFSe ${view.number} - ${view.accessKey}"
                    producer = "spring-boot-nfse-starter"
                }
            val page = PDPage(PDRectangle.A4)
            document.addPage(page)
            val logo = logoBytes?.let { PDImageXObject.createFromByteArray(document, it, "logo-nfse") }
            PdfCanvas(document, page).use { canvas -> DanfseLayout(canvas, view, options, logo).draw(status) }
            return ByteArrayOutputStream().also { document.save(it) }.toByteArray()
        }
    }

    private companion object {
        const val LOGO_RESOURCE = "logo-nfse-horizontal.png"
    }
}
