package io.github.rodrigoma.nfse.certificate

import io.github.rodrigoma.nfse.autoconfigure.NfseProperties
import io.github.rodrigoma.nfse.exception.NfseException
import io.github.rodrigoma.nfse.model.dps.FederalId
import io.github.rodrigoma.nfse.xml.XmlSigner
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.CertificateExpiredException
import java.security.cert.CertificateNotYetValidException
import java.security.cert.X509Certificate
import java.util.Base64

/**
 * The emitter's ICP-Brasil A1 certificate (PKCS#12), loaded and checked once at startup.
 *
 * Checks follow Anexo I: X.509 v3, not a CA certificate, KeyUsage with *Digital Signature* and *Non Repudiation*
 * (when the extension is present), and validity dates. The same key signs the XML and opens the mTLS connection.
 * The password and the key material never appear in `toString()`.
 */
class NfseCertificate private constructor(
    val privateKey: PrivateKey,
    val certificate: X509Certificate,
    val chain: List<X509Certificate>,
    internal val keyStore: KeyStore,
    private val password: CharArray,
) {
    /** CNPJ or CPF read from the ICP-Brasil extensions, or `null` for certificates without them (e.g. test ones). */
    val federalId: FederalId? by lazy { IcpBrasilExtensions.federalIdOf(certificate) }

    val subject: String get() = certificate.subjectX500Principal.name

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

        /** Loads from `nfse.certificate.*` — exactly one of `pfx-path` / `pfx-base64` must be set. */
        fun load(properties: NfseProperties.Certificate): NfseCertificate {
            val bytes =
                when {
                    properties.pfxPath != null ->
                        runCatching { Files.readAllBytes(Path.of(properties.pfxPath)) }
                            .getOrElse { fail("Cannot read nfse.certificate.pfx-path '${properties.pfxPath}'", it) }
                    properties.pfxBase64 != null ->
                        runCatching { Base64.getDecoder().decode(properties.pfxBase64.filterNot(Char::isWhitespace)) }
                            .getOrElse { fail("nfse.certificate.pfx-base64 is not valid Base64", it) }
                    else -> fail("Set nfse.certificate.pfx-path or nfse.certificate.pfx-base64")
                }
            return load(bytes, properties.password)
        }

        /** Loads a PKCS#12 blob and runs the ICP-Brasil checks. */
        fun load(
            pkcs12: ByteArray,
            password: String,
        ): NfseCertificate {
            val chars = password.toCharArray()
            val keyStore =
                try {
                    KeyStore.getInstance("PKCS12").apply { load(pkcs12.inputStream(), chars) }
                } catch (e: IOException) {
                    fail("Cannot open the PKCS#12 certificate: wrong password or corrupt file", e)
                } catch (e: GeneralSecurityException) {
                    fail("Cannot open the PKCS#12 certificate: ${e.message}", e)
                }
            val alias =
                keyStore.aliases().toList().firstOrNull { keyStore.isKeyEntry(it) }
                    ?: fail("The PKCS#12 file contains no private key entry")
            val key = keyStore.getKey(alias, chars) as? PrivateKey ?: fail("Entry '$alias' has no private key")
            val chain = keyStore.getCertificateChain(alias).orEmpty().filterIsInstance<X509Certificate>()
            val certificate = chain.firstOrNull() ?: fail("Entry '$alias' has no X.509 certificate")
            check(certificate)
            return NfseCertificate(key, certificate, chain, keyStore, chars)
        }

        private fun check(certificate: X509Certificate) {
            val subject = certificate.subjectX500Principal.name
            try {
                certificate.checkValidity()
            } catch (e: CertificateExpiredException) {
                fail("Certificate '$subject' expired on ${certificate.notAfter}", e)
            } catch (e: CertificateNotYetValidException) {
                fail("Certificate '$subject' is not valid before ${certificate.notBefore}", e)
            }
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
    }
}
