package io.github.rodrigoma.nfse.xml

import io.github.rodrigoma.nfse.model.event.NfseEventType
import io.github.rodrigoma.nfse.support.TestXml
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class NfseXmlParserTest {
    @Test
    fun `reads the NFS-e header and amounts`() {
        val nfse = NfseXmlParser.parseNfse(TestXml.nfse())

        assertThat(nfse.accessKey).isEqualTo(TestXml.ACCESS_KEY)
        assertThat(nfse.number).isEqualTo("123")
        assertThat(nfse.statusCode).isEqualTo("100")
        assertThat(nfse.processedAt).isEqualTo("2026-09-19T13:00:00Z".let { java.time.OffsetDateTime.parse(it) })
        assertThat(nfse.emitterMunicipality).isEqualTo("São Paulo")
        assertThat(nfse.dpsId).isEqualTo("DPS355030821234567800019500001000000000000001")
        assertThat(nfse.netAmount).isEqualByComparingTo(BigDecimal("98.00"))
        assertThat(nfse.calculationBase).isEqualByComparingTo(BigDecimal("100.00"))
        assertThat(nfse.appliedRate).isEqualByComparingTo(BigDecimal("2.00"))
        assertThat(nfse.issqnAmount).isEqualByComparingTo(BigDecimal("2.00"))
        assertThat(nfse.xml).isEqualTo(TestXml.nfse())
    }

    @Test
    fun `reads a cancellation event`() {
        val event = NfseXmlParser.parseEvent(TestXml.event())

        assertThat(event.id).isEqualTo("EVT${TestXml.ACCESS_KEY}101101001")
        assertThat(event.typeCode).isEqualTo("101101")
        assertThat(event.type).isEqualTo(NfseEventType.CANCELLATION)
        assertThat(event.sequence).isEqualTo(1)
        assertThat(event.accessKey).isEqualTo(TestXml.ACCESS_KEY)
        assertThat(event.reasonCode).isEqualTo("1")
        assertThat(event.justification).isEqualTo("Nota emitida com valor incorreto")
        assertThat(event.processedAt).isNotNull()
    }

    @Test
    fun `rejects documents of the wrong kind`() {
        assertThatThrownBy { NfseXmlParser.parseNfse("<x/>") }.hasMessageContaining("infNFSe")
        assertThatThrownBy { NfseXmlParser.parseEvent("<x/>") }.hasMessageContaining("infEvento")
    }

    @Test
    fun `unknown event codes yield a null type`() {
        assertThat(NfseEventType.fromCode("e999999")).isNull()
        assertThat(NfseEventType.fromCode("e105102")).isEqualTo(NfseEventType.CANCELLATION_BY_SUBSTITUTION)
        assertThat(NfseEventType.CANCELLATION.elementName).isEqualTo("e101101")
    }
}
