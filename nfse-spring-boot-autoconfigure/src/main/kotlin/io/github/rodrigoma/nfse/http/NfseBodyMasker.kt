package io.github.rodrigoma.nfse.http

import tools.jackson.core.JacksonException
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.node.ObjectNode

/**
 * Renders an HTTP body for logging. The `…XmlGZipB64` payloads (which carry the CNPJ/CPF, names and addresses of
 * the parties inside the compressed XML) are replaced by their size; everything else is logged as is. Non-JSON
 * bodies (the DANFSE PDF) are reported by size only.
 */
object NfseBodyMasker {
    private const val PAYLOAD_SUFFIX = "xmlgzipb64"
    private val mapper: JsonMapper = JsonMapper.builder().build()

    fun mask(body: ByteArray): String =
        when {
            body.isEmpty() -> ""
            else ->
                parse(body)?.let { root ->
                    maskNode(root)
                    mapper.writeValueAsString(root)
                } ?: "<${body.size} bytes, not JSON>"
        }

    private fun parse(body: ByteArray): JsonNode? =
        try {
            mapper.readTree(body)
        } catch (_: JacksonException) {
            null
        }

    private fun maskNode(node: JsonNode) {
        when {
            node.isObject -> maskObject(node as ObjectNode)
            node.isArray -> node.values().forEach { maskNode(it) }
        }
    }

    private fun maskObject(node: ObjectNode) {
        node.propertyNames().toList().forEach { key ->
            val child = node.get(key)
            if (key.lowercase().endsWith(PAYLOAD_SUFFIX) && child.isTextual) {
                node.put(key, "<${child.asString().length} chars gzip+base64>")
            } else {
                maskNode(child)
            }
        }
    }
}
