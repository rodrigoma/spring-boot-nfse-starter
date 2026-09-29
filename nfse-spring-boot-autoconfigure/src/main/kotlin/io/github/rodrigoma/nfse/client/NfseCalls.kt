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

    /**
     * Paths under the ADN Parâmetros Municipais base URL — the six `GET` queries of the service. Its other three
     * paths are `POST`s for a municipality to change its own parameters, which is not an emitter's job.
     */
    const val PARAM_AGREEMENT = "/{codigoMunicipio}/convenio"
    const val PARAM_RATES = "/{codigoMunicipio}/{codigoServico}/{competencia}/aliquota"
    const val PARAM_RATE_HISTORY = "/{codigoMunicipio}/{codigoServico}/historicoaliquotas"
    const val PARAM_BENEFIT = "/{codigoMunicipio}/{numeroBeneficio}/{competencia}/beneficio"
    const val PARAM_SPECIAL_REGIMES = "/{codigoMunicipio}/{codigoServico}/{competencia}/regimes_especiais"
    const val PARAM_WITHHOLDINGS = "/{codigoMunicipio}/{competencia}/retencoes"
}
