package io.github.rodrigoma.nfse.autoconfigure

import com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL
import com.fasterxml.jackson.annotation.JsonInclude.Value.construct
import io.github.rodrigoma.nfse.certificate.NfseCertificate
import io.github.rodrigoma.nfse.certificate.NfseSslContextFactory
import io.github.rodrigoma.nfse.client.DefaultNfseClient
import io.github.rodrigoma.nfse.client.NfseClient
import io.github.rodrigoma.nfse.http.NfseErrorHandler
import io.github.rodrigoma.nfse.http.NfseLoggingInterceptor
import io.github.rodrigoma.nfse.model.dps.FederalId
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter
import org.springframework.web.client.RestClient
import tools.jackson.databind.MapperFeature
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.jacksonMapperBuilder
import java.net.http.HttpClient

@AutoConfiguration
@ConditionalOnProperty(name = ["nfse.enabled"], havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(NfseProperties::class)
class NfseAutoConfiguration(
    private val properties: NfseProperties,
) {
    /**
     * Mapper dedicated to the Sefin API (case-insensitive property names, nulls omitted).
     *
     * Deliberately **not** a Spring bean: publishing it would make Spring Boot's `JacksonAutoConfiguration` back off
     * and change the JSON contract of the whole application.
     */
    private val objectMapper: JsonMapper =
        jacksonMapperBuilder()
            .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES)
            .changeDefaultPropertyInclusion { construct(NON_NULL, NON_NULL) }
            .build()

    /** Loads and checks the certificate once; a bad certificate fails the context with a clear message. */
    @Bean
    fun nfseCertificate(): NfseCertificate {
        val certificate = NfseCertificate.load(properties.certificate)
        val configured = properties.emitterFederalId()
        val inCertificate = certificate.federalId
        if (inCertificate != null && !sameHolder(configured, inCertificate)) {
            log.warn(
                "The certificate identifies {} but nfse.emitter is {} — the Sefin rejects a DPS signed by another " +
                    "holder (E0718)",
                inCertificate,
                configured,
            )
        }
        log.info("NFS-e certificate loaded: {} ({} environment)", certificate, properties.environment)
        return certificate
    }

    @Bean(name = ["nfseRestClient"])
    fun nfseRestClient(
        certificate: NfseCertificate,
        customizers: ObjectProvider<NfseRestClientCustomizer>,
    ): RestClient {
        val errorHandler = NfseErrorHandler(objectMapper)
        val httpClient =
            HttpClient
                .newBuilder()
                .sslContext(NfseSslContextFactory.create(certificate, properties.certificate))
                .connectTimeout(properties.connectTimeout)
                .build()
        val requestFactory = JdkClientHttpRequestFactory(httpClient).apply { setReadTimeout(properties.readTimeout) }

        val builder =
            RestClient
                .builder()
                .baseUrl(properties.resolvedSefinBaseUrl())
                .requestFactory(requestFactory)
                .configureMessageConverters {
                    it.registerDefaults().withJsonConverter(JacksonJsonHttpMessageConverter(objectMapper))
                }.also { if (properties.logRequests) it.requestInterceptor(NfseLoggingInterceptor()) }
                .defaultStatusHandler({ it.isError }) { _, response -> errorHandler.handle(response) }

        customizers.orderedStream().forEach { it.customize(builder) }

        return builder.build()
    }

    @Bean
    fun nfseClient(
        @Qualifier("nfseRestClient") restClient: RestClient,
        certificate: NfseCertificate,
    ): NfseClient = DefaultNfseClient(restClient, properties, certificate)

    private fun sameHolder(
        configured: FederalId,
        inCertificate: FederalId,
    ): Boolean =
        when {
            configured is FederalId.Cnpj && inCertificate is FederalId.Cnpj -> configured.base == inCertificate.base
            else -> configured == inCertificate
        }

    private companion object {
        private val log = LoggerFactory.getLogger(NfseAutoConfiguration::class.java)
    }
}
