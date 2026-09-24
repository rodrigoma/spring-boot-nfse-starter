# CLAUDE.md

Guidance for Claude Code (and any AI assistant) working in this repository.

## What this is

`spring-boot-nfse-starter` — a Spring Boot 4 / Kotlin starter for the **Sistema Nacional NFS-e** (nfse.gov.br), the
Brazilian national service-invoice standard. Sister project of `spring-boot-pagbank-starter`; it follows the same
module layout, convention plugins, quality gates and documentation style. The library knows the **fields** of the
standard, never a company's values: certificate, CNPJ, municipality, tax regime and service codes come from
configuration or the caller, and it keeps **no state** (numbering, emitted notes, retries are the application's job).

Canonical Portuguese domain terms — keep them: **DPS** (what the emitter sends), **NFS-e** (the note the Sefin
returns), **Sefin Nacional** (emission host), **ADN** (distribution host), **NSU** (distribution cursor),
**DANFSe** (PDF), **IBS/CBS** (tax-reform group), **chave de acesso** (50-character key).

## Commands

```
./gradlew build                        # compile, test, spotless, detekt, JaCoCo gate (80 % instructions)
./gradlew spotlessApply                # fix formatting (ktlint, max line 120 via .editorconfig)
./gradlew detekt
./gradlew :nfse-spring-boot-autoconfigure:test --tests '*DpsXmlBuilderTest*'
./gradlew :nfse-spring-boot-danfse:renderSamples   # fixtures → build/danfse/*.pdf for a visual check of the DANFSe
./gradlew :nfse-spring-boot-sample:bootRun --args='--spring.profiles.active=local'   # sandbox, no certificate
```

JDK 21 is required (`JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home` on the maintainer's Mac).

## Layout

```
nfse-spring-boot-autoconfigure/   the library — io.github.rodrigoma.nfse
  autoconfigure/   NfseAutoConfiguration, NfseProperties, NfseEnvironment, health indicator
  certificate/     PKCS#12 loading from four sources + ICP-Brasil checks, SSLContext (mTLS),
                   NfseCertificateProvider (the vault seam)
  client/          NfseClient (Sefin), AdnClient, MunicipalParametersClient, DpsAssembler, DpsPreflight
  exception/       NfseException (sealed: Rejected, Validation, Certificate, Unavailable, Unauthorized, NotFound)
  http/            error handler, logging interceptor with payload masking
  model/           dps/* (full TCInfDPS), event/*, request/*, response/*
  xml/             DpsXmlBuilder, EventXmlBuilder, XsdValidator, XmlSigner, GzipBase64, NfseXmlParser
  resources/META-INF/nfse/xsd/1.01/   official XSD bundle 2026-07-27, verbatim
nfse-spring-boot-starter/         transitive dependency only
nfse-spring-boot-danfse/          optional DANFSe v2.0 renderer (NT 008/2026) — io.github.rodrigoma.nfse.danfse
  DanfseRenderer / DanfseAutoConfiguration / NoteStatus / DanfseOptions
  layout/          NfseView (DOM view of the NFSe XML), DanfseLayout (blocks in cm, table 2.4.5 of the NT),
                   PdfCanvas (PDFBox, cm from top-left, Helvetica), QrCode (ZXing), Formats, Places, Descriptions
  resources/       gov.br logo, municipios-ibge.csv (code;name;uf), paises-iso2.csv
nfse-spring-boot-sample/          demo app; `local` profile = in-process fake Sefin/ADN with mTLS
docs/specs/                       OpenAPI specs of the four services (reference, not shipped)
docs/standards-watch.md           Notas Técnicas / schema radar and check history
docs/superpowers/specs/           design notes
```

## Invariants that are easy to break

- **Two hosts, two wire formats.** Sefin = camelCase + integer `tipoAmbiente`; ADN = PascalCase + string
  `TipoAmbiente`. The JSON mapper is case-insensitive so both bind to the same DTOs, but keep the DTOs per service
  (`ApiPayloads`) and never assume one format on the other host.
- **The ADN answers 400/404 with a full body.** `StatusProcessamento` is the real outcome (`REJEICAO`,
  `NENHUM_DOCUMENTO_LOCALIZADO`, `DOCUMENTOS_LOCALIZADOS`). `AdnClient` uses `exchange()` to read those statuses;
  do not route ADN calls through `retrieve()`.
- **Sefin error bodies:** `erros[]` on `POST /nfse`, a single `erro` object elsewhere. `NfseErrorHandler` reads both.
- **`GET/HEAD /dps/{id}` takes the full id** (`DPS` + 42 characters). `DpsId.value`, not `DpsId.digits`.
- **Only `GET /nfse/{chave}/eventos/{tipo}/{seq}` exists on the Sefin.** The list of events is an ADN call.
- **HTTP/1.1 only.** `HttpClient.Version.HTTP_1_1` in `NfseAutoConfiguration` — the Sefin kills h2 streams.
- **No namespace prefixes** in the XML (rule E1228): default namespace for the documents and for `Signature`.
- **The XSDs are embedded verbatim** from the official bundle; never patch them. `XsdValidator` serves every
  `xs:include` from the classpath through an `LSResourceResolver` — a Spring Boot fat jar (`jar:nested:` URLs)
  broke URL-based resolution once. `xmldsig-core-schema.xsd` must have no `DOCTYPE`.
- **The pedido de registro de evento id is `PRE` + chave(50) + tipo(6)** — no `nPedRegEvento`, the annex prose is
  wrong and the XSD pattern is right.
- **Series is written unpadded** (`<serie>3</serie>`); the DPS id pads it to five digits.
- **Alphanumeric CNPJ** (`[0-9A-Z]{14}`, since 2026-08-10): `FederalId.Cnpj` keeps letters; `TaxIdCheckDigits`
  computes the DV with ASCII − 48.
- **Nothing is logged with secrets**: `NfseProperties.toString()` hides the password/blob, the interceptor replaces
  `…XmlGZipB64` payloads with their size.
- **The DANFSe is a pure function of the `NFSe` XML + events.** `NfseView` reads the DOM by local name and formats
  every value (`-` for empty ones); `DanfseLayout` only positions. Nothing is computed from application state, the
  core module never depends on PDFBox (`DanfsePdfRenderer` is the seam) and the layout constants are the cm of
  NT 008 — change them only against the NT.
- **Four certificate sources, one at a time.** `location` (Resource), `base64`, `ssl-bundle`, or an
  `NfseCertificateProvider` bean — which wins over the properties, so the "exactly one source" check lives in
  `NfseCertificate.load`, not in `NfseProperties.afterPropertiesSet` (only the loader knows the bean exists).
  Never write the PKCS#12 to disk; strip whitespace before the strict Base64 decoder (wrapped `base64` output is
  the common case, and the MIME decoder would hide typos as "corrupt PKCS#12").
- **An expired certificate does not fail the context.** Structural checks (v3, not a CA, key usages) do — they
  mean the wrong file. Expiry is logged, exposed (`expiresAt`, `isUsable`), reported DOWN by the health indicator
  and enforced by `requireUsable()` in `emit`/`cancel`; reads keep working. Taking the whole application down over
  a yearly certificate would trade a fiscal problem for an outage.
- **Validate before consuming a number.** `emit` runs `DpsPreflight` (check digits) and the XSD before anything
  touches the wire — applications should take the DPS number only after `emit` returns or fails with `Rejected`.

## Quality gates

Every commit must pass `./gradlew build` (tests, JaCoCo ≥ 80 % instructions with `**/model/**` excluded), `detekt`
(max line 120, `TooManyFunctions` 15/class, `LongParameterList` 6/7 — data classes exempt, `MagicNumber` outside
tests) and `spotlessCheck`. Commits use Conventional Commits in English. The integration tests run against
`NfseStubServer` (JDK `HttpsServer` + certificates minted by BouncyCastle, test scope only).

## When the standard moves

1. Download the new XSD bundle from gov.br (check both "Documentação Atual" and "Produção Restrita"; the latter is
   often newer), diff against `resources/META-INF/nfse/xsd/1.01/`, replace verbatim.
2. Walk the type diff into `model/dps/*` (prefer optional fields), the writers in `xml/*Xml.kt` and the parser.
3. Update `docs/standards-watch.md` (radar + check history) and the README "Standards radar".
4. If the OpenAPI changed, refresh `docs/specs/` (`scripts/fetch-swagger.sh`) and `ApiPayloads`/`AdnClient`.

## Out of scope

ERP integrations, a UI, persistence/retry queues, legacy municipal (ABRASF) emitters, NF-e (goods), emission by
administrative/judicial decision, DPS issued by the taker/intermediary (the Sefin rejects it, E9996).
