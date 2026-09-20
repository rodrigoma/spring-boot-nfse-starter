package io.github.rodrigoma.nfse.xml

import io.github.rodrigoma.nfse.model.dps.IbsCbs
import io.github.rodrigoma.nfse.model.dps.IbsCbsTax
import io.github.rodrigoma.nfse.model.dps.PropertyInfo
import io.github.rodrigoma.nfse.model.dps.PropertyReference
import io.github.rodrigoma.nfse.model.dps.Recipient
import io.github.rodrigoma.nfse.model.dps.ReimbursementDocument
import io.github.rodrigoma.nfse.model.dps.ReimbursementDocumentReference
import org.w3c.dom.Element

/** Writer for the `IBSCBS` group (`TCRTCInfoIBSCBS`). */
internal object IbsCbsXml {
    fun write(
        parent: Element,
        ibsCbs: IbsCbs,
    ) {
        val root = parent.child("IBSCBS")
        root.code("finNFSe", ibsCbs.purpose)
        ibsCbs.personalUse?.let { root.text("indFinal", if (it) "1" else "0") }
        root.text("cIndOp", ibsCbs.operationIndicatorCode)
        root.codeIfPresent("tpOper", ibsCbs.governmentOperationType)
        if (ibsCbs.referencedNfse.isNotEmpty()) {
            root.child("gRefNFSe").apply { ibsCbs.referencedNfse.forEach { text("refNFSe", it) } }
        }
        root.codeIfPresent("tpEnteGov", ibsCbs.governmentEntityType)
        root.code("indDest", ibsCbs.recipientIndicator)
        ibsCbs.recipient?.let { recipient(root, it) }
        ibsCbs.property?.let { property(root, it) }
        val valores = root.child("valores")
        if (ibsCbs.amounts.reimbursements.isNotEmpty()) {
            val group = valores.child("gReeRepRes")
            ibsCbs.amounts.reimbursements.forEach { reimbursement(group, it) }
        }
        tax(valores.child("trib"), ibsCbs.amounts.tax)
    }

    private fun recipient(
        parent: Element,
        recipient: Recipient,
    ) {
        val dest = parent.child("dest")
        PartyXml.federalId(dest, recipient.id)
        dest.text("xNome", recipient.name)
        recipient.address?.let { PartyXml.address(dest, it) }
        dest.textIfPresent("fone", recipient.phone)
        dest.textIfPresent("email", recipient.email)
    }

    private fun property(
        parent: Element,
        property: PropertyInfo,
    ) {
        val imovel = parent.child("imovel")
        imovel.textIfPresent("inscImobFisc", property.propertyRegistration)
        when (val reference = property.reference) {
            is PropertyReference.Cib -> imovel.text("cCIB", reference.cib)
            is PropertyReference.Location -> PartyXml.simpleAddress(imovel, reference.address)
        }
    }

    private fun reimbursement(
        parent: Element,
        document: ReimbursementDocument,
    ) {
        val doc = parent.child("documentos")
        when (val reference = document.reference) {
            is ReimbursementDocumentReference.NationalDocument ->
                doc.child("dFeNacional").apply {
                    code("tipoChaveDFe", reference.keyType)
                    textIfPresent("xTipoChaveDFe", reference.keyTypeDescription)
                    text("chaveDFe", reference.key)
                }
            is ReimbursementDocumentReference.OtherFiscalDocument ->
                doc.child("docFiscalOutro").apply {
                    text("cMunDocFiscal", PartyXml.ibge(reference.municipalityIbge))
                    text("nDocFiscal", reference.number)
                    text("xDocFiscal", reference.description)
                }
            is ReimbursementDocumentReference.OtherDocument ->
                doc.child("docOutro").apply {
                    text("nDoc", reference.number)
                    text("xDoc", reference.description)
                }
        }
        document.supplier?.let {
            doc.child("fornec").apply {
                PartyXml.federalId(this, it.id)
                text("xNome", it.name)
            }
        }
        doc.date("dtEmiDoc", document.issueDate)
        doc.date("dtCompDoc", document.competenceDate)
        doc.code("tpReeRepRes", document.type)
        doc.textIfPresent("xTpReeRepRes", document.typeDescription)
        doc.decimal("vlrReeRepRes", document.amount)
    }

    private fun tax(
        parent: Element,
        tax: IbsCbsTax,
    ) {
        parent.child("gIBSCBS").apply {
            text("CST", tax.cst)
            text("cClassTrib", tax.classificationCode)
            textIfPresent("cCredPres", tax.presumedCreditCode)
            tax.regularTaxation?.let {
                child("gTribRegular").apply {
                    text("CSTReg", it.cst)
                    text("cClassTribReg", it.classificationCode)
                }
            }
            tax.deferral?.let {
                child("gDif").apply {
                    decimal("pDifUF", it.statePercentage)
                    decimal("pDifMun", it.municipalPercentage)
                    decimal("pDifCBS", it.cbsPercentage)
                }
            }
        }
    }
}
