package io.github.rodrigoma.nfse.danfse.layout

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class FormatsTest {
    @Test
    fun `formats money, percentages, dates and masks the Brazilian way`() {
        assertThat(Formats.money("1234.5")).isEqualTo("R$ 1.234,50")
        assertThat(Formats.money(BigDecimal("0"))).isEqualTo("R$ 0,00")
        assertThat(Formats.money("abc")).isEqualTo("-")
        assertThat(Formats.money(null as String?)).isEqualTo("-")
        assertThat(Formats.percent("2")).isEqualTo("2,00 %")
        assertThat(Formats.percent(null)).isEqualTo("-")
        assertThat(Formats.date("2026-09-20")).isEqualTo("20/09/2026")
        assertThat(Formats.date("bad")).isEqualTo("-")
        assertThat(Formats.dateTime("2026-09-20T13:07:42-03:00")).isEqualTo("20/09/2026 13:07:42")
        assertThat(Formats.dateTime(null)).isEqualTo("-")
        assertThat(Formats.cnpj("12345678000195")).isEqualTo("12.345.678/0001-95")
        assertThat(Formats.cnpj("123")).isEqualTo("123")
        assertThat(Formats.cpf("12345678909")).isEqualTo("123.456.789-09")
        assertThat(Formats.cpf("1")).isEqualTo("1")
        assertThat(Formats.cep("01310100")).isEqualTo("01310-100")
        assertThat(Formats.cep("x")).isEqualTo("x")
        assertThat(Formats.nationalTaxCode("010701")).isEqualTo("01.07.01")
        assertThat(Formats.nationalTaxCode("1")).isEqualTo("1")
        assertThat(Formats.nbs("115011000")).isEqualTo("1.1501.10.00")
        assertThat(Formats.nbs("1")).isEqualTo("1")
        assertThat(Formats.orDash(" ")).isEqualTo("-")
        assertThat(Formats.truncate("abcdef", 4)).isEqualTo("abc…")
        assertThat(Formats.truncate("abc", 4)).isEqualTo("abc")
    }

    @Test
    fun `looks municipalities and countries up`() {
        assertThat(Places.municipalityLabel("3548807")).isEqualTo("São Caetano do Sul / SP")
        assertThat(Places.municipalityLabel("0000000")).isEqualTo("0000000")
        assertThat(Places.municipalityLabel(null)).isNull()
        assertThat(Places.municipality("2111300")?.uf).isEqualTo("MA")
        assertThat(Places.country("br")).isEqualTo("Brasil")
        assertThat(Places.country("XX")).isEqualTo("XX")
        assertThat(Descriptions.of(Descriptions.status, "999")).isEqualTo("999")
        assertThat(Descriptions.of(Descriptions.status, null)).isNull()
    }
}
