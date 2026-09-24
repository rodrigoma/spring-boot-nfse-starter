package io.github.rodrigoma.nfse.sample.local

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpsServer
import io.github.rodrigoma.nfse.exception.NfseError
import io.github.rodrigoma.nfse.xml.GzipBase64
import io.github.rodrigoma.nfse.xml.XmlSigner
import io.github.rodrigoma.nfse.xml.XmlSupport
import io.github.rodrigoma.nfse.xml.XsdValidator
import org.slf4j.LoggerFactory
import org.w3c.dom.Document
import org.w3c.dom.Element
import tools.jackson.databind.json.JsonMapper
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Executors

/**
 * A fake Sefin Nacional for the `local` profile: HTTPS with mutual TLS, the JSON contract the library expects,
 * schema validation and signature verification of what is sent, in-memory notes and events, a stub DANFSE.
 *
 * [start] mints the certificates under `build/local-sefin/`, boots the server on a free port and returns the
 * `nfse.*` properties that point the starter at it.
 */
internal class LocalSefin private constructor(
    private val store: LocalSefinStore,
    private val server: HttpsServer,
) {
    private val mapper = JsonMapper.builder().build()
    private val dpsValidator = XsdValidator.dps()
    private val eventValidator = XsdValidator.eventRequest()
    private val adn = LocalAdn(store)

    val baseUrl: String get() = "https://localhost:${server.address.port}"

    private class Rejection(
        val errors: List<NfseError>,
    ) : RuntimeException(errors.joinToString())

    private class Route(
        val method: String,
        val pattern: Regex,
        val handler: (List<String>, ByteArray) -> Reply,
    )

    // Same routing as the real services (docs/specs): Sefin at the root, ADN under /contribuintes, /danfse
    // and /parametrizacao.
    private val routes =
        listOf(
            Route("POST", Regex("/nfse")) { _, body -> emit(body) },
            Route("GET", Regex("/nfse/([^/]+)")) { p, _ -> get(p[0]) },
            Route("GET", Regex("/dps/(DPS[0-9A-Z]+)")) { p, _ -> dps(p[0], head = false) },
            Route("HEAD", Regex("/dps/(DPS[0-9A-Z]+)")) { p, _ -> dps(p[0], head = true) },
            Route("POST", Regex("/nfse/([^/]+)/eventos")) { p, body -> event(p[0], body) },
            Route("GET", Regex("/nfse/([^/]+)/eventos/([0-9]{6})/([0-9]+)")) { p, _ -> eventByType(p[0], p[1], p[2]) },
            Route("GET", Regex("/contribuintes/NFSe/([^/]+)/Eventos")) { p, _ -> adn.events(p[0]) },
            Route("GET", Regex("/contribuintes/DFe/([0-9]+)")) { p, _ -> adn.distribution(p[0].toLong()) },
            Route("GET", Regex("/danfse/([^/]+)")) { p, _ -> danfse(p[0]) },
            Route("GET", Regex("/parametrizacao/([^/]+)/(.+)")) { p, _ -> adn.parameters(p[0], p[1]) },
        )

    private fun route(exchange: HttpExchange): Reply {
        val method = exchange.requestMethod
        val path = exchange.requestURI.path
        val body = exchange.requestBody.readBytes()
        log.info("{} {}", method, path)
        val route =
            routes.firstOrNull { it.method == method && it.pattern.matches(path) } ?: return Reply(HTTP_NOT_FOUND)
        val parameters =
            route.pattern
                .matchEntire(path)!!
                .groupValues
                .drop(1)
        return route.handler(parameters, body)
    }

    private fun emit(body: ByteArray): Reply {
        val dps = decode(body, "dpsXmlGZipB64", dpsValidator)
        val infDps = dps.documentElement.element("infDPS")
        if (infDps.element("tpAmb").textContent != "2") {
            reject("E0006", "Ambiente informado diverge do ambiente de recebimento.")
        }
        val digits = infDps.getAttribute("Id").removePrefix("DPS")
        if (store.findByDps(digits) != null) {
            reject("E0014", "Conjunto de Série, Número, Município Emissor e CNPJ/CPF já existe em uma NFS-e gerada.")
        }
        val note = store.generate(dps)
        val payload =
            mapOf(
                "idDps" to "DPS$digits",
                "chaveAcesso" to note.accessKey,
                "nfseXmlGZipB64" to GzipBase64.encode(note.nfseXml),
            )
        return json(HTTP_CREATED, header() + payload)
    }

    private fun get(accessKey: String): Reply {
        val note = store.find(accessKey) ?: return notFound("Chave de acesso não encontrada.")
        return json(HTTP_OK, header() + mapOf("nfseXmlGZipB64" to GzipBase64.encode(note.nfseXml)))
    }

    private fun dps(
        id: String,
        head: Boolean,
    ): Reply {
        val note =
            store.findByDps(id.removePrefix("DPS"))
                ?: return notFound("Não foi gerada uma NFS-e com o identificador informado")
        return if (head) Reply(HTTP_OK) else json(HTTP_OK, header() + mapOf("chaveAcesso" to note.accessKey))
    }

    private fun event(
        accessKey: String,
        body: ByteArray,
    ): Reply {
        val note = store.find(accessKey) ?: return notFound("Chave de acesso não encontrada.")
        val request = decode(body, "pedidoRegistroEventoXmlGZipB64", eventValidator)
        if (note.events.any { it.typeCode == CANCELLATION }) {
            reject("E0840", "O evento de Cancelamento de NFS-e já está vinculado à NFS-e indicada.")
        }
        return json(HTTP_CREATED, header() + eventJson(store.registerEvent(note, request)))
    }

    private fun eventByType(
        accessKey: String,
        typeCode: String,
        sequence: String,
    ): Reply {
        val note = store.find(accessKey) ?: return notFound("Chave de acesso não encontrada.")
        val event = note.events.firstOrNull { it.typeCode == typeCode && it.sequence == sequence.toInt() }
        return event?.let { json(HTTP_OK, header() + eventJson(it)) }
            ?: notFound("Nenhum evento encontrado para a NFS-e")
    }

    private fun danfse(accessKey: String): Reply {
        val note = store.find(accessKey) ?: return notFound("Chave de acesso não encontrada.")
        val pdf = LocalPdf.render(listOf("DANFSE (sandbox local)", "Chave de acesso: ${note.accessKey}"))
        return Reply(HTTP_OK, pdf, "application/pdf")
    }

    private fun decode(
        body: ByteArray,
        field: String,
        validator: XsdValidator,
    ): Document {
        val payload = runCatching { mapper.readValue(body, Map::class.java)[field] as? String }.getOrNull()
        val xml =
            payload?.let { runCatching { GzipBase64.decode(it) }.getOrNull() }
                ?: reject("E1225", "Falha na decodificação da base 64 da área de dados")
        val document =
            runCatching {
                XmlSupport.parse(xml)
            }.getOrElse { reject("E1226", "Estrutura descompactada mal formada.") }
        validator.validate(document).takeIf { it.isNotEmpty() }?.let { throw Rejection(it) }
        if (!XmlSigner.verify(document)) reject("E0714", "Arquivo enviado com erro na assinatura.")
        return document
    }

    private fun reject(
        code: String,
        description: String,
    ): Nothing = throw Rejection(listOf(NfseError(code, description)))

    private fun handle(exchange: HttpExchange) {
        val reply =
            try {
                route(exchange)
            } catch (e: Rejection) {
                json(
                    HTTP_BAD_REQUEST,
                    mapOf(
                        "erros" to e.errors.map { mapOf("codigo" to it.code, "descricao" to it.description) },
                    ),
                )
            }
        exchange.responseHeaders.add("Content-Type", reply.contentType)
        if (reply.body.isEmpty()) {
            exchange.sendResponseHeaders(reply.status, -1)
        } else {
            exchange.sendResponseHeaders(reply.status, reply.body.size.toLong())
            exchange.responseBody.use { it.write(reply.body) }
        }
        exchange.close()
    }

    companion object {
        private val log = LoggerFactory.getLogger(LocalSefin::class.java)
        private const val CANCELLATION = "101101"

        /** Boots the sandbox and returns the `nfse.*` properties that point the starter at it. */
        fun start(cnpj: String): Map<String, Any> {
            val directory = Files.createDirectories(Path.of("build", "local-sefin"))
            val emitter = LocalCertificates.emitter(cnpj)
            val serverIdentity = LocalCertificates.server()
            val pfx = LocalCertificates.pfxFile(directory, emitter)
            val trust = LocalCertificates.trustStoreFile(directory, serverIdentity.certificate)

            val server = HttpsServer.create(InetSocketAddress("127.0.0.1", 0), 0)
            server.httpsConfigurator = LocalCertificates.mutualTls(serverIdentity, emitter)
            val sefin = LocalSefin(LocalSefinStore(), server)
            server.createContext("/", sefin::handle)
            server.executor = Executors.newCachedThreadPool()
            server.start()
            log.info(
                "Local Sefin sandbox on {} (mTLS; certificates under {})",
                sefin.baseUrl,
                directory.toAbsolutePath(),
            )
            return mapOf(
                "nfse.certificate.location" to "file:$pfx",
                "nfse.certificate.password" to LocalCertificates.PASSWORD,
                "nfse.certificate.trust-store-path" to trust.toString(),
                "nfse.certificate.trust-store-password" to LocalCertificates.PASSWORD,
                "nfse.base-url.sefin" to sefin.baseUrl,
                "nfse.base-url.adn" to "${sefin.baseUrl}/contribuintes",
                "nfse.base-url.danfse" to "${sefin.baseUrl}/danfse",
                "nfse.base-url.municipal-parameters" to "${sefin.baseUrl}/parametrizacao",
            )
        }
    }
}

private fun eventJson(event: StoredEvent): Map<String, Any?> = mapOf("eventoXmlGZipB64" to GzipBase64.encode(event.xml))

private fun Element.element(name: String): Element =
    getElementsByTagNameNS(XmlSupport.NFSE_NAMESPACE, name).item(0) as Element
