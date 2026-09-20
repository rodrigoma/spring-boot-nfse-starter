# spring-boot-nfse-starter — design

Source of truth for requirements: [`prompt-spring-boot-nfse-starter.md`](../../../prompt-spring-boot-nfse-starter.md).
Conventions are copied from `rodrigoma/spring-boot-pagbank-starter` (read in full on 2026-09-19). This document
records the decisions taken where the prompt left room, or where the official documentation did not settle a
question. Rodrigo authorized these decisions to be taken autonomously.

## Sources read

- PagBank starter: `buildSrc/`, `settings.gradle.kts`, all three modules, workflows, README, CONTRIBUTING,
  CHANGELOG, `detekt.yml`. Notable: it has **no** stub HTTP server (`PagBankStub` does not exist) — tests use
  `MockClientHttpRequest`/`MockRestServiceServer`. Coverage gate: 80 % instructions, `**/model/**` excluded.
- Official docs (`docs/*.pdf` + downloaded from gov.br): Emissor Público API manual v1.2 (Oct/2025), ADN
  contribuintes manual, decisão administrativa/judicial manual, `NFSe-ESQUEMAS_XSD v1.01 (2026-02-09)`,
  Anexo I (leiaute + RN DPS/NFS-e, 2026-02-09), Anexo II (eventos, 2026-01-22).
- The Swagger (only reachable with an ICP-Brasil certificate — every probe returned 403) could **not** be
  read, so JSON field names and response shapes come from the ecosystem's known contract and are made
  tolerant (case-insensitive properties, unknown properties ignored). Listed in README → "Notes on the
  official docs" so they can be confirmed with a real certificate.

## Facts confirmed in the schemas/annexes that drive the design

- Namespace `http://www.sped.fazenda.gov.br/nfse`, `versao="1.01"`. **No namespace prefixes allowed** in the
  data area (rule E1228) → the DPS uses the default namespace, and the `Signature` element is marshalled with
  the default xmldsig namespace (no `ds:` prefix). XML must be UTF-8 (E1229).
- `DPS/infDPS/@Id` = `"DPS"` + IBGE(7) + federal id type(1: `1`=CPF, `2`=CNPJ) + federal id(14, zero-padded)
  + series(5) + number(15). Series range for own software: `00001`–`49999` (E0010).
- `xNome` of the provider **must not** be sent when the provider is the emitter (`tpEmit=1`, E0121). The
  provider address is optional in that case; `regTrib` is mandatory. `regApTribSN` is mandatory when
  `opSimpNac=3` and forbidden otherwise (E0162/E0166).
- `pAliq` max 5 % (E0595), pattern `TSDec1V2`; money is `TSDec15V2` with exactly 2 decimals.
- Cancellation event = `e101101` with fixed `xDesc="Cancelamento de NFS-e"`, `cMotivo` ∈ {1 Erro na emissão,
  2 Serviço não prestado, 9 Outros}, `xMotivo` 15–255 chars (mandatory). Pedido id = `"PRE"` + access
  key(50) + event type(6) — the prose of Anexo II adds `nPedRegEvento`(3), but the XSD pattern `PRE[0-9]{56}`
  and `maxLength` 59 do not; the schema wins. Signed with the emitter's certificate (E1991).
- `TSSerieDPS` of the 2026-02-09 package declares `^0{0,4}\d{1,5}$` (`^`/`$` are literals in XSD regex). The
  2026-07-27 package from the restricted-production page fixes it and adds the alphanumeric CNPJ; that is the one
  embedded, verbatim.
- Certificate rules (Anexo I): X.509 v3, not a CA, KeyUsage `digitalSignature` + `nonRepudiation`
  (signature) and `clientAuth` EKU (transport), ICP-Brasil chain, CNPJ/CPF in `otherName` OID
  `2.16.76.1.3.3` / `2.16.76.1.3.1`.
- Signature algorithm is **not** stated in Anexo I or the XSDs (`xmldsig-core-schema` is generic). Default
  RSA-SHA256 + SHA-256 digests + inclusive C14N + enveloped transform, matching the reference
  implementations of the national emitter; `nfse.signature.algorithm=RSA_SHA1` is available as a fallback.
- `xmldsig-core-schema.xsd` of the 2026-02-09 package ships with a `DOCTYPE` pointing at w3.org; the 2026-07-27
  package removes it. All XSDs are embedded verbatim under `META-INF/nfse/xsd/1.01/`.

## Structure

```
nfse-spring-boot-parent
├── buildSrc                     nfse.kotlin-library / nfse.publish / nfse.security (same versions as PagBank)
├── nfse-spring-boot-autoconfigure
│   └── io.github.rodrigoma.nfse
│       ├── autoconfigure   NfseAutoConfiguration, NfseHealthIndicatorAutoConfiguration, NfseProperties,
│       │                   NfseEnvironment, NfseRestClientCustomizer
│       ├── certificate     NfseCertificate (PKCS#12 loading + checks), NfseSslContextFactory
│       ├── client          NfseClient (interface), DefaultNfseClient, NfseApiPaths
│       ├── exception       NfseException (sealed) + NfseError
│       ├── http            NfseErrorHandler, NfseLoggingInterceptor, NfseBodyMasker
│       ├── model           dps/* (full TCInfDPS in English), event/*, response/* (NfseResult, Nfse,
│       │                   NfseEvent, MunicipalParameters), request/* (DpsRequest and friends)
│       └── xml             DpsXmlBuilder, EventXmlBuilder, XmlSigner, XsdValidator, GzipBase64, XmlSupport
├── nfse-spring-boot-starter     transitive dependency only
└── nfse-spring-boot-sample      Spring Boot app: POST /sample/nfse emits a demo DPS in restricted production
```

Stack: Kotlin 2.4.20, Spring Boot 4.1.1, JDK 21 toolchain, Gradle 9.7.1, Spotless/ktlint 1.5.0, Detekt
1.23.8, OWASP dependency-check 12.1.1, JaCoCo ≥ 0.80 (models excluded). Group `io.github.rodrigoma`, version
`1.0.0-RC1`.

Runtime dependencies: Spring `RestClient` (JDK `HttpClient` request factory for mTLS), Jackson 3 (`tools.jackson`)
already on the Boot classpath, JDK `javax.xml` / `org.w3c.dom` / `javax.xml.crypto.dsig`. **No** JAXB, no
BouncyCastle at runtime. BouncyCastle `bcpkix` is a **test-only** dependency because the JDK has no public
API to mint X.509 certificates (needed for the mTLS stub and the ICP-Brasil `otherName` extension tests).

## Public API

```kotlin
interface NfseClient {
    fun emit(request: DpsRequest): NfseResult
    fun get(accessKey: String): Nfse
    fun accessKeyOf(dpsId: DpsId): String?          // null on 404
    fun exists(dpsId: DpsId): Boolean               // HEAD
    fun cancel(accessKey: String, reason: CancellationReason, justification: String): NfseEvent
    fun events(accessKey: String): List<NfseEvent>
    fun danfse(accessKey: String): ByteArray
    fun municipalAgreement(municipalityIbge: Int): MunicipalParameters
    fun municipalParameters(municipalityIbge: Int, serviceCode: String): MunicipalParameters
}
```

- `DpsRequest` holds what varies per invoice: `number`, `series?`, `competenceDate`, `issuedAt?`, `taker?`,
  `intermediary?`, `service`, `amounts`, `substitutes?`, `ibsCbs?`, `provider?` (overrides the configured
  emitter for multi-CNPJ apps). `Dps` is the full XSD model; `DpsAssembler` merges properties + request.
- Money and percentages are `BigDecimal` (documented). Enums mirror the XSD code tables with the XML code as
  a property (`IssqnTaxation.TAXABLE.code == "1"`).
- `NfseResult(accessKey, nfseNumber, processedAt, dpsId, nfseXml, dpsXml)`.
- Low-level pieces are public: `DpsXmlBuilder`, `EventXmlBuilder`, `XmlSigner`, `XsdValidator`, `GzipBase64`,
  the `nfseRestClient` bean and `NfseRestClientCustomizer`.
- Exceptions (nested sealed subclasses, as PagBank does): `NfseException.Rejected(errors, httpStatus)`,
  `.Validation(errors)`, `.Certificate(message)`, `.Unavailable(message, statusCode?)`, `.Unauthorized`,
  `.NotFound`.

## Properties (prefix `nfse`)

`enabled`, `environment` (`RESTRICTED_PRODUCTION` default | `PRODUCTION`), `certificate.pfx-path` /
`certificate.pfx-base64`, `certificate.password`, `certificate.trust-store-path/-password` (optional),
`emitter.cnpj` | `emitter.cpf`, `emitter.municipal-registration`, `emitter.name`,
`emitter.municipality-ibge`, `emitter.address.*`, `emitter.email`, `emitter.phone`,
`emitter.tax-regime.simples-nacional` (`NOT_OPTING` | `MEI` | `ME_EPP`), `emitter.tax-regime.simples-nacional-assessment`
(`SIMPLES_NACIONAL` | `FEDERAL_ONLY` | `NONE`), `emitter.tax-regime.special-regime` (`NONE`, `COOPERATIVE`,
`ESTIMATE`, `MUNICIPAL_MICRO_ENTERPRISE`, `NOTARY`, `SELF_EMPLOYED_PROFESSIONAL`, `PROFESSIONAL_COMPANY`,
`OTHER`), `emitter.dps-series` (default 1), `application-version`, `log-requests`,
`health-indicator-enabled`, `connect-timeout`, `read-timeout`, `base-url.sefin`, `base-url.danfse`.

Validation happens in `afterPropertiesSet` (properties) and in `NfseCertificate` (certificate, at startup).

## HTTP contract used (to be confirmed against the Swagger)

| Call | Request | Response |
|---|---|---|
| `POST {sefin}/nfse` | `{"dpsXmlGZipB64": "..."}` | `tipoAmbiente, versaoAplicativo, dataHoraProcessamento, idDps, chaveAcesso, nfseXmlGZipB64` |
| `GET {sefin}/nfse/{chave}` | — | `..., nfseXmlGZipB64` |
| `GET / HEAD {sefin}/dps/{id}` | — | `..., chaveAcesso` / status only |
| `POST {sefin}/nfse/{chave}/eventos` | `{"pedidoRegistroEventoXmlGZipB64": "..."}` | `..., idEvento, tipoEvento, numSeqEvento, eventoXmlGZipB64` |
| `GET {sefin}/nfse/{chave}/eventos[/{tipo}[/{seq}]]` | — | `eventos: [{ ..., eventoXmlGZipB64 }]` (also accepts a single object) |
| `GET {sefin}/parametros_municipais/{mun}/convenio`, `/{mun}/{servico}` | — | opaque JSON → `MunicipalParameters.raw` |
| `GET {danfse}/{chave}` | — | `application/pdf` |
| errors 400/422 | — | `{"erros":[{"codigo","descricao","complemento"}]}` |

Base URLs: restricted production `https://sefin.producaorestrita.nfse.gov.br/SefinNacional` and
`https://adn.producaorestrita.nfse.gov.br/danfse`; production `https://sefin.nfse.gov.br/SefinNacional` and
`https://adn.nfse.gov.br/danfse`. Both overridable.

## Testing

- Unit: XML builder (minimal + complete case vs expected XML), XSD validation (valid/invalid), `DpsId`,
  signer sign+verify with `javax.xml.crypto`, gzip/base64 round trip, response/error mapping, properties.
- Integration: `NfseStubServer` on JDK `HttpsServer` with a self-signed certificate minted by BouncyCastle
  at test time; one case sets `needClientAuth` (real mTLS) and one asserts the handshake fails without the
  client certificate. Covers emit, get, dps, cancel, events, DANFSE, parameters, 4xx with error list, 5xx,
  timeout.
- Auto-configuration with `ApplicationContextRunner`: enabled/disabled, missing properties, bad certificate
  → context fails with a clear message, customizer applied, health indicator conditions.

## Out of scope (documented in README)

Numbering persistence, fiscal decisions (rates, service codes), ADN distribution by NSU, emission by
tomador/intermediário via the high-level API (possible through the low-level `Dps` model), administrative /
judicial "bypass" emission.
