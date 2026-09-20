package io.github.rodrigoma.nfse.model.dps

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class TaxIdCheckDigitsTest {
    @Test
    fun `validates CPF check digits`() {
        assertThat(TaxIdCheckDigits.isValidCpf("12345678909")).isTrue()
        assertThat(TaxIdCheckDigits.isValidCpf("01075595363")).isTrue()
        assertThat(TaxIdCheckDigits.isValidCpf("12345678900")).isFalse()
        assertThat(TaxIdCheckDigits.isValidCpf("11111111111")).isFalse()
        assertThat(TaxIdCheckDigits.isValidCpf("1234567890")).isFalse()
        assertThat(TaxIdCheckDigits.isValidCpf("1234567890A")).isFalse()
    }

    @Test
    fun `validates numeric and alphanumeric CNPJ check digits`() {
        assertThat(TaxIdCheckDigits.isValidCnpj("12345678000195")).isTrue()
        assertThat(TaxIdCheckDigits.isValidCnpj("00574753000100")).isTrue()
        assertThat(TaxIdCheckDigits.isValidCnpj("11222333000181")).isTrue()
        // Alphanumeric example published with IN RFB 2.229/2024
        assertThat(TaxIdCheckDigits.isValidCnpj("12ABC34501DE35")).isTrue()
        assertThat(TaxIdCheckDigits.isValidCnpj("12ABC34501DE36")).isFalse()
        assertThat(TaxIdCheckDigits.isValidCnpj("12345678000196")).isFalse()
        assertThat(TaxIdCheckDigits.isValidCnpj("00000000000000")).isFalse()
        assertThat(TaxIdCheckDigits.isValidCnpj("1234567800019")).isFalse()
        assertThat(TaxIdCheckDigits.isValidCnpj("12ABC34501DEAB")).isFalse()
    }
}
