# NFS-e Spring Boot Starter

[![CI](https://github.com/rodrigoma/spring-boot-nfse-starter/actions/workflows/ci.yml/badge.svg)](https://github.com/rodrigoma/spring-boot-nfse-starter/actions/workflows/ci.yml)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.rodrigoma/nfse-spring-boot-starter.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/io.github.rodrigoma/nfse-spring-boot-starter)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

A Spring Boot auto-configuration library for the **Sistema Nacional NFS-e** — the Brazilian national standard for
the electronic service invoice (NFS-e, [gov.br/nfse](https://www.gov.br/nfse)). Configure the emitter's certificate
and identification, and get an `NfseClient` that builds the DPS, validates it against the official XSD, signs it,
sends it to the Sefin Nacional over mutual TLS and returns the generated NFS-e — plus lookup, cancellation, events,
municipal parameters and the DANFSE (PDF).

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
| `nfse.emitter.name` | `String` | — | No | Kept for DPS issued by a taker/intermediary; **not sent** when the provider emits (rule E0121) |
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
| `nfse.base-url.danfse` | `String` | by environment | No | Overrides the DANFSE base URL |

The context **fails to start** when a required property is missing, when the certificate cannot be opened (wrong
password, corrupt file), is expired, is a CA certificate or lacks the *Digital Signature* / *Non Repudiation* key
usages required by Anexo I. A certificate whose CNPJ base differs from `nfse.emitter.cnpj` logs a warning — the Sefin
rejects such a DPS with E0718.

### Environments

| Value | `tpAmb` | Sefin Nacional | DANFSE |
|---|---|---|---|
| `RESTRICTED_PRODUCTION` (default) | `2` | `https://sefin.producaorestrita.nfse.gov.br/SefinNacional` | `https://adn.producaorestrita.nfse.gov.br/danfse` |
| `PRODUCTION` | `1` | `https://sefin.nfse.gov.br/SefinNacional` | `https://adn.nfse.gov.br/danfse` |

Production is only reached with `PRODUCTION` spelled out (or an explicit `base-url`).

## Auto-configured beans

| Bean name | Type | Purpose |
|---|---|---|
| `nfseClient` | `NfseClient` | The API you call |
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
call with `provider = ServiceProvider(...)` when one application issues for several CNPJs). Foreign takers use
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
val events = nfse.events(accessKey)                  // List<NfseEvent>
val pdf: ByteArray = nfse.danfse(accessKey)          // DANFSE

// Reconciling after a lost response: the access key from the DPS identifier
val dpsId = DpsId(municipalityIbge = 3550308, emitter = FederalId.Cnpj("12345678000195"), series = 1, number = 42)
nfse.exists(dpsId)                                   // HEAD /dps/{id}
nfse.accessKeyOf(dpsId)                              // GET /dps/{id}, null when no NFS-e was generated

nfse.municipalAgreement(3550308)                     // GET /parametros_municipais/{mun}/convenio
nfse.municipalParameters(3550308, "010701")          // GET /parametros_municipais/{mun}/{servico}
```

### Error handling

| Exception | When | Notes |
|---|---|---|
| `NfseException.Validation` | the DPS/event does not pass the embedded XSD | Nothing is sent; `errors` lists the violations |
| `NfseException.Rejected` | HTTP 400/422 from the Sefin | `errors` carries `code`, `description`, `detail` (`E0010`, …); `httpStatus` |
| `NfseException.Unauthorized` | HTTP 401/403 | The certificate was not accepted for this call |
| `NfseException.NotFound` | HTTP 404 | NFS-e, DPS or event not found (or not visible to this certificate) |
| `NfseException.Unavailable` | network error, timeout, HTTP 429/5xx | Retry later; `statusCode` when there was a response |
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
- **Municipal-layout NFS-e** (`tpEmis=2`), emission by administrative/judicial decision (`/decisao-judicial/nfse`)
  and ADN distribution by NSU (`/DFe/{nsu}`).
- **A3 certificates** and HSMs.

## Notes on the official docs

Decisions taken where the documentation (Emissor Público API manual v1.2, Anexo I/II v1.01, XSD v1.01 of
2026-02-09) is silent or inconsistent. The Swagger of the restricted-production environment is only reachable with a
certificate; check these against it when you have one.

- **JSON field names.** `dpsXmlGZipB64` (request), `nfseXmlGZipB64`, `chaveAcesso`, `idDps`,
  `dataHoraProcessamento` (responses), `pedidoRegistroEventoXmlGZipB64` / `eventoXmlGZipB64` (events) and
  `erros[].codigo/descricao/complemento` (rejections). Property names are matched case-insensitively and unknown
  ones are ignored, so small variations still bind; a rejection body that does not parse becomes a single error
  with the raw text.
- **`GET/HEAD /dps/{id}`** receives the 42 digits of the identifier (without the `DPS` literal), as the manual
  describes the parameter.
- **Signature algorithm.** Anexo I names none; the library signs with RSA-SHA256 / SHA-256, inclusive C14N and the
  enveloped transform, in the default xmldsig namespace (namespace prefixes are rejected by rule E1228).
- **`TSSerieDPS`.** The official XSD declares the pattern `^0{0,4}\d{1,5}$`. In XML Schema regular expressions
  `^` and `$` are ordinary characters, so a conforming validator rejects every series. The embedded copy uses the
  intended pattern `0{0,4}\d{1,5}`; the series is written zero-padded to five digits (`00001`).
- **`TSIdPedRegEvt`.** Anexo II describes the request id as `PRE` + access key + event type + request number, but
  the XSD pattern is `PRE[0-9]{56}` (key 50 + type 6) with `maxLength` 59 = 3 + 56. The library follows the schema:
  no request number.
- **`xmldsig-core-schema.xsd`** ships with a `DOCTYPE` pointing at w3.org; the embedded copy has it removed so
  validation never touches the network. Every other schema is embedded verbatim.
- **Municipal parameters** are returned as the raw JSON object (`MunicipalParameters.raw`); the manual does not
  publish their layout.
- **DANFSE** is fetched from `{danfse base URL}/{chaveAcesso}` with `Accept: application/pdf`.

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
