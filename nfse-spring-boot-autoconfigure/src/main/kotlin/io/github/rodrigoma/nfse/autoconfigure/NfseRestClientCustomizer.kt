package io.github.rodrigoma.nfse.autoconfigure

import org.springframework.web.client.RestClient

/**
 * Callback applied to the `RestClient.Builder` used for `nfseRestClient` right before it is built.
 *
 * Register one or more beans of this type to add interceptors, a proxy-aware request factory, extra headers, etc.
 * without re-assembling the client yourself. Customizers run in bean order (`@Order` / `Ordered` honoured) after the
 * starter's own configuration, so they may override it — including the mTLS request factory.
 */
fun interface NfseRestClientCustomizer {
    fun customize(builder: RestClient.Builder)
}
