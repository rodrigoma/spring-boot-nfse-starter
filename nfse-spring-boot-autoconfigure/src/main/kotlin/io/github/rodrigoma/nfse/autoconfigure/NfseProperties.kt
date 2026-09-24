package io.github.rodrigoma.nfse.autoconfigure

import io.github.rodrigoma.nfse.model.dps.Address
import io.github.rodrigoma.nfse.model.dps.AddressLocation
import io.github.rodrigoma.nfse.model.dps.BrazilianState
import io.github.rodrigoma.nfse.model.dps.DpsId
import io.github.rodrigoma.nfse.model.dps.FederalId
import io.github.rodrigoma.nfse.model.dps.ServiceProvider
import io.github.rodrigoma.nfse.model.dps.SimplesNacionalAssessment
import io.github.rodrigoma.nfse.model.dps.SimplesNacionalOption
import io.github.rodrigoma.nfse.model.dps.SpecialTaxRegime
import io.github.rodrigoma.nfse.model.dps.TaxRegime
import org.springframework.beans.factory.InitializingBean
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.core.io.Resource
import java.net.URI
import java.time.Duration

/**
 * `nfse.*` — everything that belongs to the emitter (certificate, CNPJ, municipality, tax regime, series) lives here;
 * the library itself carries no fiscal values.
 */
@ConfigurationProperties(prefix = "nfse")
data class NfseProperties(
    /** Set to `false` to leave the starter on the classpath without creating any bean. */
    val enabled: Boolean = true,
    val environment: NfseEnvironment = NfseEnvironment.RESTRICTED_PRODUCTION,
    val certificate: Certificate = Certificate(),
    val emitter: Emitter = Emitter(),
    /** `verAplic` (≤ 20 characters). Defaults to `nfse-boot/<library version>`. */
    val applicationVersion: String? = null,
    /** Logs outgoing HTTP traffic at `DEBUG` with the compressed XML payloads reduced to their size. */
    val logRequests: Boolean = false,
    val healthIndicatorEnabled: Boolean = false,
    val connectTimeout: Duration = Duration.ofSeconds(DEFAULT_CONNECT_TIMEOUT_SECONDS),
    val readTimeout: Duration = Duration.ofSeconds(DEFAULT_READ_TIMEOUT_SECONDS),
    val baseUrl: BaseUrl = BaseUrl(),
) : InitializingBean {
    /**
     * `nfse.certificate.*` — the emitter's A1 (PKCS#12) certificate. At most one of [sslBundle], [location] and
     * [base64]; a bean of `NfseCertificateProvider` wins over all of them (Vault, KMS, Secrets Manager).
     */
    data class Certificate(
        /** Name of a bundle declared under `spring.ssl.bundle.*`, which carries its own passwords. */
        val sslBundle: String? = null,
        /** The PKCS#12 file: `file:/etc/secrets/certificado.pfx`, `classpath:…`, or a plain path. */
        val location: Resource? = null,
        /** The same file, Base64-encoded; line breaks are tolerated. */
        val base64: String? = null,
        /** PKCS#12 password. Not used with [sslBundle], which carries its own. */
        val password: String = "",
        /** Key entry to use when the file holds more than one; the first private-key entry by default. */
        val alias: String? = null,
        /** Optional trust store (JKS or PKCS#12) replacing the JDK default — for stubs and corporate proxies. */
        val trustStorePath: String? = null,
        val trustStorePassword: String? = null,
    ) {
        /** The configured sources, by property name. More than one is a startup error; see `NfseCertificate.load`. */
        val configuredSources: List<String>
            get() =
                listOfNotNull(
                    sslBundle?.let { "$PREFIX.ssl-bundle" },
                    location?.let { "$PREFIX.location" },
                    base64?.let { "$PREFIX.base64" },
                )

        override fun toString(): String =
            "Certificate(sslBundle=$sslBundle, location=$location, base64=${base64?.let { "<hidden>" }}, " +
                "password=<hidden>, alias=$alias, trustStorePath=$trustStorePath, trustStorePassword=<hidden>)"

        companion object {
            const val PREFIX = "nfse.certificate"
        }
    }

    /** `nfse.emitter.*` — the service provider that issues the DPS. */
    data class Emitter(
        val cnpj: String? = null,
        val cpf: String? = null,
        val municipalRegistration: String? = null,
        /** IBGE code of the municipality of the establishment (`cLocEmi`). */
        val municipalityIbge: Int? = null,
        val address: Address = Address(),
        val email: String? = null,
        /** Digits only: DDD + number. */
        val phone: String? = null,
        val taxRegime: TaxRegime = TaxRegime(),
        /** `serie` of the DPS; own software must use 1–49999 (rule E0010). */
        val dpsSeries: Int = 1,
    ) {
        data class Address(
            val street: String? = null,
            val number: String? = null,
            val complement: String? = null,
            val district: String? = null,
            /** Digits only. */
            val zipCode: String? = null,
            /** Defaults to `nfse.emitter.municipality-ibge`. */
            val municipalityIbge: Int? = null,
            val state: BrazilianState? = null,
        ) {
            val isPresent: Boolean get() = street != null
        }

        data class TaxRegime(
            val simplesNacional: SimplesNacionalOption = SimplesNacionalOption.NOT_OPTING,
            /** Required for ME/EPP, forbidden otherwise. */
            val simplesNacionalAssessment: SimplesNacionalAssessment? = null,
            val specialRegime: SpecialTaxRegime = SpecialTaxRegime.NONE,
        )
    }

    /** Absolute URLs overriding the ones implied by [environment] — e.g. a local stub. */
    data class BaseUrl(
        /** Sefin Nacional: emission, lookup, events. */
        val sefin: String? = null,
        /** ADN Contribuintes: distribution by NSU, events by access key. */
        val adn: String? = null,
        /** ADN DANFSe. */
        val danfse: String? = null,
        /** ADN Parâmetros Municipais. */
        val municipalParameters: String? = null,
    )

    fun resolvedSefinBaseUrl(): String = baseUrl.sefin ?: environment.sefinBaseUrl()

    fun resolvedAdnBaseUrl(): String = baseUrl.adn ?: environment.adnBaseUrl()

    fun resolvedDanfseBaseUrl(): String = baseUrl.danfse ?: environment.danfseBaseUrl()

    fun resolvedMunicipalParametersBaseUrl(): String =
        baseUrl.municipalParameters ?: environment.municipalParametersBaseUrl()

    fun resolvedApplicationVersion(): String =
        (applicationVersion ?: defaultApplicationVersion()).take(APPLICATION_VERSION_MAX_LENGTH)

    fun emitterFederalId(): FederalId =
        emitter.cnpj?.let { FederalId.Cnpj(it) }
            ?: FederalId.Cpf(requireNotNull(emitter.cpf) { "nfse.emitter.cnpj or cpf" })

    /** The provider group as it goes into a DPS issued by the provider itself. */
    fun emitterProvider(): ServiceProvider =
        ServiceProvider(
            id = emitterFederalId(),
            municipalRegistration = emitter.municipalRegistration,
            address = emitterAddress(),
            phone = emitter.phone,
            email = emitter.email,
            taxRegime =
                TaxRegime(
                    simplesNacional = emitter.taxRegime.simplesNacional,
                    simplesNacionalAssessment = emitter.taxRegime.simplesNacionalAssessment,
                    specialRegime = emitter.taxRegime.specialRegime,
                ),
        )

    fun emitterAddress(): Address? =
        emitter.address.takeIf { it.isPresent }?.let {
            Address(
                location =
                    AddressLocation.National(
                        municipalityIbge = it.municipalityIbge ?: requireNotNull(emitter.municipalityIbge),
                        zipCode = requireNotNull(it.zipCode),
                    ),
                street = requireNotNull(it.street),
                number = requireNotNull(it.number),
                complement = it.complement,
                district = requireNotNull(it.district),
            )
        }

    override fun afterPropertiesSet() {
        // The certificate sources are validated where the certificate is loaded: only there is it known whether the
        // application published an NfseCertificateProvider bean, which wins over these properties.
        require((emitter.cnpj != null) xor (emitter.cpf != null)) {
            "Set exactly one of nfse.emitter.cnpj and nfse.emitter.cpf"
        }
        runCatching { emitterFederalId() }
            .getOrElse { throw IllegalArgumentException("nfse.emitter: ${it.message}", it) }
        requireNotNull(emitter.municipalityIbge) {
            "nfse.emitter.municipality-ibge must be configured (IBGE code, 7 digits)"
        }
        require(emitter.dpsSeries in 1..DpsId.MAX_SERIES) {
            "nfse.emitter.dps-series must be between 1 and ${DpsId.MAX_SERIES}"
        }
        require(
            (emitter.taxRegime.simplesNacional == SimplesNacionalOption.ME_EPP) ==
                (emitter.taxRegime.simplesNacionalAssessment != null),
        ) {
            "nfse.emitter.tax-regime.simples-nacional-assessment is required for ME_EPP and must be absent otherwise"
        }
        if (emitter.address.isPresent) {
            val address = emitter.address
            require(address.number != null && address.district != null && address.zipCode != null) {
                "nfse.emitter.address needs street, number, district and zip-code"
            }
        }
        applicationVersion?.let { require(it.isNotBlank()) { "nfse.application-version must not be blank" } }
        val urls =
            listOf(
                "nfse.base-url.sefin" to baseUrl.sefin,
                "nfse.base-url.adn" to baseUrl.adn,
                "nfse.base-url.danfse" to baseUrl.danfse,
                "nfse.base-url.municipal-parameters" to baseUrl.municipalParameters,
            )
        urls.forEach { (name, url) ->
            url?.let {
                require(runCatching { URI(it).isAbsolute }.getOrDefault(false)) {
                    "$name must be an absolute URL (e.g. http://localhost:8080), got '$it'"
                }
            }
        }
    }

    companion object {
        const val DEFAULT_CONNECT_TIMEOUT_SECONDS = 10L
        const val DEFAULT_READ_TIMEOUT_SECONDS = 60L
        const val APPLICATION_VERSION_MAX_LENGTH = 20

        private fun defaultApplicationVersion(): String =
            "nfse-boot/" + (NfseProperties::class.java.getPackage()?.implementationVersion ?: "dev")
    }
}
