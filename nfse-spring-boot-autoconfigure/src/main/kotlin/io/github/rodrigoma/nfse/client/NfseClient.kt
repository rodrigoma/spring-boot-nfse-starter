package io.github.rodrigoma.nfse.client

import io.github.rodrigoma.nfse.model.dps.Dps
import io.github.rodrigoma.nfse.model.dps.DpsId
import io.github.rodrigoma.nfse.model.event.CancellationReason
import io.github.rodrigoma.nfse.model.event.NfseEvent
import io.github.rodrigoma.nfse.model.event.NfseEventType
import io.github.rodrigoma.nfse.model.request.DpsRequest
import io.github.rodrigoma.nfse.model.response.DistributionBatch
import io.github.rodrigoma.nfse.model.response.Nfse
import io.github.rodrigoma.nfse.model.response.NfseResult

/**
 * Entry point for the Sistema Nacional NFS-e. One bean (`nfseClient`) is auto-configured from `nfse.*`.
 *
 * Emission, lookup and events go to the **Sefin Nacional**; distribution, the event list and the DANFSE go to the
 * **ADN**; municipal parameters live in [municipalParameters] (ADN Parâmetros Municipais).
 *
 * Every method throws [io.github.rodrigoma.nfse.exception.NfseException] subclasses: `Rejected` when the service
 * refuses the request, `Validation` when the XML fails the local checks, `Unavailable` for network/429/5xx
 * problems, `Unauthorized` for 401/403 and `NotFound` for 404 (except where documented otherwise).
 */
interface NfseClient {
    /** Builds, validates, signs and sends the DPS (`POST /nfse`); the provider comes from `nfse.emitter.*`. */
    fun emit(request: DpsRequest): NfseResult

    /** Same as [emit] for a fully specified [Dps]. */
    fun emit(dps: Dps): NfseResult

    /** `GET /nfse/{chaveAcesso}`. */
    fun get(accessKey: String): Nfse

    /** `GET /dps/{id}` — the access key of the NFS-e generated from that DPS, or `null` when none exists (404). */
    fun accessKeyOf(dpsId: DpsId): String?

    /** `HEAD /dps/{id}` — whether an NFS-e was generated from that DPS. */
    fun exists(dpsId: DpsId): Boolean

    /**
     * Registers the cancellation event (`e101101`) for the NFS-e (`POST /nfse/{chaveAcesso}/eventos`).
     *
     * @param justification 15 to 255 characters (`xMotivo`).
     */
    fun cancel(
        accessKey: String,
        reason: CancellationReason,
        justification: String,
    ): NfseEvent

    /** `GET /nfse/{chaveAcesso}/eventos/{tipoEvento}/{numSeqEvento}` — one specific event (Sefin Nacional). */
    fun event(
        accessKey: String,
        type: NfseEventType,
        sequence: Int = 1,
    ): NfseEvent

    /** `GET /NFSe/{chaveAcesso}/Eventos` on the ADN — every event registered against the NFS-e (empty when none). */
    fun events(accessKey: String): List<NfseEvent>

    /**
     * `GET /DFe/{NSU}` on the ADN — the documents (NFS-e, events) in which the certificate holder is provider, taker
     * or intermediary, from [nsu] on. Persist [DistributionBatch.nextNsu] and call again until the status is
     * [io.github.rodrigoma.nfse.model.response.DistributionStatus.NONE_FOUND].
     *
     * @param cnpj `cnpjConsulta` — a CNPJ with the same root as the certificate's, to read another establishment.
     */
    fun distribution(
        nsu: Long,
        cnpj: String? = null,
    ): DistributionBatch

    /**
     * The DANFSe (PDF). With the `nfse-spring-boot-danfse` module on the classpath the PDF is rendered locally from
     * the note and its events (NT 008/2026 layout); otherwise it is fetched from the ADN — whose official
     * generation **is suspended since 2026-08-03**, so expect `NotFound`/`Unavailable` there.
     */
    fun danfse(accessKey: String): ByteArray

    /** Municipal agreement, rates, benefits, special regimes and withholdings (ADN Parâmetros Municipais). */
    val municipalParameters: MunicipalParametersClient
}
