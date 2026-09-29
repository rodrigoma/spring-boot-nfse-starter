package io.github.rodrigoma.nfse.client

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class ServiceCodesTest {
    @Test
    fun `accepts both spellings of the complete code`() {
        // 01.09.02 (conteúdos de vídeo, imagem e texto pela internet) + municipal complement 001
        assertThat(ServiceCodes.complete("010902001")).isEqualTo("01.09.02.001")
        assertThat(ServiceCodes.complete("01.09.02.001")).isEqualTo("01.09.02.001")
    }

    @Test
    fun `refuses the cTribNac alone, which is what a caller reaches for first`() {
        assertThatThrownBy { ServiceCodes.complete("010902") }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("01.09.02.001")
            .hasMessageContaining("6 digits")
            .hasMessageContaining("cTribNac alone")
    }

    @Test
    fun `refuses anything that is not nine digits, or that carries other characters`() {
        assertThatThrownBy { ServiceCodes.complete("0109020011") }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("10 digits")
        assertThatThrownBy { ServiceCodes.complete("") }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { ServiceCodes.complete("01.09.02-001") }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("digits and dots")
    }

    @Test
    fun `normalises however the dots were placed`() {
        // The service refuses these spellings; normalising keeps the caller out of that trap.
        assertThat(ServiceCodes.complete("010902.001")).isEqualTo("01.09.02.001")
        assertThat(ServiceCodes.complete("01.09.02001")).isEqualTo("01.09.02.001")
    }
}
