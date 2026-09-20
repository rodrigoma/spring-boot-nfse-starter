package io.github.rodrigoma.nfse.certificate

import io.github.rodrigoma.nfse.model.dps.FederalId
import java.security.cert.X509Certificate

/**
 * Reads the CNPJ / CPF that ICP-Brasil certificates carry in the `subjectAltName` `otherName` entries
 * (OID `2.16.76.1.3.3` = CNPJ, OID `2.16.76.1.3.1` = CPF inside a fixed-layout string), using a minimal DER reader
 * so no ASN.1 library is needed at runtime.
 */
internal object IcpBrasilExtensions {
    const val CNPJ_OID = "2.16.76.1.3.3"
    const val CPF_OID = "2.16.76.1.3.1"

    private const val OTHER_NAME_TYPE = 0
    private const val TAG_SEQUENCE = 0x30
    private const val TAG_OID = 0x06
    private const val TAG_CONTEXT_0 = 0xA0
    private const val LONG_FORM_MASK = 0x80
    private const val LENGTH_MASK = 0x7F
    private const val OID_FIRST_ARC_BASE = 40
    private const val BASE_128_MASK = 0x7F
    private const val BASE_128_SHIFT = 7
    private const val CPF_OFFSET = 8
    private const val BYTE_MASK = 0xFF

    /**
     * The CNPJ (preferred) or CPF the certificate identifies, or `null` when neither extension is present or the
     * extensions cannot be read — a certificate with odd extensions must still be usable.
     */
    fun federalIdOf(certificate: X509Certificate): FederalId? {
        val otherNames =
            runCatching { certificate.subjectAlternativeNames.orEmpty() }
                .getOrDefault(emptyList())
                .filter { it.size >= 2 && it[0] == OTHER_NAME_TYPE && it[1] is ByteArray }
                .mapNotNull { runCatching { parseOtherName(it[1] as ByteArray) }.getOrNull() }
                .toMap()
        val cnpj =
            otherNames[CNPJ_OID]
                ?.uppercase()
                ?.filter(Char::isLetterOrDigit)
                ?.takeIf { it.length == FederalId.CNPJ_LENGTH }
        val cpf =
            otherNames[CPF_OID]
                ?.filter(Char::isDigit)
                ?.takeIf { it.length >= CPF_OFFSET + FederalId.CPF_LENGTH }
                ?.substring(CPF_OFFSET, CPF_OFFSET + FederalId.CPF_LENGTH)
        return cnpj?.let { FederalId.Cnpj(it) } ?: cpf?.let { FederalId.Cpf(it) }
    }

    /** `OtherName ::= SEQUENCE { type-id OBJECT IDENTIFIER, value [0] EXPLICIT ANY }` → (oid, value as text). */
    @Suppress("ReturnCount")
    internal fun parseOtherName(der: ByteArray): Pair<String, String>? {
        val reader = DerReader(der)
        if (reader.tag() != TAG_SEQUENCE) return null
        reader.length()
        if (reader.tag() != TAG_OID) return null
        val oid = decodeOid(reader.bytes(reader.length()))
        if (reader.tag() != TAG_CONTEXT_0) return null
        reader.length()
        reader.tag()
        val value = reader.bytes(reader.length())
        return oid to value.toString(Charsets.UTF_8)
    }

    private fun decodeOid(bytes: ByteArray): String {
        val values = mutableListOf<Long>()
        var accumulator = 0L
        for (byte in bytes) {
            val b = byte.toInt() and BYTE_MASK
            accumulator = (accumulator shl BASE_128_SHIFT) or (b and BASE_128_MASK).toLong()
            if (b and LONG_FORM_MASK == 0) {
                values += accumulator
                accumulator = 0
            }
        }
        if (values.isEmpty()) return ""
        val first = values.first()
        val head =
            when {
                first < OID_FIRST_ARC_BASE -> listOf(0L, first)
                first < OID_FIRST_ARC_BASE * 2 -> listOf(1L, first - OID_FIRST_ARC_BASE)
                else -> listOf(2L, first - OID_FIRST_ARC_BASE * 2)
            }
        return (head + values.drop(1)).joinToString(".")
    }

    private class DerReader(
        private val data: ByteArray,
    ) {
        private var position = 0

        fun tag(): Int = data[position++].toInt() and BYTE_MASK

        fun length(): Int {
            val first = data[position++].toInt() and BYTE_MASK
            if (first and LONG_FORM_MASK == 0) return first
            var length = 0
            repeat(first and LENGTH_MASK) {
                length = (length shl Byte.SIZE_BITS) or (data[position++].toInt() and BYTE_MASK)
            }
            return length
        }

        fun bytes(count: Int): ByteArray = data.copyOfRange(position, position + count).also { position += count }
    }
}
