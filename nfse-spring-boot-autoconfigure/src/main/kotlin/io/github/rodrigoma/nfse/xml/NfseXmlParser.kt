package io.github.rodrigoma.nfse.xml

import io.github.rodrigoma.nfse.model.event.NfseEvent
import io.github.rodrigoma.nfse.model.response.Nfse

/** Reads the fields the library exposes from the `NFSe` and `evento` XMLs returned by the Sefin. */
object NfseXmlParser {
    private const val NFSE_ID_PREFIX = "NFS"
    private val eventElementName = Regex("e\\d{6}")

    fun parseNfse(xml: String): Nfse {
        val root = XmlSupport.parse(xml).documentElement
        val infNfse = requireNotNull(root.firstElement("infNFSe")) { "Not an NFS-e document: infNFSe is missing" }
        val values = infNfse.childElement("valores")
        return Nfse(
            accessKey = infNfse.getAttribute(XmlSigner.ID_ATTRIBUTE).removePrefix(NFSE_ID_PREFIX),
            number = infNfse.childElement("nNFSe")?.textContent.orEmpty(),
            statusCode = infNfse.childElement("cStat")?.textContent.orEmpty(),
            processedAt = XmlSupport.parseDateTime(infNfse.childElement("dhProc")?.textContent),
            emitterMunicipality = infNfse.childElement("xLocEmi")?.textContent,
            dpsId = infNfse.firstElement("infDPS")?.getAttribute(XmlSigner.ID_ATTRIBUTE)?.takeIf { it.isNotEmpty() },
            netAmount = XmlSupport.parseDecimal(values?.childElement("vLiq")?.textContent),
            calculationBase = XmlSupport.parseDecimal(values?.childElement("vBC")?.textContent),
            appliedRate = XmlSupport.parseDecimal(values?.childElement("pAliqAplic")?.textContent),
            issqnAmount = XmlSupport.parseDecimal(values?.childElement("vISSQN")?.textContent),
            ibsCbs = NfseIbsCbsParser.parse(infNfse),
            xml = xml,
        )
    }

    fun parseEvent(xml: String): NfseEvent {
        val root = XmlSupport.parse(xml).documentElement
        val infEvento = requireNotNull(root.firstElement("infEvento")) { "Not an event document: infEvento is missing" }
        val infPedReg = infEvento.firstElement("infPedReg")
        val body = infPedReg?.childElements()?.firstOrNull { eventElementName.matches(it.localName) }
        return NfseEvent(
            id = infEvento.getAttribute(XmlSigner.ID_ATTRIBUTE),
            typeCode = body?.localName?.removePrefix("e").orEmpty(),
            sequence = infEvento.childElement("nSeqEvento")?.textContent?.toIntOrNull() ?: 1,
            processedAt = XmlSupport.parseDateTime(infEvento.childElement("dhProc")?.textContent),
            accessKey = infPedReg?.firstText("chNFSe").orEmpty(),
            reasonCode = body?.childElement("cMotivo")?.textContent,
            justification = body?.childElement("xMotivo")?.textContent,
            xml = xml,
        )
    }
}
