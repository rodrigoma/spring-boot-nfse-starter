# NFS-e Spring Boot Starter

[![CI](https://github.com/rodrigoma/spring-boot-nfse-starter/actions/workflows/ci.yml/badge.svg)](https://github.com/rodrigoma/spring-boot-nfse-starter/actions/workflows/ci.yml)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.rodrigoma/nfse-spring-boot-starter.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/io.github.rodrigoma/nfse-spring-boot-starter)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

A Spring Boot auto-configuration library for the **Sistema Nacional NFS-e** — the Brazilian national standard for
the electronic service invoice (NFS-e, [gov.br/nfse](https://www.gov.br/nfse)). Configure the emitter's certificate
and identification, and get an `NfseClient` that builds the DPS, validates it against the official XSD, signs it,
sends it to the Sefin Nacional over mutual TLS and returns the generated NFS-e — plus lookup, cancellation, events,
distribution by NSU (ADN), municipal parameters and the DANFSe (PDF) — rendered locally by the optional
`nfse-spring-boot-danfse` module, following the national layout of NT 008/2026.

The library knows the **fields** of the national layout (`DPS` v1.01, `pedRegEvento` v1.01), never the values of a
company: certificate, CNPJ, municipal registration, municipality, tax regime, series, service codes and rates all
come from configuration or from the caller.

## Compatibility

| Library version | Spring Boot | Java | Kotlin |
|---|---|---|---|
| 1.x | 4.1+ | 21+ | 2.3+ |

Built and tested against Spring Boot 4.1.1 / Kotlin 2.4 / Java 21. The library is compiled with Kotlin 2.4, so a
Kotlin consumer needs a compiler that can read that metadata (2.3 or newer). Java consumers need nothing else.

## Requirements

| Dependency   | Minimum version |
|--------------|-----------------|
| Java         | 21              |
| Spring Boot  | 4.1.0           |
| Kotlin       | 2.3 (optional)  |

An **ICP-Brasil A1 certificate** (e-CNPJ or e-CPF, PKCS#12 `.pfx`) of the emitter. The same certificate signs the
XML and opens the mTLS connection — there is no token. A3 (smart card / token) certificates are not supported.

## Installation

### Gradle (Kotlin DSL)

```kotlin
dependencies {
    implementation("io.github.rodrigoma:nfse-spring-boot-starter:1.0.0-RC1")
}
```

### Gradle (Groovy DSL)

```groovy
dependencies {
    implementation 'io.github.rodrigoma:nfse-spring-boot-starter:1.0.0-RC1'
}
```

### Maven

```xml
<dependency>
    <groupId>io.github.rodrigoma</groupId>
    <artifactId>nfse-spring-boot-starter</artifactId>
    <version>1.0.0-RC1</version>
</dependency>
```

### DANFSe renderer (optional)

Add `nfse-spring-boot-danfse` to render the PDF locally — see [Rendering the DANFSe](#rendering-the-danfse):

```kotlin
dependencies {
    implementation("io.github.rodrigoma:nfse-spring-boot-danfse:1.0.0-RC1")
}
```

## Configuration

```yaml
nfse:
  environment: RESTRICTED_PRODUCTION          # default; PRODUCTION must be spelled out
  certificate:
    pfx-path: /secrets/certificado-a1.pfx     # or pfx-base64
    password: ${NFSE_CERTIFICATE_PASSWORD}
  emitter:
    cnpj: 12.345.678/0001-95                  # or cpf
    municipal-registration: "12345"
    municipality-ibge: 3550308
    address:                                  # optional for the provider-emitter; sent when present
      street: Av. Paulista
      number: "1000"
      district: Bela Vista
      zip-code: "01310100"
      state: SP
    email: financeiro@empresa.com.br
    phone: "11999998888"
    tax-regime:
      simples-nacional: ME_EPP                # NOT_OPTING (default) | MEI | ME_EPP
      simples-nacional-assessment: SIMPLES_NACIONAL   # required for ME_EPP only
      special-regime: NONE
    dps-series: 1
  log-requests: false
```

| Property | Type | Default | Required | Description |
|---|---|---|---|---|
| `nfse.enabled` | `Boolean` | `true` | No | `false` leaves the starter on the classpath without creating any bean |
| `nfse.environment` | `Enum` | `RESTRICTED_PRODUCTION` | No | `RESTRICTED_PRODUCTION` (`tpAmb=2`, tests) or `PRODUCTION` (`tpAmb=1`) |
| `nfse.certificate.pfx-path` | `String` | — | One of | Path to the PKCS#12 file of the emitter's A1 certificate |
| `nfse.certificate.pfx-base64` | `String` | — | One of | The same file, Base64-encoded (e.g. from a secret manager) |
| `nfse.certificate.password` | `String` | `""` | Yes | PKCS#12 password. Never logged |
| `nfse.certificate.trust-store-path` | `String` | JDK default | No | JKS/PKCS#12 trust store for the TLS connection (stubs, corporate proxies) |
| `nfse.certificate.trust-store-password` | `String` | — | No | Password of that trust store |
| `nfse.emitter.cnpj` / `nfse.emitter.cpf` | `String` | — | One of | Federal id of the service provider; punctuation is ignored |
| `nfse.emitter.municipal-registration` | `String` | — | No | `IM` — mandatory when the municipality keeps a complementary registry (rule E0125) |
| `nfse.emitter.municipality-ibge` | `Int` | — | Yes | IBGE code of the establishment — `cLocEmi` and default place of provision |
| `nfse.emitter.address.*` | — | — | No | `street`, `number`, `complement`, `district`, `zip-code`, `municipality-ibge`, `state` |
| `nfse.emitter.email`, `nfse.emitter.phone` | `String` | — | No | Phone is digits only (DDD + number) |
| `nfse.emitter.tax-regime.simples-nacional` | `Enum` | `NOT_OPTING` | No | `opSimpNac`: `NOT_OPTING`, `MEI`, `ME_EPP` |
| `nfse.emitter.tax-regime.simples-nacional-assessment` | `Enum` | — | For ME_EPP | `regApTribSN`: `SIMPLES_NACIONAL`, `FEDERAL_ONLY`, `NONE` |
| `nfse.emitter.tax-regime.special-regime` | `Enum` | `NONE` | No | `regEspTrib`: `NONE`, `COOPERATIVE`, `ESTIMATE`, `MUNICIPAL_MICRO_ENTERPRISE`, `NOTARY`, `SELF_EMPLOYED_PROFESSIONAL`, `PROFESSIONAL_COMPANY`, `OTHER` |
| `nfse.emitter.dps-series` | `Int` | `1` | No | `serie` of the DPS; own software uses 1–49999 (rule E0010) |
| `nfse.application-version` | `String` | `nfse-boot/<version>` | No | `verAplic`, at most 20 characters |
| `nfse.log-requests` | `Boolean` | `false` | No | Logs HTTP traffic at `DEBUG` (see [Request logging](#request-logging)) |
| `nfse.health-indicator-enabled` | `Boolean` | `false` | No | Exposes `/actuator/health/nfse` |
| `nfse.connect-timeout` | `Duration` | `10s` | No | TCP/TLS connect timeout |
| `nfse.read-timeout` | `Duration` | `60s` | No | Response timeout — emission is synchronous and can take a while |
| `nfse.base-url.sefin` | `String` | by environment | No | Overrides the Sefin Nacional base URL (e.g. a local stub) |
| `nfse.base-url.adn` | `String` | by environment | No | Overrides the ADN Contribuintes base URL (distribution, event list) |
| `nfse.base-url.danfse` | `String` | by environment | No | Overrides the ADN DANFSe base URL |
| `nfse.base-url.municipal-parameters` | `String` | by environment | No | Overrides the ADN Parâmetros Municipais base URL |
| `nfse.danfse.enabled` | `Boolean` | `true` | No | With `nfse-spring-boot-danfse` on the classpath: render the DANFSe locally (`false` falls back to the ADN) |
| `nfse.danfse.stub` | `Boolean` | `false` | No | Print the optional "Canhoto" (acknowledgement stub) at the bottom of the DANFSe |

The context **fails to start** when a required property is missing, when the certificate cannot be opened (wrong
password, corrupt file), is expired, is a CA certificate or lacks the *Digital Signature* / *Non Repudiation* key
usages required by Anexo I. A certificate whose CNPJ base differs from `nfse.emitter.cnpj` logs a warning — the Sefin
rejects such a DPS with E0718.

### Environments and services

The API is split across **two hosts with different wire formats** — the Sefin Nacional (camelCase JSON, integer
`tipoAmbiente`) and the ADN (PascalCase JSON, string `TipoAmbiente`); the client hides the difference.

| Service | What | Restricted production (`tpAmb=2`, default) | Production (`tpAmb=1`) |
|---|---|---|---|
| Sefin Nacional | `POST /nfse`, `GET /nfse/{chave}`, `GET/HEAD /dps/{id}`, `POST /nfse/{chave}/eventos`, `GET /nfse/{chave}/eventos/{tipo}/{seq}` | `https://sefin.producaorestrita.nfse.gov.br/SefinNacional` | `https://sefin.nfse.gov.br/SefinNacional` |
| ADN Contribuintes | `GET /DFe/{NSU}`, `GET /NFSe/{chave}/Eventos` | `https://adn.producaorestrita.nfse.gov.br/contribuintes` | `https://adn.nfse.gov.br/contribuintes` |
| ADN DANFSe | `GET /{chave}` (PDF) | `https://adn.producaorestrita.nfse.gov.br/danfse` | `https://adn.nfse.gov.br/danfse` |
| ADN Parâmetros Municipais | agreement, rates, benefits, special regimes, withholdings | `https://adn.producaorestrita.nfse.gov.br/parametrizacao` | `https://adn.nfse.gov.br/parametrizacao` |

Production is only reached with `PRODUCTION` spelled out (or explicit `base-url`s). The client always speaks
**HTTP/1.1** — the Sefin refuses HTTP/2 on authenticated paths.

## Auto-configured beans

| Bean name | Type | Purpose |
|---|---|---|
| `nfseClient` | `NfseClient` | The API you call; `nfseClient.municipalParameters` is the `MunicipalParametersClient` |
| `nfseCertificate` | `NfseCertificate` | The loaded certificate: private key, chain, CNPJ/CPF read from the ICP-Brasil extension |
| `nfseRestClient` | `RestClient` | Sefin Nacional client with the mTLS request factory, JSON mapper and error handling |
| `nfseHealthIndicator` | `HealthIndicator` | Only with `nfse.health-indicator-enabled=true` and Actuator on the classpath |

The JSON mapper used for the Sefin API is private to the starter — your application's Jackson configuration is not
touched.

## Usage

### Emitting an NFS-e

```kotlin
@Service
class InvoiceService(private val nfse: NfseClient, private val counters: DpsCounterRepository) {

    fun invoice(order: Order): NfseResult {
        val result = nfse.emit(
            DpsRequest(
                number = counters.next(series = 1),           // your persisted sequence — the library keeps no state
                competenceDate = order.deliveredOn,
                taker = Person(id = FederalId.cnpjOrCpf(order.customerDocument), name = order.customerName,
                               email = order.customerEmail),
                service = ServiceRequest(
                    nationalTaxCode = "010701",                // cTribNac (LC 116 item + sub-item + national breakdown)
                    description = "Consultoria em TI — ${order.id}",
                    nbsCode = "115011000",
                ),
                amounts = Amounts(
                    serviceAmount = order.total,               // BigDecimal, two decimals in the XML
                    taxes = Taxes(
                        municipal = MunicipalTax(
                            taxation = IssqnTaxation.TAXABLE,
                            withholding = IssqnWithholding.NOT_WITHHELD,
                            rate = BigDecimal("2.00"),         // pAliq — only when your regime requires it (Anexo I)
                        ),
                        total = TotalTaxes.NotInformed,
                    ),
                ),
            ),
        )
        // Keep both XMLs: they are the fiscal documents.
        store(result.accessKey, result.nfseNumber, result.nfseXml, result.dpsXml)
        return result
    }
}
```

`DpsRequest` carries what changes per invoice; the provider group comes from `nfse.emitter.*` (override it per
call with `provider = ServiceProvider(...)` and `emitterMunicipalityIbge` when one application issues for several
CNPJs). The provider's name is never written when the provider emits — rule E0121 forbids it. Foreign takers use
`FederalId.Nif("…")` or `FederalId.NoNif(reason)` and a foreign `Address`. To replace a note, set
`substitution = Substitution(substitutedAccessKey, SubstitutionReason.OTHER, "justification…")` — the Sefin cancels
the old note by substitution and returns the new one. The optional IBS/CBS group (tax reform) is available as
`ibsCbs = IbsCbs(...)`.

Everything the layout allows is modelled: `nfse.emit(dps: Dps)` takes the full `Dps` (deductions with documents,
foreign trade, constructions, events, federal taxes, municipal benefits, suspended enforceability, DPS issued by the
taker or intermediary…).

### Cancelling, querying, PDF

```kotlin
val event = nfse.cancel(accessKey, CancellationReason.ISSUANCE_ERROR, "Nota emitida com valor incorreto")
event.type            // NfseEventType.CANCELLATION
event.xml             // the `evento` XML returned by the Sefin

val note = nfse.get(accessKey)                       // Nfse: number, status, amounts, full XML
val events = nfse.events(accessKey)                  // ADN: every event of the note (empty when none)
val cancellation = nfse.event(accessKey, NfseEventType.CANCELLATION)   // Sefin: one event by type + sequence
val pdf: ByteArray = nfse.danfse(accessKey)          // DANFSe — local render or ADN, see below

// Reconciling after a lost response: the access key from the DPS identifier
val dpsId = DpsId(municipalityIbge = 3550308, emitter = FederalId.Cnpj("12345678000195"), series = 1, number = 42)
nfse.exists(dpsId)                                   // HEAD /dps/{id}
nfse.accessKeyOf(dpsId)                              // GET /dps/{id}, null when no NFS-e was generated

// Municipal parameters (ADN Parâmetros Municipais)
val params = nfse.municipalParameters
params.agreement(3550308)                            // /{mun}/convenio
params.rates(3550308, "010701", LocalDate.now())     // /{mun}/{servico}/{competencia}/aliquota
params.rateHistory(3550308, "010701")
params.benefit(3550308, "12345678901234", LocalDate.now())
params.specialRegimes(3550308, "010701", LocalDate.now())
params.withholdings(3550308, LocalDate.now())        // each returns MunicipalParameters(message, raw)
```

### Distribution by NSU (what was issued to or by you)

The ADN hands every document in which the certificate holder is provider, taker or intermediary — NFS-e issued
by others against your CNPJ, cancellations, manifestations — as a cursor of NSUs:

```kotlin
var nsu = repository.lastNsu()                       // persisted by the application
do {
    val batch = nfse.distribution(nsu)               // GET /DFe/{nsu}?lote=true
    batch.documents.forEach { document ->            // type NFSE / EVENT / …, xml, accessKey
        process(document)
    }
    nsu = batch.nextNsu
    repository.saveLastNsu(nsu)
} while (batch.status == DistributionStatus.FOUND)
```

`DistributionStatus.NONE_FOUND` (an HTTP 404 with a body on the wire) is the end of the cursor, not an error;
`REJECTED` surfaces as `NfseException.Rejected`.

### Rendering the DANFSe

NT 008/2026 suspended the official PDF service of the ADN on 2026-08-03 and defined a **single national layout**
(DANFSe v2.0) that emitters render themselves. The optional `nfse-spring-boot-danfse` module does that: with it on
the classpath, `nfse.danfse(accessKey)` fetches the note and its events and returns the PDF rendered locally —
one A4 page with the fixed model of Anexo I of the NT, the QR Code of the public query, "NFS-e SEM VALIDADE
JURÍDICA" in restricted production and the CANCELADA / SUBSTITUÍDA watermark when the events say so. Everything
printed comes from the `NFSe` XML, so the same XML always gives the same PDF; nothing is stored.

```kotlin
val pdf = nfse.danfse(accessKey)                     // Nfse + events → PDF, no ADN call

// Or straight from an XML you kept, without touching the API
val renderer = DanfseRenderer(DanfseOptions(stub = true))
val pdf = renderer.render(nfse.get(accessKey), nfse.events(accessKey))
val same = renderer.render(nfseXml, NoteStatus.CANCELLED)
```

The module registers a `DanfseRenderer` bean (`DanfsePdfRenderer`); declare your own to replace it, or set
`nfse.danfse.enabled=false` to fall back to the ADN service. It depends on Apache PDFBox 3 and ZXing (Apache 2.0)
and on nothing else — the core starter stays free of PDF libraries. Fonts are the PDF standard Helvetica family
(the NT names Arial / MS Sans Serif); the fidelity was checked against the model of the NT, not against a PDF
generated by the government service, which is not available.

### Error handling

| Exception | When | Notes |
|---|---|---|
| `NfseException.Validation` | the DPS/event fails the local checks (XSD, CPF/CNPJ check digits) | Nothing is sent; `errors` lists the violations with the official codes (`E1235`, `E0080`…) |
| `NfseException.Rejected` | HTTP 400/422 from the Sefin | `errors` carries `code`, `description`, `detail` (`E0010`, …); `httpStatus` |
| `NfseException.Unauthorized` | HTTP 401/403 | The certificate was not accepted for this call |
| `NfseException.NotFound` | HTTP 404 | NFS-e, DPS or event not found (or not visible to this certificate) |
| `NfseException.Unavailable` | network error, timeout, HTTP 429/5xx | Retry later; `statusCode` when there was a response, `retryAfter` from a 429 |
| `NfseException.Certificate` | at startup | Certificate cannot be loaded or violates the ICP-Brasil rules |

```kotlin
try {
    nfse.emit(request)
} catch (e: NfseException.Rejected) {
    e.errors.forEach { log.warn("Sefin rejected the DPS: {} {}", it.code, it.description) }
} catch (e: NfseException.Unavailable) {
    scheduleRetry()
}
```

### Low-level pieces

Everything the client uses is public, for applications that need a different flow:

- `DpsXmlBuilder` / `EventXmlBuilder` — model → `org.w3c.dom.Document`, validated against the XSD.
- `XsdValidator` — the embedded schemas (`DPS_v1.01`, `pedRegEvento_v1.01`, `NFSe_v1.01`, `evento_v1.01`).
- `XmlSigner` — XML-DSig (enveloped, RSA-SHA256, inclusive C14N) with `verify()`; `nfseCertificate.signer()`.
- `GzipBase64` — the `…XmlGZipB64` encoding of every payload.
- `NfseXmlParser` — reads `Nfse` / `NfseEvent` out of the XMLs the Sefin returns.
- `DpsId` — the 45-character DPS identifier.
- `nfseRestClient` and `NfseRestClientCustomizer` — add interceptors, a proxy, extra headers:

```kotlin
@Bean
fun nfseProxy() = NfseRestClientCustomizer { builder -> builder.defaultHeader("X-Trace", "…") }
```

## Testing your integration

Point the starter at a local stub instead of re-assembling the client. The stub must speak HTTPS (the request
factory always uses the mTLS `SSLContext`); give it a self-signed certificate and trust it:

```yaml
# src/test/resources/application-test.yml
nfse:
  certificate:
    pfx-path: build/test-certificate.pfx          # generated by your test setup — never commit a real .pfx
    password: changeit
    trust-store-path: build/stub-trust.p12
    trust-store-password: changeit
  emitter:
    cnpj: "12345678000195"
    municipality-ibge: 3550308
  base-url:
    sefin: https://localhost:${stub.port}
    danfse: https://localhost:${stub.port}/danfse
```

The library's own integration tests do exactly this with the JDK `HttpsServer` and certificates minted at test time
(`nfse-spring-boot-autoconfigure/src/test/kotlin/.../support/NfseStubServer.kt`).

### Local sandbox (no certificate needed)

The sample application ships a fake Sefin Nacional for the `local` profile: it mints a sandbox "e-CNPJ" and a
`localhost` certificate under `build/local-sefin/`, starts an HTTPS server that **requires the client certificate**,
validates every DPS/event against the XSD, verifies the XML signature, keeps notes and events in memory and serves a
placeholder DANFSe for when the local renderer is disabled. The starter is pointed at it automatically:

```bash
./gradlew :nfse-spring-boot-sample:bootRun --args='--spring.profiles.active=local'
```

```bash
curl -s -X POST localhost:8080/sample/nfse -H 'Content-Type: application/json' \
  -d '{"takerDocument":"123.456.789-09","takerName":"Fulano","nationalTaxCode":"010701","description":"Consultoria","amount":1500,"rate":2}'
# then, with the returned accessKey:
curl -s localhost:8080/sample/nfse/<accessKey>                      # the NFS-e
curl -s -X DELETE localhost:8080/sample/nfse/<accessKey> -H 'Content-Type: application/json' \
  -d '{"justification":"Nota emitida com valor incorreto"}'          # cancellation event
curl -s localhost:8080/sample/nfse/<accessKey>/events
curl -s localhost:8080/sample/nfse/<accessKey>/danfse -o danfse.pdf   # rendered locally (DANFSe v2.0)
```

Rejections come back with real codes (`E0014` duplicate DPS, `E0840` already cancelled, `E1235` schema, `E0714`
signature). It exercises the library exactly as production does — only the fiscal rules of Anexo I are not there.

### Restricted production

1. Ask your municipality to enable the emitter in the **produção restrita** environment (it has its own registry).
2. Configure the real A1 certificate, `nfse.environment=RESTRICTED_PRODUCTION` (the default) and the emitter data.
3. Run the sample: `./gradlew :nfse-spring-boot-sample:bootRun` after editing
   [`application.yml`](nfse-spring-boot-sample/src/main/resources/application.yml), then

```bash
curl -X POST localhost:8080/sample/nfse -H 'Content-Type: application/json' \
  -d '{"takerDocument":"12345678909","takerName":"Fulano","nationalTaxCode":"010701","description":"Teste","amount":100}'
```

Notes generated there have no fiscal value.

## Request logging

With `nfse.log-requests=true` and the logger `io.github.rodrigoma.nfse.http` at `DEBUG`, every call is logged with
method, URI, status and body. Headers are never logged. The compressed XML payloads (`dpsXmlGZipB64`,
`nfseXmlGZipB64`, …) — which carry the parties' CNPJ/CPF, names and addresses — are replaced by their size, and
non-JSON bodies (the PDF) are logged by size only.

```yaml
nfse:
  log-requests: true
logging:
  level:
    io.github.rodrigoma.nfse.http: DEBUG
```

## Health indicator

With `nfse.health-indicator-enabled=true` and Spring Boot Actuator on the classpath, `/actuator/health/nfse` fetches
the emitter municipality's agreement parameters — a cheap call that also exercises the mTLS handshake. `UP` on
success, `OUT_OF_SERVICE` when the certificate is refused, `DOWN` otherwise.

## What the library does not do

- **Numbering.** `nDPS` is yours: persist the counter per series and pass `number` on every call. The library keeps
  no state and never retries on its own.
- **Fiscal decisions.** Service codes, rates, withholding, Simples Nacional options, benefits and deductions are
  inputs. Anexo I of the official manual says when each field is mandatory or forbidden for your regime; the Sefin
  validates them and the rejection codes come back verbatim in `NfseException.Rejected`.
- **Municipal-layout NFS-e** (`tpEmis=2`) and emission by administrative/judicial decision (`/decisao-judicial/nfse`).
- **DPS issued by the taker or intermediary** (`tpEmit=2/3`): the model allows it, but the current version of the
  Sefin rejects it (rule E9996).
- **Fetching the DANFSe from the government.** NT 008/2026 suspended the official PDF service on 2026-08-03;
  `danfse()` renders locally with the `nfse-spring-boot-danfse` module and only calls the ADN without it.
- **A3 certificates** and HSMs.

## Notes on the official docs

The HTTP contract follows the OpenAPI documents of the four services captured from the restricted-production
Swagger UIs (2026-04-16), kept under [`docs/specs/`](docs/specs/README.md) — they settle what the manuals leave
open. `scripts/fetch-swagger.sh /path/to/certificado.pfx` refreshes them with your certificate. Points worth knowing:

- **JSON shapes.** `POST /nfse` → `NFSePostResponseSucesso` (`idDps`, `chaveAcesso`, `nfseXmlGZipB64`, `alertas[]`)
  or 400 `NFSePostResponseErro` (`erros[]` of `codigo`/`descricao`/`complemento`). Every other Sefin endpoint
  answers errors as `ResponseErro` with a **single `erro` object**. The ADN speaks PascalCase and returns **400
  (rejection) and 404 (nothing found) with a full body** — the client reads `StatusProcessamento` instead of
  failing. Property names are matched case-insensitively and unknown ones are ignored.
- **`GET/HEAD /dps/{id}`** receives the full identifier, `DPS` literal included.
- **Only `GET /nfse/{chave}/eventos/{tipo}/{seq}` exists on the Sefin.** The one- and two-parameter forms of the
  manual are not in the OpenAPI; the list of events comes from the ADN (`GET /NFSe/{chave}/Eventos`).
- **Municipal parameters and the DANFSe moved to the ADN** — the Sefin paths answer 501.
- **HTTP/1.1 only.** The Sefin answers `HTTP_1_1_REQUIRED` on HTTP/2 streams.
- **Signature algorithm.** Anexo I names none; the library signs with RSA-SHA256 / SHA-256, inclusive C14N and the
  enveloped transform, in the default xmldsig namespace (namespace prefixes are rejected by rule E1228). Notes
  generated in production carry inclusive-C14N signatures; other emitters use exclusive C14N + SHA-256 — the Sefin
  accepts both.
- **Series.** Written unpadded (`<serie>3</serie>`), as production notes carry it; the DPS id pads it to five
  digits.
- **Schemas.** The embedded XSDs are the `NFSe-ESQUEMAS_XSD v1.01` package published on the restricted-production
  page on 2026-07-27, verbatim. Compared with the 2026-02-09 package of the production page it fixes
  `TSSerieDPS` (whose `^…$` anchors, literal characters in XML Schema regular expressions, rejected every series),
  drops the `DOCTYPE` of `xmldsig-core-schema.xsd` (no network access during validation) and introduces the
  **alphanumeric CNPJ** (`[0-9A-Z]{14}`) in the CNPJ, DPS id and access key patterns — `FederalId.Cnpj` accepts it.
  The series is written zero-padded to five digits (`00001`).
- **`TSIdPedRegEvt`.** Anexo II describes the request id as `PRE` + access key + event type + request number, but
  the XSD pattern is `PRE` + 56 characters (key 50 + type 6) with `maxLength` 59. The library follows the schema:
  no request number.
- **Municipal parameters** are returned as `MunicipalParameters(message, raw)`; the payload sits under its own key
  (`parametrosConvenio`, `aliquotas`, `beneficio`, `regimesEspeciais`, `retencoes`) as the OpenAPI describes it.
- **DANFSe** without the renderer module is fetched from `{danfse base URL}/{chaveAcesso}` with
  `Accept: application/pdf` — suspended by NT 008/2026, see above.

### Standards radar

What the standard is doing around this release — details and check history in
[`docs/standards-watch.md`](docs/standards-watch.md):

- **CNPJ alfanumérico** in production since 2026-08-10 (XSD bundle 2026-07-27, embedded here).
- **IBS/CBS** (`IBSCBS` group): omission is tolerated until 2026-12-31; highlighting becomes mandatory in waves
  from **2026-10-01** (LC 116 services in general). Model it with `DpsRequest.ibsCbs`.
- **Simples Nacional** emitters must use the national emitter from **2026-11-01** (Resolução CGSN 191).
- **NT 008/2026**: official DANFSe generation suspended on 2026-08-03; single national PDF layout to be rendered
  by emitters — implemented by `nfse-spring-boot-danfse`.
- **NT 009/2026** (new layout, IBS/CBS adjustments, `finNFSe` credit/debit notes): published, without a schedule.

## Releasing (maintainers)

The version lives in [`gradle.properties`](gradle.properties); pushing a tag `v<version>` triggers the
[Release workflow](.github/workflows/release.yml), which builds, tests, signs, uploads the bundle to the Maven
Central Portal and creates a GitHub Release. The workflow refuses a tag that does not match `gradle.properties`.

```bash
git checkout main && git pull
git tag v1.0.0-RC1
git push origin v1.0.0-RC1
```

Secrets used: `SIGNING_KEY`, `SIGNING_PASSWORD`, `OSSRH_USERNAME`, `OSSRH_PASSWORD` (Central Portal user token).

## License

Copyright 2026 Rodrigo Montanha

Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE) for the full license text.
