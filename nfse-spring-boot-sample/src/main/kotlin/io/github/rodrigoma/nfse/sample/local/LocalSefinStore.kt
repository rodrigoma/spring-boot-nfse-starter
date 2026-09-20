package io.github.rodrigoma.nfse.sample.local

import io.github.rodrigoma.nfse.xml.XmlSupport
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random

private const val NS = XmlSupport.NFSE_NAMESPACE

/** A note kept by the fake Sefin. */
internal class StoredNote(
    val accessKey: String,
    val dpsDigits: String,
    val nfseXml: String,
    val events: MutableList<StoredEvent> = mutableListOf(),
)

internal data class Distributed(
    val nsu: Long,
    val note: StoredNote,
    val xml: String,
    val type: String,
    val eventTypeCode: String?,
)

internal class StoredEvent(
    val id: String,
    val typeCode: String,
    val sequence: Int,
    val xml: String,
)

/**
 * In-memory "Sefin Nacional": turns a signed DPS into an NFS-e the way the real one does — key, number, processing
 * timestamp, computed ISSQN — and keeps the notes and their events for the lifetime of the process.
 */
internal class LocalSefinStore {
    private val notes = ConcurrentHashMap<String, StoredNote>()
    private val order = java.util.concurrent.CopyOnWriteArrayList<String>()
    private val byDps = ConcurrentHashMap<String, String>()
    private val nextNumber = AtomicLong(1)

    /** Every document (note, then its events) in emission order, numbered from 1, for the NSU distribution. */
    fun documentsFrom(nsu: Long): List<Distributed> {
        var next = 1L
        val all = mutableListOf<Distributed>()
        for (note in order) {
            val stored = notes.getValue(note)
            all += Distributed(next++, stored, stored.nfseXml, "NFSE", null)
            stored.events.forEach { all += Distributed(next++, stored, it.xml, "EVENTO", it.typeCode) }
        }
        return all.filter { it.nsu > nsu }
    }

    fun findByDps(dpsDigits: String): StoredNote? = byDps[dpsDigits]?.let { notes[it] }

    fun find(accessKey: String): StoredNote? = notes[accessKey]

    /** Generates the NFS-e for a valid, signed DPS document and stores it. */
    fun generate(dps: Document): StoredNote {
        val infDps = dps.documentElement.first("infDPS")
        val dpsId = infDps.getAttribute("Id")
        val digits = dpsId.removePrefix("DPS")
        val municipality = digits.take(IBGE_LENGTH)
        val federalIdType = digits[IBGE_LENGTH].toString()
        val federalId = digits.substring(IBGE_LENGTH + 1, IBGE_LENGTH + 1 + FEDERAL_ID_LENGTH)
        val number = nextNumber.getAndIncrement()
        val processedAt = OffsetDateTime.now()
        val accessKey = accessKey(municipality, federalIdType, federalId, number, processedAt)

        val serviceAmount = BigDecimal(infDps.first("vServ").textContent)
        val taxable = infDps.first("tribISSQN").textContent == "1"
        val rate = infDps.optional("pAliq")?.textContent?.let { BigDecimal(it) } ?: DEFAULT_RATE
        val issqn =
            if (taxable) {
                serviceAmount
                    .multiply(
                        rate,
                    ).divide(HUNDRED, 2, RoundingMode.HALF_EVEN)
            } else {
                BigDecimal.ZERO
            }
        val withheld = infDps.first("tpRetISSQN").textContent != "1"
        val net = if (withheld) serviceAmount.subtract(issqn) else serviceAmount

        val amounts = Amounts(serviceAmount, taxable, rate, issqn, withheld, net)
        val document = nfseDocument(dps, accessKey, number, processedAt, municipality, amounts)
        val note = StoredNote(accessKey, digits, XmlSupport.serialize(document))
        notes[accessKey] = note
        byDps[digits] = accessKey
        order += accessKey
        return note
    }

    private class Amounts(
        val service: BigDecimal,
        val taxable: Boolean,
        val rate: BigDecimal,
        val issqn: BigDecimal,
        val withheld: Boolean,
        val net: BigDecimal,
    )

    @Suppress("LongParameterList")
    private fun nfseDocument(
        dps: Document,
        accessKey: String,
        number: Long,
        processedAt: OffsetDateTime,
        municipality: String,
        amounts: Amounts,
    ): Document {
        val infDps = dps.documentElement.first("infDPS")
        val digits = infDps.getAttribute("Id").removePrefix("DPS")
        val federalIdType = digits[IBGE_LENGTH].toString()
        val federalId = digits.substring(IBGE_LENGTH + 1, IBGE_LENGTH + 1 + FEDERAL_ID_LENGTH)
        val document = XmlSupport.newDocument()
        val root = document.createElementNS(NS, "NFSe").also { it.setAttribute("versao", XmlSupport.LAYOUT_VERSION) }
        document.appendChild(root)
        val infNfse = root.child("infNFSe").also { it.setAttribute("Id", "NFS$accessKey") }
        infNfse.text("xLocEmi", "Município $municipality")
        infNfse.text("xLocPrestacao", "Município $municipality")
        infNfse.text("nNFSe", number.toString())
        infNfse.text("cLocIncid", municipality)
        infNfse.text("xLocIncid", "Município $municipality")
        infNfse.text("xTribNac", "Serviço ${infDps.first("cTribNac").textContent}")
        infNfse.text("verAplic", APPLICATION_VERSION)
        infNfse.text("ambGer", "2")
        infNfse.text("tpEmis", "1")
        infNfse.text("cStat", "100")
        infNfse.text("dhProc", XmlSupport.formatDateTime(processedAt))
        infNfse.text("nDFSe", number.toString())
        val emit = infNfse.child("emit")
        if (federalIdType == "1") emit.text("CPF", federalId.takeLast(CPF_LENGTH)) else emit.text("CNPJ", federalId)
        infDps.optional("IM")?.let { emit.text("IM", it.textContent) }
        emit.text("xNome", "EMITENTE LOCAL")
        emit.child("enderNac").apply {
            text("xLgr", "Rua do Sandbox")
            text("nro", "1")
            text("xBairro", "Centro")
            text("cMun", municipality)
            text("UF", "SP")
            text("CEP", "00000000")
        }
        infNfse.child("valores").apply {
            if (amounts.taxable) {
                text("vBC", amounts.service.setScale(2).toPlainString())
                text("pAliqAplic", amounts.rate.setScale(2).toPlainString())
                text("vISSQN", amounts.issqn.setScale(2).toPlainString())
            }
            if (amounts.withheld) text("vTotalRet", amounts.issqn.setScale(2).toPlainString())
            text("vLiq", amounts.net.setScale(2).toPlainString())
        }
        infNfse.appendChild(document.importNode(dps.documentElement, true))

        return document
    }

    /** Registers the event of a `pedRegEvento` document against [note]. */
    fun registerEvent(
        note: StoredNote,
        request: Document,
    ): StoredEvent {
        val infPedReg = request.documentElement.first("infPedReg")
        val typeCode =
            infPedReg
                .children()
                .first { it.localName.matches(EVENT_ELEMENT) }
                .localName
                .removePrefix("e")
        val sequence = note.events.count { it.typeCode == typeCode } + 1
        val id = "EVT" + note.accessKey + typeCode + sequence.toString().padStart(SEQUENCE_LENGTH, '0')
        val document = XmlSupport.newDocument()
        val root = document.createElementNS(NS, "evento").also { it.setAttribute("versao", XmlSupport.LAYOUT_VERSION) }
        document.appendChild(root)
        val infEvento = root.child("infEvento").also { it.setAttribute("Id", id) }
        infEvento.text("verAplic", APPLICATION_VERSION)
        infEvento.text("ambGer", "2")
        infEvento.text("nSeqEvento", sequence.toString())
        infEvento.text("dhProc", XmlSupport.formatDateTime(OffsetDateTime.now()))
        infEvento.text(
            "nDFSe",
            note.events.size
                .plus(1)
                .toString(),
        )
        infEvento.appendChild(document.importNode(request.documentElement, true))
        val event = StoredEvent(id, typeCode, sequence, XmlSupport.serialize(document))
        note.events += event
        return event
    }

    private fun accessKey(
        municipality: String,
        federalIdType: String,
        federalId: String,
        number: Long,
        issuedAt: OffsetDateTime,
    ): String {
        val body =
            municipality + "2" + federalIdType + federalId +
                number.toString().padStart(NFSE_NUMBER_LENGTH, '0') +
                issuedAt.format(DateTimeFormatter.ofPattern("yyMM")) +
                Random.nextLong(0, RANDOM_BOUND).toString().padStart(RANDOM_LENGTH, '0')
        return body + mod11(body)
    }

    private fun mod11(value: String): String {
        var weight = 2
        var sum = 0
        for (c in value.reversed()) {
            sum += c.digitToInt() * weight
            weight = if (weight == MAX_WEIGHT) 2 else weight + 1
        }
        val remainder = sum % MOD
        return if (remainder < 2) "0" else (MOD - remainder).toString()
    }

    private fun Element.first(name: String): Element = optional(name) ?: error("$name is missing")

    private fun Element.optional(name: String): Element? = getElementsByTagNameNS(NS, name).item(0) as Element?

    private fun Element.child(name: String): Element = ownerDocument.createElementNS(NS, name).also { appendChild(it) }

    private fun Element.text(
        name: String,
        value: String,
    ) {
        child(name).textContent = value
    }

    private fun Element.children(): List<Element> =
        (0 until childNodes.length).mapNotNull {
            childNodes.item(it) as? Element
        }

    private companion object {
        const val APPLICATION_VERSION = "LocalSefin/1.0"
        const val IBGE_LENGTH = 7
        const val FEDERAL_ID_LENGTH = 14
        const val CPF_LENGTH = 11
        const val NFSE_NUMBER_LENGTH = 13
        const val RANDOM_LENGTH = 9
        const val RANDOM_BOUND = 1_000_000_000L
        const val SEQUENCE_LENGTH = 3
        const val MAX_WEIGHT = 9
        const val MOD = 11
        val DEFAULT_RATE: BigDecimal = BigDecimal("2.00")
        val HUNDRED: BigDecimal = BigDecimal(100)
        val EVENT_ELEMENT = Regex("e\\d{6}")
    }
}
