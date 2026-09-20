package io.github.rodrigoma.nfse.sample.local

import io.github.rodrigoma.nfse.xml.GzipBase64
import java.time.OffsetDateTime

private const val CANCELLATION = "101101"

/** The ADN side of the sandbox: PascalCase distribution responses and the parametrização service. */
internal class LocalAdn(
    private val store: LocalSefinStore,
) {
    /** ADN shape: PascalCase `LoteDistribuicaoNSUResponse`; 404 carries the body when nothing is found. */
    fun events(accessKey: String): Reply {
        val note = store.find(accessKey) ?: return notFound("Chave de acesso não encontrada.")
        val items =
            note.events.mapIndexed {
                index,
                event,
                ->
                distributionItem(index + 1L, note, event.xml, "EVENTO", event.typeCode)
            }
        return distributionReply(items)
    }

    fun distribution(nsu: Long): Reply {
        val items =
            store.documentsFrom(nsu).map { distributionItem(it.nsu, it.note, it.xml, it.type, it.eventTypeCode) }
        return distributionReply(items)
    }

    private fun distributionReply(items: List<Map<String, Any?>>): Reply {
        val status = if (items.isEmpty()) "NENHUM_DOCUMENTO_LOCALIZADO" else "DOCUMENTOS_LOCALIZADOS"
        val body =
            mapOf(
                "StatusProcessamento" to status,
                "LoteDFe" to items,
                "Alertas" to emptyList<Any>(),
                "Erros" to emptyList<Any>(),
                "TipoAmbiente" to "HOMOLOGACAO",
                "VersaoAplicativo" to "LocalSefin/1.0",
                "DataHoraProcessamento" to OffsetDateTime.now().toString(),
            )
        return json(if (items.isEmpty()) HTTP_NOT_FOUND else HTTP_OK, body)
    }

    private fun distributionItem(
        nsu: Long,
        note: StoredNote,
        xml: String,
        type: String,
        eventTypeCode: String?,
    ): Map<String, Any?> =
        mapOf(
            "NSU" to nsu,
            "ChaveAcesso" to note.accessKey,
            "TipoDocumento" to type,
            "TipoEvento" to eventTypeCode?.let { if (it == CANCELLATION) "CANCELAMENTO" else it },
            "ArquivoXml" to GzipBase64.encode(xml),
            "DataHoraGeracao" to OffsetDateTime.now().toString(),
        )

    /** ADN Parâmetros Municipais: `{ mensagem, <payload> }` per endpoint; a 404 carries `mensagem`. */
    fun parameters(
        municipality: String,
        rest: String,
    ): Reply {
        val payload: Map<String, Any?>? =
            when {
                rest == "convenio" ->
                    mapOf(
                        "parametrosConvenio" to
                            mapOf(
                                "codigoMunicipio" to municipality,
                                "aderenteAmbienteNacional" to 1,
                                "aderenteEmissorNacional" to 1,
                            ),
                    )
                rest.endsWith("/aliquota") ->
                    mapOf(
                        "aliquotas" to
                            mapOf(rest.substringBefore('/') to listOf(mapOf("Incidencia" to "1", "Aliq" to 2.0))),
                    )
                rest.endsWith("/historicoaliquotas") -> mapOf("aliquotas" to emptyMap<String, Any>())
                rest.endsWith("/regimes_especiais") -> mapOf("regimesEspeciais" to emptyMap<String, Any>())
                rest.endsWith(
                    "/retencoes",
                ) -> mapOf("retencoes" to mapOf("artigoSexto" to mapOf("habilitado" to false)))
                else -> null
            }
        return payload?.let { json(HTTP_OK, mapOf("mensagem" to null) + it) }
            ?: json(HTTP_NOT_FOUND, mapOf("mensagem" to "Parâmetro não encontrado para o município"))
    }
}
