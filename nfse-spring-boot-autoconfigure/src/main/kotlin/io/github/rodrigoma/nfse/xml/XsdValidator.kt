package io.github.rodrigoma.nfse.xml

import io.github.rodrigoma.nfse.exception.NfseError
import io.github.rodrigoma.nfse.exception.NfseException
import org.w3c.dom.Document
import org.xml.sax.ErrorHandler
import org.xml.sax.SAXParseException
import javax.xml.XMLConstants
import javax.xml.transform.dom.DOMSource
import javax.xml.validation.Schema
import javax.xml.validation.SchemaFactory

/**
 * Validates documents against the XSDs embedded verbatim in the jar (`META-INF/nfse/xsd/1.01/`): the official
 * `NFSe-ESQUEMAS_XSD v1.01` package of 2026-07-27 (alphanumeric CNPJ, corrected `TSSerieDPS`, no `DOCTYPE` in
 * `xmldsig-core-schema.xsd`).
 */
class XsdValidator(
    resource: String,
) {
    private val schema: Schema =
        SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI).run {
            setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "")
            // Includes resolve relative to the schema URL: plain files on a test classpath, jar: entries at runtime.
            setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "file,jar")
            val url =
                requireNotNull(XsdValidator::class.java.classLoader.getResource(resource)) {
                    "Schema $resource is missing from the classpath"
                }
            newSchema(url)
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
