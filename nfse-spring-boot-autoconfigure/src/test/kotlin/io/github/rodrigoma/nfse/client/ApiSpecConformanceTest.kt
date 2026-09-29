package io.github.rodrigoma.nfse.client

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.nio.file.Files
import java.nio.file.Path

/**
 * Keeps the client honest against the OpenAPI documents of the four services, kept under `docs/specs/` and
 * refreshed with `scripts/fetch-swagger.sh`.
 *
 * This does **not** reach the network: it compares the code with the specs that are committed. Refreshing the
 * specs is therefore enough to turn a change on the government's side into a failing build — which is the point,
 * since the Swagger is only reachable with an ICP-Brasil certificate and cannot be polled from CI.
 */
class ApiSpecConformanceTest {
    private val mapper = JsonMapper.builder().build()

    private val specsDirectory: Path =
        Path.of(System.getProperty("nfse.specs.dir") ?: "../docs/specs").also {
            check(Files.isDirectory(it)) { "docs/specs not found at ${it.toAbsolutePath()}" }
        }

    private fun spec(name: String): JsonNode =
        mapper.readTree(Files.readString(specsDirectory.resolve("$name.openapi.json")))

    /** Paths of the document, with the placeholder names dropped: only the shape is part of the contract. */
    private fun JsonNode.paths(): Set<String> = this["paths"].propertyNames().map { it.normalizePath() }.toSet()

    /** `components.schemas` (OpenAPI 3, the ADN) or `definitions` (Swagger 2, the Sefin). */
    private fun JsonNode.schema(name: String): JsonNode {
        val holder = this["components"]?.get("schemas") ?: this["definitions"]
        return requireNotNull(holder?.get(name)) { "schema $name is missing from the spec" }
    }

    private fun JsonNode.propertiesOf(schema: String): Set<String> =
        schema(schema)["properties"].propertyNames().toSet()

    /** `/nfse/{chaveAcesso}` and `/nfse/{id}` describe the same endpoint; only the segments matter. */
    private fun String.normalizePath(): String = PLACEHOLDER.replace(this, "{}").lowercase()

    /** Declared fields of a Kotlin data class are its properties, without needing kotlin-reflect. */
    private fun fieldsOf(type: Class<*>): Set<String> =
        type.declaredFields
            .filterNot { it.isSynthetic }
            .map { it.name }
            .toSet()

    private fun assertBinds(
        spec: JsonNode,
        schema: String,
        type: Class<*>,
        ignored: Set<String> = emptySet(),
    ) {
        val inSpec = spec.propertiesOf(schema)
        val bound = fieldsOf(type)
        val lowerSpec = inSpec.associateBy { it.lowercase() }

        // The names are matched case-insensitively on the wire (the Sefin is camelCase, the ADN PascalCase).
        assertThat(bound.map { it.lowercase() })
            .describedAs("%s binds fields that %s does not declare", type.simpleName, schema)
            .allMatch { it in lowerSpec }

        val notBound = inSpec.filterNot { it.lowercase() in bound.map { field -> field.lowercase() } }
        assertThat(notBound)
            .describedAs(
                "%s has fields %s ignores; if this list grew, the service added something — decide whether to bind it",
                schema,
                type.simpleName,
            ).containsExactlyInAnyOrderElementsOf(ignored)
    }

    @Test
    fun `every Sefin path the client calls exists in the spec`() {
        val paths = spec("sefin-nacional").paths()

        assertThat(
            listOf(
                NfseApiPaths.NFSE,
                NfseApiPaths.NFSE_BY_KEY,
                NfseApiPaths.DPS_BY_ID,
                NfseApiPaths.EVENTS,
                NfseApiPaths.EVENT,
            ).map { it.normalizePath() },
        ).isSubsetOf(paths)
    }

    @Test
    fun `every ADN path the client calls exists in the spec`() {
        val paths = spec("adn-contribuinte").paths()

        assertThat(listOf(NfseApiPaths.ADN_EVENTS, NfseApiPaths.ADN_DISTRIBUTION).map { it.normalizePath() })
            .isSubsetOf(paths)
    }

    @Test
    fun `the six municipal parameter queries exist in the spec`() {
        val paths = spec("adn-parametrizacao").paths()

        assertThat(
            listOf(
                NfseApiPaths.PARAM_AGREEMENT,
                NfseApiPaths.PARAM_RATES,
                NfseApiPaths.PARAM_RATE_HISTORY,
                NfseApiPaths.PARAM_BENEFIT,
                NfseApiPaths.PARAM_SPECIAL_REGIMES,
                NfseApiPaths.PARAM_WITHHOLDINGS,
            ).map { it.normalizePath() },
        ).isSubsetOf(paths)
    }

    @Test
    fun `the Sefin payloads bind the fields the spec declares`() {
        val sefin = spec("sefin-nacional")

        assertBinds(sefin, "NFSePostResponseSucesso", ApiPayloads.NfseResponse::class.java)
        assertBinds(sefin, "DpsGetResponse", ApiPayloads.DpsResponse::class.java, ignored = DPS_IGNORED)
        assertBinds(sefin, "EventosPostResponseSucesso", ApiPayloads.EventResponse::class.java, ignored = EVENT_IGNORED)
        assertBinds(sefin, "MensagemProcessamento", ApiPayloads.Message::class.java)
        assertThat(sefin.propertiesOf("NFSePostRequest")).containsExactly(ApiPayloads.DPS_FIELD)
        assertThat(sefin.propertiesOf("EventosPostRequest")).containsExactly(ApiPayloads.EVENT_REQUEST_FIELD)
    }

    @Test
    fun `the get response is covered by the same payload as the post response`() {
        val sefin = spec("sefin-nacional")

        // One DTO reads both; the GET body is a subset of the POST one.
        assertThat(sefin.propertiesOf("NFSeGetResponseSucesso").map { it.lowercase() })
            .isSubsetOf(fieldsOf(ApiPayloads.NfseResponse::class.java).map { it.lowercase() })
    }

    @Test
    fun `the ADN payloads bind the fields the spec declares`() {
        val adn = spec("adn-contribuinte")

        assertBinds(adn, "DistribuicaoNSU", ApiPayloads.DistributionItem::class.java)
        assertBinds(
            adn,
            "LoteDistribuicaoNSUResponse",
            ApiPayloads.DistributionResponse::class.java,
            ignored = DISTRIBUTION_IGNORED,
        )
        assertBinds(adn, "MensagemProcessamento", ApiPayloads.Message::class.java, ignored = ADN_MESSAGE_IGNORED)
    }

    private companion object {
        val PLACEHOLDER = Regex("\\{[^}]*}")

        /** Envelope fields every response repeats and the client has no use for (the ADN spells them PascalCase). */
        val SEFIN_ENVELOPE = setOf("tipoAmbiente", "versaoAplicativo")
        val ADN_ENVELOPE = setOf("TipoAmbiente", "VersaoAplicativo")

        /** The DPS lookup only needs the access key; the id is what the caller passed in. */
        val DPS_IGNORED = SEFIN_ENVELOPE + setOf("dataHoraProcessamento", "idDps")
        val EVENT_IGNORED = SEFIN_ENVELOPE
        val DISTRIBUTION_IGNORED = ADN_ENVELOPE

        /** The ADN's message adds an enum and its substitution parameters; `Descricao` still carries the text. */
        val ADN_MESSAGE_IGNORED = setOf("Mensagem", "Parametros")
    }
}
