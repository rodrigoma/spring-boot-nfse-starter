package io.github.rodrigoma.nfse.xml

import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/** The API carries every XML as GZip-compressed, Base64-encoded UTF-8 text (`…XmlGZipB64` fields). */
object GzipBase64 {
    fun encode(xml: String): String {
        val bytes = ByteArrayOutputStream()
        GZIPOutputStream(bytes).use { it.write(xml.toByteArray(Charsets.UTF_8)) }
        return Base64.getEncoder().encodeToString(bytes.toByteArray())
    }

    fun decode(value: String): String =
        GZIPInputStream(Base64.getDecoder().decode(value.trim()).inputStream()).use {
            it.readBytes().toString(Charsets.UTF_8)
        }
}
