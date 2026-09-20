package io.github.rodrigoma.nfse.xml

import io.github.rodrigoma.nfse.support.TestCertificates
import io.github.rodrigoma.nfse.support.TestDps
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class XmlSignerTest {
    private val identity = TestCertificates.emitter()
    private val signer = XmlSigner(identity.keyPair.private, identity.certificate)

    @Test
    fun `signs infDPS with an enveloped RSA-SHA256 signature that verifies and stays schema-valid`() {
        val document = DpsXmlBuilder().build(TestDps.minimal())
        signer.sign(document, DpsXmlBuilder.infDps(document), document.documentElement)
        val xml = XmlSupport.serialize(document)

        assertThat(xml).contains("<Signature xmlns=\"http://www.w3.org/2000/09/xmldsig#\">")
        assertThat(xml).doesNotContain("ds:")
        assertThat(xml).contains("Algorithm=\"http://www.w3.org/2001/04/xmldsig-more#rsa-sha256\"")
        assertThat(xml).contains("Algorithm=\"http://www.w3.org/2001/04/xmlenc#sha256\"")
        assertThat(xml).contains("Algorithm=\"http://www.w3.org/TR/2001/REC-xml-c14n-20010315\"")
        assertThat(xml).contains("URI=\"#DPS355030821234567800019500001000000000000001\"")
        assertThat(xml).contains("<X509Certificate>")
        assertThat(xml).endsWith("</Signature></DPS>")
        assertThat(XsdValidator.dps().validate(document)).isEmpty()
        assertThat(XmlSigner.verify(XmlSupport.parse(xml))).isTrue()
    }

    @Test
    fun `verification fails when the signed content is tampered with`() {
        val document = DpsXmlBuilder().build(TestDps.minimal())
        signer.sign(document, DpsXmlBuilder.infDps(document), document.documentElement)
        val tampered = XmlSupport.serialize(document).replace("<vServ>100.00</vServ>", "<vServ>1.00</vServ>")

        assertThat(XmlSigner.verify(XmlSupport.parse(tampered))).isFalse()
        assertThat(XmlSigner.verify(DpsXmlBuilder().build(TestDps.minimal()))).isFalse()
    }

    @Test
    fun `refuses to sign an element without Id`() {
        val document = DpsXmlBuilder().build(TestDps.minimal())
        assertThatThrownBy { signer.sign(document, document.documentElement, document.documentElement) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }
}
