package io.github.rodrigoma.nfse.certificate

import java.security.KeyStore

/**
 * Supplies the emitter's certificate from wherever the application keeps it — HashiCorp Vault, AWS/GCP KMS,
 * Secrets Manager, a custom store. A bean of this type **wins over `nfse.certificate.*`**, which is then not read
 * at all, so the library never needs to know about any vault.
 *
 * ```kotlin
 * @Bean
 * fun nfseCertificateProvider(vault: VaultTemplate) =
 *     NfseCertificateProvider {
 *         val pkcs12 = vault.read("secret/nfse").data["pfx"] as ByteArray
 *         NfseKeyStore.ofPkcs12(pkcs12, password)
 *     }
 * ```
 *
 * The certificate is read once, while the context starts; rotating it means restarting the application.
 */
fun interface NfseCertificateProvider {
    fun provide(): NfseKeyStore
}

/**
 * A loaded PKCS#12 (or any [KeyStore] holding the emitter's private key) and what is needed to open it. The
 * password never appears in [toString].
 *
 * @property alias Key entry to use; the first private-key entry when `null`.
 */
class NfseKeyStore(
    val keyStore: KeyStore,
    val password: String,
    val alias: String? = null,
) {
    override fun toString(): String = "NfseKeyStore(type=${keyStore.type}, alias=$alias, password=<hidden>)"

    companion object {
        /** Opens a PKCS#12 blob — the shape a vault usually hands back. Nothing is written to disk. */
        fun ofPkcs12(
            pkcs12: ByteArray,
            password: String,
            alias: String? = null,
        ): NfseKeyStore = NfseKeyStore(Pkcs12.open(pkcs12, password), password, alias)
    }
}
