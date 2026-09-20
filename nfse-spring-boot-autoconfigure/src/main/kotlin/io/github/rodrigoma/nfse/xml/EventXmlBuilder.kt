package io.github.rodrigoma.nfse.xml

import io.github.rodrigoma.nfse.autoconfigure.NfseEnvironment
import io.github.rodrigoma.nfse.model.dps.FederalId
import io.github.rodrigoma.nfse.model.event.CancellationReason
import io.github.rodrigoma.nfse.model.event.NfseEventType
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.time.OffsetDateTime

/** A cancellation request (`pedRegEvento` with `e101101`). */
data class CancellationRequest(
    val environment: NfseEnvironment,
    val applicationVersion: String,
    val eventAt: OffsetDateTime,
    val author: FederalId,
    val accessKey: String,
    val reason: CancellationReason,
    /** `xMotivo` — 15 to 255 characters, mandatory for `e101101`. */
    val justification: String,
) {
    init {
        require(author is FederalId.Cnpj || author is FederalId.Cpf) { "The event author must be a CNPJ or a CPF" }
        require(accessKey.length == ACCESS_KEY_LENGTH && accessKey.all(Char::isDigit)) {
            "The NFS-e access key must have $ACCESS_KEY_LENGTH digits"
        }
        require(justification.length in JUSTIFICATION_MIN_LENGTH..JUSTIFICATION_MAX_LENGTH) {
            "The justification must have between $JUSTIFICATION_MIN_LENGTH and $JUSTIFICATION_MAX_LENGTH characters"
        }
    }

    /**
     * `"PRE"` + access key (50) + event type (6) — 59 characters. The XSD pattern (`PRE[0-9]{56}`) and `maxLength`
     * settle it: the request number mentioned in the prose of the annex is not part of the identifier.
     */
    val id: String = "PRE" + accessKey + NfseEventType.CANCELLATION.code

    companion object {
        const val ACCESS_KEY_LENGTH = 50
        const val JUSTIFICATION_MIN_LENGTH = 15
        const val JUSTIFICATION_MAX_LENGTH = 255
    }
}

/** Builds the `pedRegEvento` XML (layout 1.01) for the events this library sends, validated against the XSD. */
class EventXmlBuilder(
    private val validator: XsdValidator = XsdValidator.eventRequest(),
) {
    fun buildCancellation(request: CancellationRequest): Document =
        buildCancellationUnvalidated(request).also { validator.validateOrThrow(it) }

    fun buildCancellationUnvalidated(request: CancellationRequest): Document {
        val document = XmlSupport.newDocument()
        val root = document.createElementNS(XmlSupport.NFSE_NAMESPACE, ROOT_ELEMENT)
        root.setAttribute(DpsXmlBuilder.VERSION_ATTRIBUTE, XmlSupport.LAYOUT_VERSION)
        document.appendChild(root)
        val infPedReg = root.child(INF_ELEMENT)
        infPedReg.setAttribute(XmlSigner.ID_ATTRIBUTE, request.id)
        infPedReg.text("tpAmb", request.environment.code)
        infPedReg.text("verAplic", request.applicationVersion)
        infPedReg.text("dhEvento", XmlSupport.formatDateTime(request.eventAt))
        when (val author = request.author) {
            is FederalId.Cnpj -> infPedReg.text("CNPJAutor", author.value)
            is FederalId.Cpf -> infPedReg.text("CPFAutor", author.value)
            else -> error("unreachable")
        }
        infPedReg.text("chNFSe", request.accessKey)
        infPedReg.child(NfseEventType.CANCELLATION.elementName).apply {
            text("xDesc", NfseEventType.CANCELLATION.description)
            code("cMotivo", request.reason)
            text("xMotivo", request.justification)
        }
        return document
    }

    companion object {
        const val ROOT_ELEMENT = "pedRegEvento"
        const val INF_ELEMENT = "infPedReg"

        /** The `infPedReg` element of a built document — what gets signed. */
        fun infPedReg(document: Document): Element =
            document.getElementsByTagNameNS(XmlSupport.NFSE_NAMESPACE, INF_ELEMENT).item(0) as Element
    }
}
