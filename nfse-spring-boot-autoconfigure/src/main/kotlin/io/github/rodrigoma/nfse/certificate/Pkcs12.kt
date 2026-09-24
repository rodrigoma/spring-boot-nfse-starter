package io.github.rodrigoma.nfse.certificate

import io.github.rodrigoma.nfse.exception.NfseException
import java.io.IOException
import java.io.InputStream
import java.security.GeneralSecurityException
import java.security.KeyStore

/**
 * Opens PKCS#12 material from a stream — never from a temporary file. Materialising the certificate on disk would
 * only add surface: it survives a crash, shows up for anyone with a shell in the container, and buys nothing.
 */
internal object Pkcs12 {
    fun open(
        pkcs12: ByteArray,
        password: String,
    ): KeyStore = open(pkcs12.inputStream(), password)

    fun open(
        stream: InputStream,
        password: String,
    ): KeyStore =
        try {
            stream.use { input -> KeyStore.getInstance("PKCS12").apply { load(input, password.toCharArray()) } }
        } catch (e: IOException) {
            // The JCE reports a bad password as an IOException with a BadPaddingException cause.
            throw NfseException.Certificate("Cannot open the PKCS#12 certificate: wrong password or corrupt file", e)
        } catch (e: GeneralSecurityException) {
            throw NfseException.Certificate("Cannot open the PKCS#12 certificate: ${e.message}", e)
        }
}
