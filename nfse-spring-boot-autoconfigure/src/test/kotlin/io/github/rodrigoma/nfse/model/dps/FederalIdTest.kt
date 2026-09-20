package io.github.rodrigoma.nfse.model.dps

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class FederalIdTest {
    @Test
    fun `strips punctuation and exposes the CNPJ base`() {
        val cnpj = FederalId.Cnpj("12.345.678/0001-95")
        assertThat(cnpj.value).isEqualTo("12345678000195")
        assertThat(cnpj.base).isEqualTo("12345678")
        assertThat(FederalId.Cpf("123.456.789-09").value).isEqualTo("12345678909")
    }

    @Test
    fun `accepts the alphanumeric CNPJ and normalises case`() {
        assertThat(FederalId.Cnpj("12.abc.345/01de-35").value).isEqualTo("12ABC34501DE35")
        assertThat(FederalId.cnpjOrCpf("12ABC34501DE35")).isEqualTo(FederalId.Cnpj("12abc34501de35"))
        assertThatThrownBy { FederalId.Cnpj("12ABC34501DE3ç") }.hasMessageContaining("CNPJ")
    }

    @Test
    fun `cnpjOrCpf picks by digit count`() {
        assertThat(FederalId.cnpjOrCpf("12345678000195")).isInstanceOf(FederalId.Cnpj::class.java)
        assertThat(FederalId.cnpjOrCpf("12345678909")).isInstanceOf(FederalId.Cpf::class.java)
        assertThatThrownBy { FederalId.cnpjOrCpf("123") }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `validates lengths`() {
        assertThatThrownBy { FederalId.Cnpj("123") }.hasMessageContaining("CNPJ")
        assertThatThrownBy { FederalId.Cpf("123") }.hasMessageContaining("CPF")
        assertThatThrownBy { FederalId.Nif("") }.hasMessageContaining("NIF")
        assertThat(FederalId.NoNif(NoNifReason.EXEMPT).reason.code).isEqualTo("1")
    }
}
