package io.github.rodrigoma.nfse.client

import io.github.rodrigoma.nfse.autoconfigure.NfseAutoConfiguration
import io.github.rodrigoma.nfse.exception.NfseException
import io.github.rodrigoma.nfse.model.dps.Amounts
import io.github.rodrigoma.nfse.model.dps.DpsId
import io.github.rodrigoma.nfse.model.dps.FederalId
import io.github.rodrigoma.nfse.model.event.CancellationReason
import io.github.rodrigoma.nfse.model.event.NfseEventType
import io.github.rodrigoma.nfse.model.request.DpsRequest
import io.github.rodrigoma.nfse.model.request.ServiceRequest
import io.github.rodrigoma.nfse.support.NfseStubServer
import io.github.rodrigoma.nfse.support.TestCertificates
import io.github.rodrigoma.nfse.support.TestDps
import io.github.rodrigoma.nfse.support.TestXml
import io.github.rodrigoma.nfse.xml.GzipBase64
import io.github.rodrigoma.nfse.xml.XmlSigner
import io.github.rodrigoma.nfse.xml.XmlSupport
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import tools.jackson.databind.json.JsonMapper
import java.io.IOException
import java.math.BigDecimal
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory

/** Drives the auto-configured [NfseClient] against an HTTPS stub that requires the emitter's certificate (mTLS). */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NfseClientIntegrationTest {
    private val emitter = TestCertificates.emitter()
    private lateinit var stub: NfseStubServer
    private lateinit var pfx: Path
    private lateinit var contextRunner: ApplicationContextRunner
    private val mapper = JsonMapper.builder().build()
    private val accessKey = TestXml.ACCESS_KEY

    @BeforeAll
    fun startStub() {
        stub = NfseStubServer(trustedClients = listOf(emitter.certificate))
        pfx = Files.createTempFile("emitter", ".pfx").also { Files.write(it, emitter.pkcs12()) }
        contextRunner =
            ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(NfseAutoConfiguration::class.java))
                .withPropertyValues(
                    "nfse.certificate.pfx-path=$pfx",
                    "nfse.certificate.password=${TestCertificates.PASSWORD}",
                    "nfse.certificate.trust-store-path=${stub.trustStoreFile()}",
                    "nfse.certificate.trust-store-password=${TestCertificates.PASSWORD}",
                    "nfse.emitter.cnpj=${TestDps.CNPJ}",
                    "nfse.emitter.municipality-ibge=${TestDps.MUNICIPALITY}",
                    "nfse.emitter.municipal-registration=12345",
                    "nfse.base-url.sefin=${stub.baseUrl}",
                    "nfse.base-url.danfse=${stub.baseUrl}/danfse",
                    "nfse.application-version=test/1.0",
                    "nfse.read-timeout=1s",
                    "nfse.log-requests=true",
                )
    }

    @AfterAll
    fun stopStub() {
        stub.close()
        Files.deleteIfExists(pfx)
    }

    @BeforeEach
    fun resetRequests() = stub.requests.clear()

    private fun withClient(block: (NfseClient) -> Unit) =
        contextRunner.run { context ->
            assertThat(context).hasNotFailed()
            block(context.getBean(NfseClient::class.java))
        }

    private val request =
        DpsRequest(
            number = 1,
            competenceDate = TestDps.competence,
            taker = TestDps.taker,
            service = ServiceRequest(nationalTaxCode = "010701", description = "Consultoria"),
            amounts = Amounts(serviceAmount = BigDecimal("100")),
        )

    private fun nfseResponse(): String =
        mapper.writeValueAsString(
            mapOf(
                "tipoAmbiente" to 2,
                "versaoAplicativo" to "SefinNacional 1.0",
                "dataHoraProcessamento" to "2026-09-19T13:00:00Z",
                "idDps" to "DPS355030821234567800019500001000000000000001",
                "chaveAcesso" to accessKey,
                "nfseXmlGZipB64" to GzipBase64.encode(TestXml.nfse()),
            ),
        )

    @Test
    fun `emits a DPS - signed, compressed and encoded - and maps the NFS-e back`() {
        stub.stub("POST", "/nfse", 201, nfseResponse())

        withClient { client ->
            val result = client.emit(request)

            assertThat(result.accessKey).isEqualTo(accessKey)
            assertThat(result.nfseNumber).isEqualTo("123")
            assertThat(result.dpsId).isEqualTo("DPS355030821234567800019500001000000000000001")
            assertThat(result.processedAt).isNotNull()
            assertThat(result.nfseXml).isEqualTo(TestXml.nfse())
            assertThat(result.nfse.netAmount).isEqualByComparingTo("98.00")
            assertThat(result.dpsXml)
                .startsWith("<?xml")
                .contains("<Signature xmlns=\"http://www.w3.org/2000/09/xmldsig#\">")

            val sent = stub.requests.single()
            assertThat(sent.method).isEqualTo("POST")
            assertThat(sent.headers["Content-type"]?.first()).startsWith("application/json")
            val payload = mapper.readValue(sent.body, Map::class.java)
            assertThat(payload.keys).containsExactly("dpsXmlGZipB64")
            val dpsXml = GzipBase64.decode(payload["dpsXmlGZipB64"] as String)
            assertThat(dpsXml).isEqualTo(result.dpsXml)
            assertThat(XmlSigner.verify(XmlSupport.parse(dpsXml))).isTrue()
            assertThat(dpsXml).contains("<CNPJ>${TestDps.CNPJ}</CNPJ><IM>12345</IM>")
        }
    }

    @Test
    fun `emits a low-level Dps as well`() {
        stub.stub("POST", "/nfse", 200, nfseResponse())
        withClient { client -> assertThat(client.emit(TestDps.complete()).accessKey).isEqualTo(accessKey) }
    }

    @Test
    fun `a rejection carries the error list`() {
        stub.stub(
            "POST",
            "/nfse",
            400,
            """{"erros":[{"codigo":"E0010","descricao":"Série fora da faixa","complemento":"1-49999"}]}""",
        )
        withClient { client ->
            assertThatThrownBy { client.emit(request) }
                .isInstanceOf(NfseException.Rejected::class.java)
                .satisfies({
                    val rejected = it as NfseException.Rejected
                    assertThat(rejected.httpStatus).isEqualTo(400)
                    assertThat(rejected.errors.single().code).isEqualTo("E0010")
                    assertThat(rejected.errors.single().detail).isEqualTo("1-49999")
                })
        }
    }

    @Test
    fun `5xx, timeouts and responses without the XML become Unavailable`() {
        stub.stub("POST", "/nfse", 503, "unavailable")
        withClient { client ->
            assertThatThrownBy { client.emit(request) }
                .isInstanceOf(NfseException.Unavailable::class.java)
                .satisfies({ assertThat((it as NfseException.Unavailable).statusCode).isEqualTo(503) })
        }

        stub.stub("POST", "/nfse", 200, nfseResponse(), delayMillis = 2500)
        withClient { client ->
            assertThatThrownBy { client.emit(request) }
                .isInstanceOf(NfseException.Unavailable::class.java)
                .hasMessageContaining("Cannot reach")
        }

        stub.stub("POST", "/nfse", 200, """{"chaveAcesso":"$accessKey"}""")
        withClient { client ->
            assertThatThrownBy { client.emit(request) }
                .isInstanceOf(NfseException.Unavailable::class.java)
                .hasMessageContaining("nfseXmlGZipB64")
        }
    }

    @Test
    fun `a locally invalid DPS never reaches the wire`() {
        withClient { client ->
            assertThatThrownBy { client.emit(request.copy(service = request.service.copy(nationalTaxCode = "x"))) }
                .isInstanceOf(NfseException.Validation::class.java)
            assertThat(stub.requests).isEmpty()
        }
    }

    @Test
    fun `gets an NFS-e by access key and maps 404 to NotFound`() {
        stub.stub("GET", "/nfse/$accessKey", 200, nfseResponse())
        withClient { client ->
            val nfse = client.get(accessKey)
            assertThat(nfse.accessKey).isEqualTo(accessKey)
            assertThat(nfse.statusCode).isEqualTo("100")
            assertThatThrownBy { client.get("0".repeat(50)) }.isInstanceOf(NfseException.NotFound::class.java)
        }
    }

    @Test
    fun `looks a DPS up by identifier with GET and HEAD`() {
        val dpsId = DpsId(TestDps.MUNICIPALITY, FederalId.Cnpj(TestDps.CNPJ), 1, 1)
        stub.stub("GET", "/dps/${dpsId.digits}", 200, """{"tipoAmbiente":2,"chaveAcesso":"$accessKey"}""")
        stub.stub("HEAD", "/dps/${dpsId.digits}", 200)
        withClient { client ->
            assertThat(client.accessKeyOf(dpsId)).isEqualTo(accessKey)
            assertThat(client.exists(dpsId)).isTrue()
            val unknown = dpsId.copy(number = 2)
            assertThat(client.accessKeyOf(unknown)).isNull()
            assertThat(client.exists(unknown)).isFalse()

            val unexpected = dpsId.copy(number = 3)
            stub.stub("GET", "/dps/${unexpected.digits}", 200, """{"tipoAmbiente":2}""")
            assertThatThrownBy { client.accessKeyOf(unexpected) }
                .isInstanceOf(NfseException.Unavailable::class.java)
                .hasMessageContaining("chaveAcesso")
        }
    }

    @Test
    fun `cancels an NFS-e with a signed event request`() {
        val eventJson =
            mapper.writeValueAsString(
                mapOf(
                    "tipoAmbiente" to 2,
                    "dataHoraProcessamento" to "2026-09-20T13:00:00Z",
                    "idEvento" to "EVT${accessKey}101101001",
                    "tipoEvento" to "101101",
                    "numSeqEvento" to 1,
                    "eventoXmlGZipB64" to GzipBase64.encode(TestXml.event()),
                ),
            )
        stub.stub("POST", "/nfse/$accessKey/eventos", 201, eventJson)
        withClient { client ->
            val event = client.cancel(accessKey, CancellationReason.ISSUANCE_ERROR, "Nota emitida com valor incorreto")

            assertThat(event.type).isEqualTo(NfseEventType.CANCELLATION)
            assertThat(event.accessKey).isEqualTo(accessKey)
            assertThat(event.justification).isEqualTo("Nota emitida com valor incorreto")
            assertThat(event.xml).isEqualTo(TestXml.event())

            val payload = mapper.readValue(stub.requests.single().body, Map::class.java)
            val xml = GzipBase64.decode(payload["pedidoRegistroEventoXmlGZipB64"] as String)
            assertThat(xml)
                .contains("<infPedReg Id=\"PRE${accessKey}101101\">")
                .contains("<CNPJAutor>${TestDps.CNPJ}</CNPJAutor>")
            assertThat(XmlSigner.verify(XmlSupport.parse(xml))).isTrue()
        }
    }

    @Test
    fun `lists events, accepting a list or a single object, and builds one without XML from the JSON fields`() {
        val withList =
            mapper.writeValueAsString(
                mapOf(
                    "eventos" to
                        listOf(
                            mapOf("eventoXmlGZipB64" to GzipBase64.encode(TestXml.event())),
                            mapOf(
                                "idEvento" to "EVT1",
                                "tipoEvento" to "e105102",
                                "numSeqEvento" to 2,
                            ),
                        ),
                ),
            )
        stub.stub("GET", "/nfse/$accessKey/eventos", 200, withList)
        withClient { client ->
            val events = client.events(accessKey)
            assertThat(events).hasSize(2)
            assertThat(events[0].type).isEqualTo(NfseEventType.CANCELLATION)
            assertThat(events[1].id).isEqualTo("EVT1")
            assertThat(events[1].type).isEqualTo(NfseEventType.CANCELLATION_BY_SUBSTITUTION)
            assertThat(events[1].sequence).isEqualTo(2)
            assertThat(events[1].xml).isEmpty()
        }

        stub.stub(
            "GET",
            "/nfse/$accessKey/eventos",
            200,
            mapper.writeValueAsString(
                mapOf(
                    "eventoXmlGZipB64" to GzipBase64.encode(TestXml.event()),
                ),
            ),
        )
        withClient { client -> assertThat(client.events(accessKey)).hasSize(1) }

        stub.stub("GET", "/nfse/$accessKey/eventos", 200, "{}")
        withClient { client -> assertThat(client.events(accessKey)).isEmpty() }
    }

    @Test
    fun `downloads the DANFSE from the DANFSE base URL`() {
        val pdf = "%PDF-1.4 fake".toByteArray()
        stub.stubBytes("GET", "/danfse/$accessKey", pdf, "application/pdf")
        withClient { client ->
            assertThat(client.danfse(accessKey)).isEqualTo(pdf)
            assertThat(
                stub.requests
                    .single()
                    .headers["Accept"]
                    ?.first(),
            ).contains("application/pdf")
        }
    }

    @Test
    fun `fetches municipal parameters as raw maps`() {
        val agreementJson = """{"parametrosConvenio":{"aderenteAmbienteNacional":1}}"""
        stub.stub("GET", "/parametros_municipais/3550308/convenio", 200, agreementJson)
        stub.stub("GET", "/parametros_municipais/3550308/010701", 200, """{"aliquotas":[{"aliq":2.0}]}""")
        withClient { client ->
            val agreement = client.municipalAgreement(3550308)
            assertThat(agreement.serviceCode).isNull()
            assertThat(agreement.raw["parametrosConvenio"]).isEqualTo(mapOf("aderenteAmbienteNacional" to 1))
            val parameters = client.municipalParameters(3550308, "010701")
            assertThat(parameters.serviceCode).isEqualTo("010701")
            assertThat(parameters.raw).containsKey("aliquotas")
        }
    }

    @Test
    fun `the stub really requires the client certificate`() {
        val trustOnly =
            SSLContext.getInstance("TLS").apply {
                val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
                factory.init(stub.trustStore)
                init(null, factory.trustManagers, null)
            }
        val anonymous = HttpClient.newBuilder().sslContext(trustOnly).build()
        val request = HttpRequest.newBuilder(URI.create("${stub.baseUrl}/nfse/$accessKey")).GET().build()
        assertThatThrownBy { anonymous.send(request, HttpResponse.BodyHandlers.discarding()) }
            .isInstanceOf(IOException::class.java)
    }
}
