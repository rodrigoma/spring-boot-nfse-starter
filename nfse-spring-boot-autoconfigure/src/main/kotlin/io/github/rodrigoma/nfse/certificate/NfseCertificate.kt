package io.github.rodrigoma.nfse.certificate

import io.github.rodrigoma.nfse.autoconfigure.NfseProperties
import io.github.rodrigoma.nfse.exception.NfseException
import io.github.rodrigoma.nfse.model.dps.FederalId
import io.github.rodrigoma.nfse.xml.XmlSigner
import org.springframework.boot.ssl.NoSuchSslBundleException
import org.springframework.boot.ssl.SslBundles
import java.io.IOException
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.time.Duration
import java.time.Instant
import java.util.Base64

/**
 * The emitter's ICP-Brasil A1 certificate (PKCS#12), loaded and checked once at startup.
 *
 * Structural checks follow Anexo I and **fail the context**, because they mean the wrong file was configured:
 * X.509 v3, not a CA certificate, and KeyUsage with *Digital Signature* and *Non Repudiation* when the extension
 * is present. The **validity dates do not fail the context** — an A1 certificate expires every year, and taking a
 * whole application down over it trades a fiscal problem for an outage. Expiry is reported instead: logged at
 * startup, exposed through [expiresAt] and the health indicator, and enforced by [requireUsable] before anything
 * is signed.
 *
 * The same key signs the XML and opens the mTLS connection. The password and the key material never appear in
 * [toString].
 */
class NfseCertificate private constructor(
    val privateKey: PrivateKey,
    val certificate: X509Certificate,
    val chain: List<X509Certificate>,
    /** Which `nfse.certificate.*` source (or provider bean) this came from — for the startup log. */
    val source: String,
    internal val keyStore: KeyStore,
    private val password: CharArray,
) {
    /** CNPJ or CPF read from the ICP-Brasil extensions, or `null` for certificates without them (e.g. test ones). */
    val federalId: FederalId? by lazy { IcpBrasilExtensions.federalIdOf(certificate) }

    val subject: String get() = certificate.subjectX500Principal.name

    /** `notAfter`: an A1 certificate is issued for a year. */
    val expiresAt: Instant get() = certificate.notAfter.toInstant()

    val validFrom: Instant get() = certificate.notBefore.toInstant()

    val isExpired: Boolean get() = Instant.now().isAfter(expiresAt)

    val isNotYetValid: Boolean get() = Instant.now().isBefore(validFrom)

    /** `true` when the certificate can sign today — what [requireUsable] enforces. */
    val isUsable: Boolean get() = !isExpired && !isNotYetValid

    /** Why the certificate cannot be used today, or `null` when it can. */
    val unusableReason: String?
        get() =
            when {
                isExpired -> "the certificate of '$subject' expired on $expiresAt"
                isNotYetValid -> "the certificate of '$subject' is not valid before $validFrom"
                else -> null
            }

    fun expiresWithin(duration: Duration): Boolean = Instant.now().plus(duration).isAfter(expiresAt)

    /**
     * @throws NfseException.Certificate when the certificate is expired or not yet valid. Called before signing a
     * DPS or an event; reading an NFS-e keeps working, which is what you want while replacing the file.
     */
    fun requireUsable() {
        unusableReason?.let { throw NfseException.Certificate("$it; the DPS cannot be signed") }
    }

    fun signer(): XmlSigner = XmlSigner(privateKey, certificate)

    /** Password for [keyStore], for `KeyManagerFactory.init`. */
    internal fun password(): CharArray = password.copyOf()

    override fun toString(): String =
        "NfseCertificate(subject=$subject, serial=${certificate.serialNumber}, notAfter=${certificate.notAfter}, " +
            "federalId=$federalId)"

    companion object {
        private const val X509_VERSION = 3
        private const val KEY_USAGE_DIGITAL_SIGNATURE = 0
        private const val KEY_USAGE_NON_REPUDIATION = 1
        private const val NOT_A_CA = -1

        private fun fail(
            message: String,
            cause: Throwable? = null,
        ): Nothing = throw NfseException.Certificate(message, cause)

        /**
         * Loads from `nfse.certificate.*`: at most one of `ssl-bundle`, `location` and `base64`. Configuring more
         * than one fails here — silent precedence discovered in production costs more than a startup error.
         */
        fun load(
            properties: NfseProperties.Certificate,
            sslBundles: SslBundles? = null,
        ): NfseCertificate {
            val sources = properties.configuredSources
            if (sources.size > 1) {
                fail("Configure only one certificate source; found ${sources.joinToString(" and ")}")
            }
            return when {
                properties.sslBundle != null -> load(fromBundle(properties.sslBundle, sslBundles), SSL_BUNDLE_SOURCE)
                properties.location != null -> load(fromLocation(properties), LOCATION_SOURCE)
                properties.base64 != null -> load(fromBase64(properties), BASE64_SOURCE)
                else ->
                    fail(
                        "No certificate configured: set one of ${NfseProperties.Certificate.PREFIX}.ssl-bundle, " +
                            "${NfseProperties.Certificate.PREFIX}.location or " +
                            "${NfseProperties.Certificate.PREFIX}.base64, or publish an NfseCertificateProvider bean",
                    )
            }
        }

        /** Loads a PKCS#12 blob and runs the structural checks. */
        @JvmOverloads
        fun load(
            pkcs12: ByteArray,
            password: String,
            alias: String? = null,
        ): NfseCertificate = load(NfseKeyStore.ofPkcs12(pkcs12, password, alias), PROVIDER_SOURCE)

        /** Loads whatever a [NfseCertificateProvider] handed over. */
        @JvmOverloads
        fun load(
            source: NfseKeyStore,
            origin: String = PROVIDER_SOURCE,
        ): NfseCertificate {
            val keyStore = source.keyStore
            val chars = source.password.toCharArray()
            val alias = aliasOf(keyStore, source.alias)
            val key = keyStore.getKey(alias, chars) as? PrivateKey ?: fail("Entry '$alias' has no private key")
            val chain = keyStore.getCertificateChain(alias).orEmpty().filterIsInstance<X509Certificate>()
            val certificate = chain.firstOrNull() ?: fail("Entry '$alias' has no X.509 certificate")
            check(certificate)
            return NfseCertificate(key, certificate, chain, origin, keyStore, chars)
        }

        /** The configured alias, or the first private-key entry. Aliases are not secrets, so they can be listed. */
        private fun aliasOf(
            keyStore: KeyStore,
            configured: String?,
        ): String {
            val aliases = keyStore.aliases().toList()
            val keyEntries = aliases.filter { keyStore.isKeyEntry(it) }
            if (configured != null) {
                return configured.takeIf { keyStore.isKeyEntry(it) }
                    ?: fail(
                        "${NfseProperties.Certificate.PREFIX}.alias '$configured' is not a private-key entry; " +
                            "the file holds ${describe(aliases)}",
                    )
            }
            return keyEntries.firstOrNull()
                ?: fail("The PKCS#12 file contains no private key entry; it holds ${describe(aliases)}")
        }

        private fun describe(aliases: List<String>): String =
            if (aliases.isEmpty()) "no entries at all" else "${aliases.size} entries: ${aliases.joinToString(", ")}"

        private fun fromBundle(
            name: String,
            sslBundles: SslBundles?,
        ): NfseKeyStore {
            val bundles =
                sslBundles ?: fail(
                    "${NfseProperties.Certificate.PREFIX}.ssl-bundle is set but no SslBundles bean is available",
                )
            val bundle =
                try {
                    bundles.getBundle(name)
                } catch (e: NoSuchSslBundleException) {
                    fail("No SSL bundle named '$name'; declare it under spring.ssl.bundle.*", e)
                }
            val keyStore =
                bundle.stores.keyStore
                    ?: fail("The SSL bundle '$name' has no keystore; it must carry the emitter's A1 certificate")
            return NfseKeyStore(keyStore, bundle.key.password.orEmpty(), bundle.key.alias)
        }

        private fun fromLocation(properties: NfseProperties.Certificate): NfseKeyStore {
            val location = requireNotNull(properties.location)
            val stream =
                try {
                    location.inputStream
                } catch (e: IOException) {
                    fail("Cannot read ${NfseProperties.Certificate.PREFIX}.location '$location'", e)
                }
            return NfseKeyStore(Pkcs12.open(stream, properties.password), properties.password, properties.alias)
        }

        private fun fromBase64(properties: NfseProperties.Certificate): NfseKeyStore {
            // `base64` on macOS and a forgotten `-w0` on Linux wrap the output, so the value arrives with newlines
            // in it; strip them and decode strictly. The MIME decoder would swallow the line breaks too, but it
            // also ignores every other illegal character, turning a mistyped value into "corrupt PKCS#12".
            val value = requireNotNull(properties.base64).filterNot(Char::isWhitespace)
            val bytes =
                runCatching { Base64.getDecoder().decode(value) }
                    .getOrElse { fail("${NfseProperties.Certificate.PREFIX}.base64 is not valid Base64", it) }
            return NfseKeyStore.ofPkcs12(bytes, properties.password, properties.alias)
        }

        /**
         * Structural checks only — "this is the wrong file" errors. The validity dates are deliberately not checked
         * here; see the class documentation.
         */
        private fun check(certificate: X509Certificate) {
            val subject = certificate.subjectX500Principal.name
            if (certificate.version != X509_VERSION) {
                fail("Certificate '$subject' is X.509 v${certificate.version}; v3 is required")
            }
            if (certificate.basicConstraints != NOT_A_CA) {
                fail("Certificate '$subject' is a CA certificate; use the emitter's A1 certificate")
            }
            val keyUsage = certificate.keyUsage
            if (keyUsage != null && !(keyUsage[KEY_USAGE_DIGITAL_SIGNATURE] && keyUsage[KEY_USAGE_NON_REPUDIATION])) {
                fail("Certificate '$subject' lacks the Digital Signature / Non Repudiation key usages")
            }
        }

        const val SSL_BUNDLE_SOURCE = "nfse.certificate.ssl-bundle"
        const val LOCATION_SOURCE = "nfse.certificate.location"
        const val BASE64_SOURCE = "nfse.certificate.base64"
        const val PROVIDER_SOURCE = "NfseCertificateProvider bean"

        /** Warn this far ahead of the expiry date, at startup and in the health indicator. */
        val EXPIRY_WARNING: Duration = Duration.ofDays(30)
    }
}
