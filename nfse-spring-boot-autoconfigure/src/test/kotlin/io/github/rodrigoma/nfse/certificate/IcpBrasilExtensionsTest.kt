package io.github.rodrigoma.nfse.certificate

import org.assertj.core.api.Assertions.assertThat
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.DEROctetString
import org.bouncycastle.asn1.DERSequence
import org.bouncycastle.asn1.DERTaggedObject
import org.junit.jupiter.api.Test

class IcpBrasilExtensionsTest {
    @Test
    fun `decodes an OtherName with an octet string value and long-form lengths`() {
        val value = "x".repeat(200)
        val der = otherName("2.16.76.1.3.1", value.toByteArray())
        assertThat(IcpBrasilExtensions.parseOtherName(der)).isEqualTo("2.16.76.1.3.1" to value)
    }

    @Test
    fun `decodes OIDs from every root arc`() {
        listOf("1.2.840.113549.1.9.1", "0.9.2342.19200300.100.1.25", "2.5.29.17").forEach { oid ->
            assertThat(IcpBrasilExtensions.parseOtherName(otherName(oid, byteArrayOf(1)))?.first).isEqualTo(oid)
        }
    }

    @Test
    fun `returns null for structures that are not OtherName`() {
        val notASequence = DEROctetString(byteArrayOf(1, 2)).encoded
        val noOid = DERSequence(arrayOf(DEROctetString(byteArrayOf(1)))).encoded
        val noTaggedValue = DERSequence(arrayOf(ASN1ObjectIdentifier("1.2.3"), DEROctetString(byteArrayOf(1)))).encoded
        assertThat(IcpBrasilExtensions.parseOtherName(notASequence)).isNull()
        assertThat(IcpBrasilExtensions.parseOtherName(noOid)).isNull()
        assertThat(IcpBrasilExtensions.parseOtherName(noTaggedValue)).isNull()
    }

    private fun otherName(
        oid: String,
        value: ByteArray,
    ): ByteArray =
        DERSequence(arrayOf(ASN1ObjectIdentifier(oid), DERTaggedObject(true, 0, DEROctetString(value)))).encoded
}
