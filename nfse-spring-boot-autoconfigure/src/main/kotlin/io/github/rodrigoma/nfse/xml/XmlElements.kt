package io.github.rodrigoma.nfse.xml

import io.github.rodrigoma.nfse.model.dps.XmlCode
import org.w3c.dom.Element
import java.math.BigDecimal
import java.time.LocalDate

// Element helpers shared by the builders and parsers; every element lives in the NFS-e namespace.

/** Text content of the first descendant named [name] in the NFS-e namespace, or `null`. */
internal fun Element.firstText(name: String): String? =
    getElementsByTagNameNS(XmlSupport.NFSE_NAMESPACE, name).item(0)?.textContent?.takeIf { it.isNotEmpty() }

internal fun Element.firstElement(name: String): Element? =
    getElementsByTagNameNS(XmlSupport.NFSE_NAMESPACE, name).item(0) as Element?

internal fun Element.child(name: String): Element =
    ownerDocument.createElementNS(XmlSupport.NFSE_NAMESPACE, name).also {
        appendChild(it)
    }

internal fun Element.text(
    name: String,
    value: String,
): Element = child(name).apply { textContent = value }

internal fun Element.textIfPresent(
    name: String,
    value: String?,
) {
    value?.let { text(name, it) }
}

internal fun Element.code(
    name: String,
    value: XmlCode,
): Element = text(name, value.code)

internal fun Element.codeIfPresent(
    name: String,
    value: XmlCode?,
) {
    value?.let { code(name, it) }
}

internal fun Element.decimal(
    name: String,
    value: BigDecimal,
): Element = text(name, XmlSupport.formatDecimal(value))

internal fun Element.decimalIfPresent(
    name: String,
    value: BigDecimal?,
) {
    value?.let { decimal(name, it) }
}

internal fun Element.date(
    name: String,
    value: LocalDate,
): Element = text(name, XmlSupport.formatDate(value))
