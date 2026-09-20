package io.github.rodrigoma.nfse.xml

import io.github.rodrigoma.nfse.autoconfigure.NfseEnvironment
import io.github.rodrigoma.nfse.model.dps.FederalId
import io.github.rodrigoma.nfse.model.event.CancellationReason
import io.github.rodrigoma.nfse.support.TestCertificates
import io.github.rodrigoma.nfse.support.TestDps
import io.github.rodrigoma.nfse.support.TestXml
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class EventXmlBuilderTest {
    private val accessKey = TestXml.ACCESS_KEY

    private fun request(author: FederalId = FederalId.Cnpj(TestDps.CNPJ)) =
        CancellationRequest(
            environment = NfseEnvironment.RESTRICTED_PRODUCTION,
            applicationVersion = "test/1.0",
            eventAt = TestDps.issuedAt,
            author = author,
            accessKey = accessKey,
            reason = CancellationReason.ISSUANCE_ERROR,
            justification = "Nota emitida com valor incorreto",
        )

    @Test
    fun `builds a schema-valid cancellation request with the PRE identifier`() {
        val document = EventXmlBuilder().buildCancellation(request())
        val xml = XmlSupport.serialize(document)

        assertThat(request().id).isEqualTo("PRE$accessKey" + "101101").hasSize(59)
        assertThat(xml).isEqualTo(
            """<?xml version="1.0" encoding="UTF-8"?>""" +
                """<pedRegEvento versao="1.01" xmlns="http://www.sped.fazenda.gov.br/nfse">""" +
                """<infPedReg Id="PRE${accessKey}101101">""" +
                "<tpAmb>2</tpAmb><verAplic>test/1.0</verAplic><dhEvento>2026-09-19T10:00:00-03:00</dhEvento>" +
                "<CNPJAutor>12345678000195</CNPJAutor><chNFSe>$accessKey</chNFSe>" +
                "<e101101><xDesc>Cancelamento de NFS-e</xDesc><cMotivo>1</cMotivo>" +
                "<xMotivo>Nota emitida com valor incorreto</xMotivo></e101101>" +
                "</infPedReg></pedRegEvento>",
        )
    }

    @Test
    fun `signs infPedReg and still validates`() {
        val identity = TestCertificates.emitter()
        val document = EventXmlBuilder().buildCancellation(request(FederalId.Cpf("12345678909")))
        XmlSigner(identity.keyPair.private, identity.certificate)
            .sign(document, EventXmlBuilder.infPedReg(document), document.documentElement)
        assertThat(XmlSupport.serialize(document)).contains("<CPFAutor>12345678909</CPFAutor>")
        assertThat(XsdValidator.eventRequest().validate(document)).isEmpty()
        assertThat(XmlSigner.verify(document)).isTrue()
    }

    @Test
    fun `validates its inputs`() {
        assertThatThrownBy { request().copy(justification = "curta") }.hasMessageContaining("15")
        assertThatThrownBy { request().copy(accessKey = "123") }.hasMessageContaining("50")
        assertThatThrownBy { request(FederalId.Nif("X")) }.hasMessageContaining("CNPJ or a CPF")
    }
}
