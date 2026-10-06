package io.github.rodrigoma.nfse.danfse.layout

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class QrCodeTest {
    @Test
    fun `points at the environment where the note actually exists`() {
        // NT 008 names only the production address; a tpAmb = 2 note lives solely in restricted production, so
        // the prescribed URL would answer "not found" to whoever scans the test document.
        assertThat(QrCode.queryUrl(restrictedProduction = false))
            .isEqualTo("https://www.nfse.gov.br/ConsultaPublica/?tpc=1&chave=")
        assertThat(QrCode.queryUrl(restrictedProduction = true))
            .isEqualTo("https://www.producaorestrita.nfse.gov.br/ConsultaPublica/?tpc=1&chave=")
    }
}
