package io.github.rodrigoma.nfse.xml

import io.github.rodrigoma.nfse.model.dps.Dps
import io.github.rodrigoma.nfse.model.dps.Substitution
import org.w3c.dom.Document
import org.w3c.dom.Element

/**
 * Turns a [Dps] into the `DPS` XML document (layout 1.01), validated against the embedded XSD.
 *
 * The document uses the default namespace only — the Sefin rejects namespace prefixes (rule E1228) — and the
 * `infDPS/@Id` is derived from the emitter, municipality, series and number ([DpsId]).
 */
class DpsXmlBuilder(
    private val validator: XsdValidator = XsdValidator.dps(),
) {
    /**
     * Builds and validates.
     *
     * @throws io.github.rodrigoma.nfse.exception.NfseException.Validation when the result does not conform to the XSD.
     */
    fun build(dps: Dps): Document = buildUnvalidated(dps).also { validator.validateOrThrow(it) }

    /** Builds without schema validation — handy for tests that assert on invalid documents. */
    fun buildUnvalidated(dps: Dps): Document {
        val document = XmlSupport.newDocument()
        val root = document.createElementNS(XmlSupport.NFSE_NAMESPACE, ROOT_ELEMENT)
        root.setAttribute(VERSION_ATTRIBUTE, XmlSupport.LAYOUT_VERSION)
        document.appendChild(root)
        val infDps = root.child(INF_ELEMENT)
        infDps.setAttribute(XmlSigner.ID_ATTRIBUTE, dps.id.value)
        writeHeader(infDps, dps)
        dps.substitution?.let { substitution(infDps, it) }
        PartyXml.provider(infDps, dps.provider)
        dps.taker?.let { PartyXml.person(infDps, "toma", it) }
        dps.intermediary?.let { PartyXml.person(infDps, "interm", it) }
        ServiceXml.write(infDps, dps.service)
        AmountsXml.write(infDps, dps.amounts)
        dps.ibsCbs?.let { IbsCbsXml.write(infDps, it) }
        return document
    }

    private fun writeHeader(
        infDps: Element,
        dps: Dps,
    ) {
        infDps.text("tpAmb", dps.environment.code)
        infDps.text("dhEmi", XmlSupport.formatDateTime(dps.issuedAt))
        infDps.text("verAplic", dps.applicationVersion.take(APPLICATION_VERSION_MAX_LENGTH))
        // Unpadded, as the notes generated in production carry it (`<serie>3</serie>`); the Id pads it to 5.
        infDps.text("serie", dps.series.toString())
        infDps.text("nDPS", dps.number.toString())
        infDps.date("dCompet", dps.competenceDate)
        infDps.code("tpEmit", dps.emitterType)
        infDps.codeIfPresent("cMotivoEmisTI", dps.takerEmissionReason)
        infDps.textIfPresent("chNFSeRej", dps.rejectedNfseAccessKey)
        infDps.text("cLocEmi", PartyXml.ibge(dps.emitterMunicipalityIbge))
    }

    private fun substitution(
        infDps: Element,
        substitution: Substitution,
    ) {
        infDps.child("subst").apply {
            text("chSubstda", substitution.substitutedAccessKey)
            code("cMotivo", substitution.reason)
            textIfPresent("xMotivo", substitution.justification)
        }
    }

    companion object {
        const val ROOT_ELEMENT = "DPS"
        const val INF_ELEMENT = "infDPS"
        const val VERSION_ATTRIBUTE = "versao"
        private const val APPLICATION_VERSION_MAX_LENGTH = 20

        /** The `infDPS` element of a built document — what gets signed. */
        fun infDps(document: Document): Element =
            document.getElementsByTagNameNS(XmlSupport.NFSE_NAMESPACE, INF_ELEMENT).item(0) as Element
    }
}
