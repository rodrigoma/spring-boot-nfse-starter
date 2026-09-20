package io.github.rodrigoma.nfse.http

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class NfseBodyMaskerTest {
    @Test
    fun `replaces compressed XML payloads by their size and keeps the rest`() {
        val body =
            """{"dpsXmlGZipB64":"H4sIAAAA","chaveAcesso":"123",""" +
                """"erros":[{"codigo":"E1","eventoXmlGZipB64":"abcd"}]}"""
        val masked = NfseBodyMasker.mask(body.toByteArray())
        assertThat(masked).contains("\"dpsXmlGZipB64\":\"<8 chars gzip+base64>\"")
        assertThat(masked).contains("\"eventoXmlGZipB64\":\"<4 chars gzip+base64>\"")
        assertThat(masked).contains("\"chaveAcesso\":\"123\"").contains("\"codigo\":\"E1\"")
    }

    @Test
    fun `reports non-JSON and empty bodies by size`() {
        assertThat(NfseBodyMasker.mask("%PDF-1.4".toByteArray())).isEqualTo("<8 bytes, not JSON>")
        assertThat(NfseBodyMasker.mask(ByteArray(0))).isEmpty()
        assertThat(NfseBodyMasker.mask("[1,2]".toByteArray())).isEqualTo("[1,2]")
    }
}
