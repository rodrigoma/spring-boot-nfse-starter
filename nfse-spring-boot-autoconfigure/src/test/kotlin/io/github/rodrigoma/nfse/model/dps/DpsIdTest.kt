package io.github.rodrigoma.nfse.model.dps

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class DpsIdTest {
    @Test
    fun `formats DPS + IBGE(7) + type(1) + federal id(14) + series(5) + number(15)`() {
        val id = DpsId(3550308, FederalId.Cnpj("12.345.678/0001-95"), 1, 42)
        assertThat(id.value).isEqualTo("DPS355030821234567800019500001000000000000042")
        assertThat(id.value).hasSize(45)
        assertThat(id.digits).hasSize(42)
        assertThat(id.toString()).isEqualTo(id.value)
    }

    @Test
    fun `left-pads a CPF with zeros and uses type 1`() {
        val id = DpsId(5300108, FederalId.Cpf("123.456.789-09"), 12345, 999_999_999_999_999)
        assertThat(id.value).isEqualTo("DPS5300108100012345678909" + "12345" + "999999999999999")
    }

    @Test
    fun `rejects foreign ids and out-of-range values`() {
        assertThatThrownBy { DpsId(3550308, FederalId.Nif("X"), 1, 1) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { DpsId(3550308, FederalId.Cnpj("12345678000195"), 0, 1) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { DpsId(3550308, FederalId.Cnpj("12345678000195"), 1, 0) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { DpsId(10_000_000, FederalId.Cnpj("12345678000195"), 1, 1) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }
}
