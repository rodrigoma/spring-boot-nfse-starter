package io.github.rodrigoma.nfse.client

import io.github.rodrigoma.nfse.model.event.NfseEvent
import io.github.rodrigoma.nfse.model.response.Nfse

/**
 * Renders the DANFSe (the PDF of an NFS-e) locally, following NT 008/2026. Implemented by the optional
 * `nfse-spring-boot-danfse` module; when a bean of this type exists, [NfseClient.danfse] renders locally instead of
 * calling the ADN service (suspended since 2026-08-03).
 */
fun interface DanfsePdfRenderer {
    /** @param events The note's events, so cancellation/substitution watermarks can be drawn. */
    fun render(
        nfse: Nfse,
        events: List<NfseEvent>,
    ): ByteArray
}
