package io.github.rodrigoma.nfse.xml

import io.github.rodrigoma.nfse.model.dps.AdditionalInfo
import io.github.rodrigoma.nfse.model.dps.Construction
import io.github.rodrigoma.nfse.model.dps.ConstructionReference
import io.github.rodrigoma.nfse.model.dps.EventActivity
import io.github.rodrigoma.nfse.model.dps.EventActivityReference
import io.github.rodrigoma.nfse.model.dps.ForeignTrade
import io.github.rodrigoma.nfse.model.dps.ServiceInfo
import io.github.rodrigoma.nfse.model.dps.ServiceLocation
import org.w3c.dom.Element

/** Writer for the `serv` group (`TCServ`). */
internal object ServiceXml {
    fun write(
        parent: Element,
        service: ServiceInfo,
    ) {
        val serv = parent.child("serv")
        serv.child("locPrest").apply {
            when (val location = service.location) {
                is ServiceLocation.Municipality -> text("cLocPrestacao", PartyXml.ibge(location.ibge))
                is ServiceLocation.Country -> text("cPaisPrestacao", location.iso)
            }
        }
        serv.child("cServ").apply {
            text("cTribNac", service.code.nationalTaxCode)
            textIfPresent("cTribMun", service.code.municipalTaxCode)
            text("xDescServ", service.code.description)
            textIfPresent("cNBS", service.code.nbsCode)
            textIfPresent("cIntContrib", service.code.internalCode)
        }
        service.foreignTrade?.let { foreignTrade(serv, it) }
        service.construction?.let { construction(serv, it) }
        service.eventActivity?.let { eventActivity(serv, it) }
        service.additionalInfo?.let { additionalInfo(serv, it) }
    }

    private fun foreignTrade(
        parent: Element,
        trade: ForeignTrade,
    ) {
        parent.child("comExt").apply {
            code("mdPrestacao", trade.provisionMode)
            code("vincPrest", trade.partiesRelationship)
            text("tpMoeda", trade.currencyCode)
            decimal("vServMoeda", trade.amountInCurrency)
            text("mecAFComexP", trade.providerSupportMechanism)
            text("mecAFComexT", trade.takerSupportMechanism)
            code("movTempBens", trade.temporaryGoodsMovement)
            textIfPresent("nDI", trade.importDeclaration)
            textIfPresent("nRE", trade.exportRegistration)
            text("mdic", if (trade.shareWithMdic) "1" else "0")
        }
    }

    private fun construction(
        parent: Element,
        construction: Construction,
    ) {
        val obra = parent.child("obra")
        obra.textIfPresent("inscImobFisc", construction.propertyRegistration)
        when (val reference = construction.reference) {
            is ConstructionReference.Code -> obra.text("cObra", reference.code)
            is ConstructionReference.Cib -> obra.text("cCIB", reference.cib)
            is ConstructionReference.Location -> PartyXml.simpleAddress(obra, reference.address)
        }
    }

    private fun eventActivity(
        parent: Element,
        activity: EventActivity,
    ) {
        val element = parent.child("atvEvento")
        element.text("xNome", activity.name)
        element.date("dtIni", activity.startDate)
        element.date("dtFim", activity.endDate)
        when (val reference = activity.reference) {
            is EventActivityReference.Id -> element.text("idAtvEvt", reference.id)
            is EventActivityReference.Location -> PartyXml.simpleAddress(element, reference.address)
        }
    }

    private fun additionalInfo(
        parent: Element,
        info: AdditionalInfo,
    ) {
        val infoCompl = parent.child("infoCompl")
        infoCompl.textIfPresent("idDocTec", info.technicalDocumentId)
        infoCompl.textIfPresent("docRef", info.referenceDocument)
        infoCompl.textIfPresent("xPed", info.purchaseOrder)
        if (info.purchaseOrderItems.isNotEmpty()) {
            val items = infoCompl.child("gItemPed")
            info.purchaseOrderItems.forEach { items.text("xItemPed", it) }
        }
        infoCompl.textIfPresent("xInfComp", info.text)
    }
}
