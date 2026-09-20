package io.github.rodrigoma.nfse.model.event

import io.github.rodrigoma.nfse.model.dps.XmlCode
import java.time.OffsetDateTime

/** Event types of the Sistema Nacional NFS-e (Anexo II). [code] is the 6-digit type; the XML element is `e` + code. */
enum class NfseEventType(
    override val code: String,
    val description: String,
) : XmlCode {
    CANCELLATION("101101", "Cancelamento de NFS-e"),
    CANCELLATION_BY_SUBSTITUTION("105102", "Cancelamento de NFS-e por Substituição"),
    CANCELLATION_ANALYSIS_REQUEST("101103", "Solicitação de Análise Fiscal para Cancelamento de NFS-e"),
    CANCELLATION_GRANTED_BY_ANALYSIS("105104", "Cancelamento de NFS-e Deferido por Análise Fiscal"),
    CANCELLATION_DENIED_BY_ANALYSIS("105105", "Cancelamento de NFS-e Indeferido por Análise Fiscal"),
    PROVIDER_CONFIRMATION("202201", "Manifestação de NFS-e - Confirmação do Prestador"),
    TAKER_CONFIRMATION("203202", "Manifestação de NFS-e - Confirmação do Tomador"),
    INTERMEDIARY_CONFIRMATION("204203", "Manifestação de NFS-e - Confirmação do Intermediário"),
    TACIT_CONFIRMATION("205204", "Manifestação de NFS-e - Confirmação Tácita"),
    PROVIDER_REJECTION("202205", "Manifestação de NFS-e - Rejeição do Prestador"),
    TAKER_REJECTION("203206", "Manifestação de NFS-e - Rejeição do Tomador"),
    INTERMEDIARY_REJECTION("204207", "Manifestação de NFS-e - Rejeição do Intermediário"),
    REJECTION_ANNULMENT("205208", "Manifestação de NFS-e - Anulação da Rejeição"),
    CANCELLATION_EX_OFFICIO("305101", "Cancelamento de NFS-e por Ofício"),
    BLOCK_EX_OFFICIO("305102", "Bloqueio de NFS-e por Ofício"),
    UNBLOCK_EX_OFFICIO("305103", "Desbloqueio de NFS-e por Ofício"),
    ;

    /** Name of the XML element carrying this event inside `infPedReg` / `infEvento` (`e101101`, …). */
    val elementName: String get() = "e$code"

    companion object {
        fun fromCode(code: String): NfseEventType? = entries.firstOrNull { it.code == code.removePrefix("e") }
    }
}

/** `cMotivo` of the cancellation event `e101101`. */
enum class CancellationReason(
    override val code: String,
) : XmlCode {
    ISSUANCE_ERROR("1"),
    SERVICE_NOT_PROVIDED("2"),
    OTHER("9"),
}

/**
 * An event registered against an NFS-e, parsed from the `evento` XML the Sefin returns.
 *
 * @property id `EVT` + access key (50) + type (6) + request number (3).
 * @property typeCode The 6-digit event type; [type] is `null` when the code is not in [NfseEventType].
 * @property sequence `nSeqEvento`.
 * @property reasonCode `cMotivo` for events that carry one (cancellation, rejection…).
 * @property justification `xMotivo`, when present.
 * @property xml The complete `evento` XML, as returned by the API, for the application to keep.
 */
data class NfseEvent(
    val id: String,
    val typeCode: String,
    val sequence: Int,
    val processedAt: OffsetDateTime?,
    val accessKey: String,
    val reasonCode: String? = null,
    val justification: String? = null,
    val xml: String,
) {
    val type: NfseEventType? get() = NfseEventType.fromCode(typeCode)
}
