package io.github.rodrigoma.nfse.client

import io.github.rodrigoma.nfse.model.dps.Dps
import io.github.rodrigoma.nfse.model.dps.DpsId
import io.github.rodrigoma.nfse.model.event.CancellationReason
import io.github.rodrigoma.nfse.model.event.NfseEvent
import io.github.rodrigoma.nfse.model.request.DpsRequest
import io.github.rodrigoma.nfse.model.response.MunicipalParameters
import io.github.rodrigoma.nfse.model.response.Nfse
import io.github.rodrigoma.nfse.model.response.NfseResult

/**
 * Entry point for the Sistema Nacional NFS-e. One bean (`nfseClient`) is auto-configured from `nfse.*`.
 *
 * Every method throws [io.github.rodrigoma.nfse.exception.NfseException] subclasses: `Rejected` when the Sefin refuses
 * the request, `Validation` when the XML fails the local XSD check, `Unavailable` for network/5xx problems,
 * `Unauthorized` for 401/403 and `NotFound` for 404 (except where documented otherwise).
 */
interface NfseClient {
    /** Builds, validates, signs and sends the DPS (`POST /nfse`); the provider comes from `nfse.emitter.*`. */
    fun emit(request: DpsRequest): NfseResult

    /** Same as [emit] for a fully specified [Dps] (any `tpEmit`, any provider). */
    fun emit(dps: Dps): NfseResult

    /** `GET /nfse/{chaveAcesso}`. */
    fun get(accessKey: String): Nfse

    /** `GET /dps/{id}` — the access key of the NFS-e generated from that DPS, or `null` when none exists. */
    fun accessKeyOf(dpsId: DpsId): String?

    /** `HEAD /dps/{id}` — whether an NFS-e was generated from that DPS. */
    fun exists(dpsId: DpsId): Boolean

    /**
     * Registers the cancellation event (`e101101`) for the NFS-e.
     *
     * @param justification 15 to 255 characters (`xMotivo`).
     */
    fun cancel(
        accessKey: String,
        reason: CancellationReason,
        justification: String,
    ): NfseEvent

    /** `GET /nfse/{chaveAcesso}/eventos` — every event registered against the NFS-e. */
    fun events(accessKey: String): List<NfseEvent>

    /** The DANFSE (PDF) bytes of the NFS-e. */
    fun danfse(accessKey: String): ByteArray

    /** `GET /parametros_municipais/{codigoMunicipio}/convenio`. */
    fun municipalAgreement(municipalityIbge: Int): MunicipalParameters

    /** `GET /parametros_municipais/{codigoMunicipio}/{codigoServico}` — rates and regimes for a service sub-item. */
    fun municipalParameters(
        municipalityIbge: Int,
        serviceCode: String,
    ): MunicipalParameters
}
