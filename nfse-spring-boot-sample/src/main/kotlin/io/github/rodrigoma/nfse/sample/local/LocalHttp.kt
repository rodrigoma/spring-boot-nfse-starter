package io.github.rodrigoma.nfse.sample.local

import tools.jackson.databind.json.JsonMapper
import java.time.OffsetDateTime

internal const val HTTP_OK = 200
internal const val HTTP_CREATED = 201
internal const val HTTP_BAD_REQUEST = 400
internal const val HTTP_NOT_FOUND = 404

private val mapper = JsonMapper.builder().build()

internal class Reply(
    val status: Int,
    val body: ByteArray = ByteArray(0),
    val contentType: String = "application/json",
)

internal fun json(
    status: Int,
    value: Any,
): Reply = Reply(status, mapper.writeValueAsBytes(value))

/** Sefin header fields (camelCase, integer `tipoAmbiente`). */
internal fun header(): Map<String, Any?> =
    mapOf(
        "tipoAmbiente" to 2,
        "versaoAplicativo" to "LocalSefin/1.0",
        "dataHoraProcessamento" to OffsetDateTime.now().toString(),
    )

/** `ResponseErro` — the single-object shape every non-POST endpoint uses. */
internal fun notFound(description: String): Reply =
    json(HTTP_NOT_FOUND, header() + mapOf("erro" to mapOf("codigo" to "E1234", "descricao" to description)))
