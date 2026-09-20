package io.github.rodrigoma.nfse.danfse

import io.github.rodrigoma.nfse.autoconfigure.NfseAutoConfiguration
import io.github.rodrigoma.nfse.client.DanfsePdfRenderer
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean

/**
 * Registers the local [DanfseRenderer]; with it on the classpath, `NfseClient.danfse()` renders the PDF instead of
 * calling the (suspended) ADN service. Disable with `nfse.danfse.enabled=false`; set `nfse.danfse.stub=true` to
 * print the optional "Canhoto".
 */
@AutoConfiguration(before = [NfseAutoConfiguration::class])
@ConditionalOnProperty(name = ["nfse.danfse.enabled"], havingValue = "true", matchIfMissing = true)
class DanfseAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(DanfsePdfRenderer::class)
    fun danfseRenderer(
        @Value("\${nfse.danfse.stub:false}") stub: Boolean,
    ): DanfseRenderer = DanfseRenderer(DanfseOptions(stub = stub))
}
