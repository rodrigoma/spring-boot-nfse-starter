package io.github.rodrigoma.nfse.xml

import io.github.rodrigoma.nfse.model.event.NfseEvent
import io.github.rodrigoma.nfse.model.response.Nfse
import org.w3c.dom.Element

/** Reads the fields the library exposes from the `NFSe` and `evento` XMLs returned by the Sefin. */
object NfseXmlParser {
    private const val NFSE_ID_PREFIX = "NFS"
    private val eventElementName = Regex("e\\d{6}")

    fun parseNfse(xml: String): Nfse {
        val root = XmlSupport.parse(xml).documentElement
        val infNfse = requireNotNull(root.firstElement("infNFSe")) { "Not an NFS-e document: infNFSe is missing" }
        val values = infNfse.directChild("valores")
        return Nfse(
            accessKey = infNfse.getAttribute(XmlSigner.ID_ATTRIBUTE).removePrefix(NFSE_ID_PREFIX),
            number = infNfse.directChild("nNFSe")?.textContent.orEmpty(),
            statusCode = infNfse.directChild("cStat")?.textContent.orEmpty(),
            processedAt = XmlSupport.parseDateTime(infNfse.directChild("dhProc")?.textContent),
            emitterMunicipality = infNfse.directChild("xLocEmi")?.textContent,
            dpsId = infNfse.firstElement("infDPS")?.getAttribute(XmlSigner.ID_ATTRIBUTE)?.takeIf { it.isNotEmpty() },
            netAmount = XmlSupport.parseDecimal(values?.directChild("vLiq")?.textContent),
            calculationBase = XmlSupport.parseDecimal(values?.directChild("vBC")?.textContent),
            appliedRate = XmlSupport.parseDecimal(values?.directChild("pAliqAplic")?.textContent),
            issqnAmount = XmlSupport.parseDecimal(values?.directChild("vISSQN")?.textContent),
            xml = xml,
        )
    }

    fun parseEvent(xml: String): NfseEvent {
        val root = XmlSupport.parse(xml).documentElement
        val infEvento = requireNotNull(root.firstElement("infEvento")) { "Not an event document: infEvento is missing" }
        val infPedReg = infEvento.firstElement("infPedReg")
        val body = infPedReg?.children()?.firstOrNull { eventElementName.matches(it.localName) }
        return NfseEvent(
            id = infEvento.getAttribute(XmlSigner.ID_ATTRIBUTE),
            typeCode = body?.localName?.removePrefix("e").orEmpty(),
            sequence = infEvento.directChild("nSeqEvento")?.textContent?.toIntOrNull() ?: 1,
            processedAt = XmlSupport.parseDateTime(infEvento.directChild("dhProc")?.textContent),
            accessKey = infPedReg?.firstText("chNFSe").orEmpty(),
            reasonCode = body?.directChild("cMotivo")?.textContent,
            justification = body?.directChild("xMotivo")?.textContent,
            xml = xml,
        )
    }

    private fun Element.children(): List<Element> =
        (0 until childNodes.length).mapNotNull {
            childNodes.item(it) as? Element
        }

    private fun Element.directChild(name: String): Element? = children().firstOrNull { it.localName == name }
}
