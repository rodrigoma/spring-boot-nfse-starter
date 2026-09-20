package io.github.rodrigoma.nfse.xml

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.Base64

class GzipBase64Test {
    @Test
    fun `round-trips UTF-8 text`() {
        val xml = "<DPS><xNome>Açaí &amp; Cia</xNome></DPS>"
        val encoded = GzipBase64.encode(xml)
        assertThat(encoded).isBase64()
        assertThat(Base64.getDecoder().decode(encoded).take(2)).containsExactly(0x1f.toByte(), 0x8b.toByte())
        assertThat(GzipBase64.decode(encoded)).isEqualTo(xml)
        assertThat(GzipBase64.decode(" $encoded\n")).isEqualTo(xml)
    }
}
