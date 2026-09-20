package io.github.rodrigoma.nfse.client

import io.github.rodrigoma.nfse.exception.NfseException
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClientException

/** Runs a call, turning transport failures into [NfseException.Unavailable]; [NfseException]s pass through. */
internal inline fun <T> nfseCall(call: () -> T): T =
    try {
        call()
    } catch (e: ResourceAccessException) {
        throw NfseException.Unavailable("Cannot reach the NFS-e service: ${e.message}", cause = e)
    } catch (e: RestClientException) {
        throw (e.cause as? NfseException) ?: NfseException.Unavailable("NFS-e call failed: ${e.message}", cause = e)
    }

/** Paths under the Sefin Nacional base URL. */
object NfseApiPaths {
    const val NFSE = "/nfse"
    const val NFSE_BY_KEY = "/nfse/{chaveAcesso}"
    const val DPS_BY_ID = "/dps/{id}"
    const val EVENTS = "/nfse/{chaveAcesso}/eventos"
    const val EVENT = "/nfse/{chaveAcesso}/eventos/{tipoEvento}/{numSeqEvento}"

    /** Paths under the ADN Contribuintes base URL. */
    const val ADN_EVENTS = "/NFSe/{chaveAcesso}/Eventos"
    const val ADN_DISTRIBUTION = "/DFe/{nsu}"
}
