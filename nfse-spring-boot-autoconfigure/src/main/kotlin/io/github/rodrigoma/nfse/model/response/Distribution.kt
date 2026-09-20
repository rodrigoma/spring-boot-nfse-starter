package io.github.rodrigoma.nfse.model.response

import io.github.rodrigoma.nfse.exception.NfseError
import java.time.OffsetDateTime

/** `StatusProcessamento` of an ADN distribution response. */
enum class DistributionStatus {
    REJECTED,
    NONE_FOUND,
    FOUND,
    ;

    companion object {
        fun fromWire(value: String?): DistributionStatus =
            when (value?.uppercase()) {
                "DOCUMENTOS_LOCALIZADOS" -> FOUND
                "NENHUM_DOCUMENTO_LOCALIZADO" -> NONE_FOUND
                else -> REJECTED
            }
    }
}

/** `TipoDocumento` of a distributed document. */
enum class DistributedDocumentType {
    NONE,
    DPS,
    EVENT_REQUEST,
    NFSE,
    EVENT,
    CNC,
    ;

    companion object {
        fun fromWire(value: String?): DistributedDocumentType =
            when (value?.uppercase()) {
                "DPS" -> DPS
                "PEDIDO_REGISTRO_EVENTO" -> EVENT_REQUEST
                "NFSE" -> NFSE
                "EVENTO" -> EVENT
                "CNC" -> CNC
                else -> NONE
            }
    }
}

/**
 * One document from the ADN (`DistribuicaoNSU`).
 *
 * @property nsu Sequential number of the document for this taxpayer — persist the highest one you processed.
 * @property eventType The ADN's event name (`CANCELAMENTO`, `CONFIRMACAO_TOMADOR`, …) for events, else `null`.
 * @property xml The decoded document (`NFSe`, `evento`, …).
 */
data class DistributedDocument(
    val nsu: Long,
    val accessKey: String?,
    val type: DistributedDocumentType,
    val eventType: String?,
    val xml: String,
    val generatedAt: OffsetDateTime?,
)

/**
 * Result of `GET /DFe/{NSU}`. [DistributionStatus.NONE_FOUND] means the cursor is at the end — not an error.
 *
 * @property nextNsu The NSU to ask for next (highest returned + 1), or the requested one when nothing came back.
 */
data class DistributionBatch(
    val status: DistributionStatus,
    val documents: List<DistributedDocument>,
    val alerts: List<NfseError>,
    val nextNsu: Long,
)
