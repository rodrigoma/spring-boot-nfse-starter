package io.github.rodrigoma.nfse.xml

import io.github.rodrigoma.nfse.exception.NfseError
import io.github.rodrigoma.nfse.exception.NfseException
import org.w3c.dom.Document
import org.w3c.dom.bootstrap.DOMImplementationRegistry
import org.w3c.dom.ls.DOMImplementationLS
import org.w3c.dom.ls.LSInput
import org.w3c.dom.ls.LSResourceResolver
import org.xml.sax.ErrorHandler
import org.xml.sax.SAXParseException
import javax.xml.XMLConstants
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamSource
import javax.xml.validation.Schema
import javax.xml.validation.SchemaFactory

/**
 * Validates documents against the XSDs embedded verbatim in the jar (`META-INF/nfse/xsd/1.01/`): the official
 * `NFSe-ESQUEMAS_XSD v1.01` package of 2026-07-27 (alphanumeric CNPJ, corrected `TSSerieDPS`, no `DOCTYPE` in
 * `xmldsig-core-schema.xsd`).
 *
 * Every schema — including the ones pulled in by `xs:include` / `xs:import` — is served from the classpath as a
 * stream, so loading does not depend on the resource URL scheme (`file:`, `jar:`, Spring Boot's `jar:nested:`)
 * and never reaches the network.
 */
class XsdValidator internal constructor(
    resource: String,
    private val classLoader: ClassLoader,
) {
    constructor(resource: String) : this(resource, XsdValidator::class.java.classLoader)

    private val directory = resource.substringBeforeLast('/')

    private val schema: Schema =
        SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI).run {
            setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "")
            setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")
            resourceResolver = LSResourceResolver { _, _, _, systemId, _ -> systemId?.let { classpathInput(it) } }
            val name = resource.substringAfterLast('/')
            newSchema(StreamSource(open(name), name))
        }

    private fun open(name: String) =
        requireNotNull(classLoader.getResourceAsStream("$directory/$name")) {
            "Schema $directory/$name is missing from the classpath"
        }

    /** Serves an included schema from the same classpath directory, ignoring whatever base URI Xerces derived. */
    private fun classpathInput(systemId: String): LSInput? {
        val name = systemId.substringAfterLast('/')
        val stream = classLoader.getResourceAsStream("$directory/$name") ?: return null
        val implementation = DOMImplementationRegistry.newInstance().getDOMImplementation("LS") as DOMImplementationLS
        return implementation.createLSInput().apply {
            byteStream = stream
            this.systemId = name
        }
    }

    /** Every schema violation, in document order; empty when the document is valid. */
    fun validate(document: Document): List<NfseError> {
        val errors = mutableListOf<NfseError>()
        val validator = schema.newValidator()
        validator.errorHandler =
            object : ErrorHandler {
                override fun warning(exception: SAXParseException) = Unit

                override fun error(exception: SAXParseException) {
                    errors += NfseError(SCHEMA_ERROR_CODE, exception.message ?: "invalid document")
                }

                override fun fatalError(exception: SAXParseException) = error(exception)
            }
        validator.validate(DOMSource(document))
        return errors
    }

    /** @throws NfseException.Validation when the document does not conform. */
    fun validateOrThrow(document: Document) {
        val errors = validate(document)
        if (errors.isNotEmpty()) throw NfseException.Validation(errors)
    }

    companion object {
        /** Code used for local schema errors — the same the Sefin returns for schema failures (Anexo I). */
        const val SCHEMA_ERROR_CODE = "E1235"
        private const val BASE = "META-INF/nfse/xsd/1.01"

        fun dps(): XsdValidator = XsdValidator("$BASE/DPS_v1.01.xsd")

        fun eventRequest(): XsdValidator = XsdValidator("$BASE/pedRegEvento_v1.01.xsd")

        fun nfse(): XsdValidator = XsdValidator("$BASE/NFSe_v1.01.xsd")

        fun event(): XsdValidator = XsdValidator("$BASE/evento_v1.01.xsd")
    }
}
