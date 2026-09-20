package io.github.rodrigoma.nfse.xml

import org.w3c.dom.Document
import java.io.StringWriter
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

/**
 * DOM plumbing shared by the builders, the signer and the parsers: namespace-aware, secure factories and the
 * value formats the schema expects (`TSDec15V2`, `TSData`, `TSDateTimeUTC`).
 */
object XmlSupport {
    /** Namespace of every NFS-e document (`DPS`, `NFSe`, `pedRegEvento`, `evento`). */
    const val NFSE_NAMESPACE = "http://www.sped.fazenda.gov.br/nfse"

    /** Layout version written into `versao` attributes. */
    const val LAYOUT_VERSION = "1.01"

    private const val MONEY_SCALE = 2

    /** `TSDateTimeUTC` requires `±hh:mm` — `Z` is not accepted, hence `xxx` instead of `XXX`. */
    private val dateTimeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssxxx")

    // Factories are created per call: the JAXP factories are not guaranteed thread-safe and the client is a singleton.
    private fun documentBuilderFactory(): DocumentBuilderFactory =
        DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            isXIncludeAware = false
            isExpandEntityReferences = false
        }

    private fun transformerFactory(): TransformerFactory =
        TransformerFactory.newInstance().apply {
            setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
        }

    fun newDocument(): Document =
        documentBuilderFactory().newDocumentBuilder().newDocument().apply { xmlStandalone = true }

    fun parse(xml: String): Document =
        documentBuilderFactory().newDocumentBuilder().parse(xml.byteInputStream(Charsets.UTF_8)).apply {
            xmlStandalone = true
        }

    /** Serializes without re-indenting, so a signed document keeps the bytes that were signed. */
    fun serialize(document: Document): String {
        val writer = StringWriter()
        transformerFactory()
            .newTransformer()
            .apply {
                setOutputProperty(OutputKeys.ENCODING, "UTF-8")
                setOutputProperty(OutputKeys.INDENT, "no")
            }.transform(DOMSource(document), StreamResult(writer))
        return writer.toString()
    }

    /** `TSDec15V2` and friends: two decimals, plain notation, never negative. */
    fun formatDecimal(value: BigDecimal): String {
        require(value.signum() >= 0) { "Amounts and percentages cannot be negative, got $value" }
        return value.setScale(MONEY_SCALE, RoundingMode.HALF_EVEN).toPlainString()
    }

    fun formatDate(value: LocalDate): String = value.format(DateTimeFormatter.ISO_LOCAL_DATE)

    fun formatDateTime(value: OffsetDateTime): String = value.truncatedTo(ChronoUnit.SECONDS).format(dateTimeFormat)

    fun parseDateTime(value: String?): OffsetDateTime? =
        value?.let { runCatching { OffsetDateTime.parse(it) }.getOrNull() }

    fun parseDecimal(value: String?): BigDecimal? = value?.let { runCatching { BigDecimal(it) }.getOrNull() }
}
