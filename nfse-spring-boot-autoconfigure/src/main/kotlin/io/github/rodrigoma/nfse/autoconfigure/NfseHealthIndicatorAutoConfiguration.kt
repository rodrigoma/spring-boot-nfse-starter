package io.github.rodrigoma.nfse.autoconfigure

import io.github.rodrigoma.nfse.certificate.NfseCertificate
import io.github.rodrigoma.nfse.client.NfseApiPaths
import io.github.rodrigoma.nfse.exception.NfseException
import io.github.rodrigoma.nfse.model.dps.DpsId
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
        certificate: NfseCertificate,
    ): HealthIndicator {
        val probe =
            DpsId(
                requireNotNull(properties.emitter.municipalityIbge),
                properties.emitterFederalId(),
                properties.emitter.dpsSeries,
                1,
            )
        return NfseHealthIndicator(restClient, probe, certificate)
    }
}

/**
 * The certificate's validity plus `HEAD /dps/{id}` for the emitter's first DPS — a cheap Sefin call that exercises
 * the mTLS handshake; 200 and 404 both mean the service is up and accepted the certificate.
 *
 * An expired certificate is **DOWN**: emission is refused, and this is the alert that should fire before a customer
 * notices. One that expires soon stays UP, with the date as a detail.
 */
class NfseHealthIndicator(
    private val restClient: RestClient,
    private val probe: DpsId,
    private val certificate: NfseCertificate,
) : AbstractHealthIndicator() {
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    override fun doHealthCheck(builder: Health.Builder) {
        builder.withDetail("certificateExpiresAt", certificate.expiresAt.toString())
        certificate.unusableReason?.let {
            builder.down().withDetail("certificate", it)
            return
        }
        if (certificate.expiresWithin(NfseCertificate.EXPIRY_WARNING)) {
            builder.withDetail(
                "certificateWarning",
                "expires in less than ${NfseCertificate.EXPIRY_WARNING.toDays()} days",
            )
        }
        try {
            restClient
                .head()
                .uri(NfseApiPaths.DPS_BY_ID, probe.value)
                .retrieve()
                .toBodilessEntity()
            builder.up()
        } catch (e: NfseException.NotFound) {
            builder.up()
        } catch (e: NfseException.Unauthorized) {
            builder.outOfService().withDetail("reason", "certificate not accepted")
        } catch (e: Exception) {
            builder.down(e)
        }
    }
}
