package io.github.rodrigoma.nfse.autoconfigure

import io.github.rodrigoma.nfse.client.NfseApiPaths
import io.github.rodrigoma.nfse.exception.NfseException
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.health.contributor.AbstractHealthIndicator
import org.springframework.boot.health.contributor.Health
import org.springframework.boot.health.contributor.HealthIndicator
import org.springframework.context.annotation.Bean
import org.springframework.web.client.RestClient

@AutoConfiguration(after = [NfseAutoConfiguration::class])
@ConditionalOnClass(HealthIndicator::class)
@ConditionalOnProperty(name = ["nfse.health-indicator-enabled"], havingValue = "true")
@ConditionalOnBean(name = ["nfseRestClient"])
class NfseHealthIndicatorAutoConfiguration {
    @Bean("nfseHealthIndicator")
    fun nfseHealthIndicator(
        @Qualifier("nfseRestClient") restClient: RestClient,
        properties: NfseProperties,
    ): HealthIndicator = NfseHealthIndicator(restClient, requireNotNull(properties.emitter.municipalityIbge))
}

/** Fetches the emitter municipality's agreement parameters — a cheap call that also exercises the mTLS handshake. */
class NfseHealthIndicator(
    private val restClient: RestClient,
    private val municipalityIbge: Int,
) : AbstractHealthIndicator() {
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    override fun doHealthCheck(builder: Health.Builder) {
        try {
            restClient
                .get()
                .uri(NfseApiPaths.MUNICIPAL_AGREEMENT, municipalityIbge)
                .retrieve()
                .toBodilessEntity()
            builder.up()
        } catch (e: NfseException.Unauthorized) {
            builder.outOfService().withDetail("reason", "certificate not accepted")
        } catch (e: Exception) {
            builder.down(e)
        }
    }
}
