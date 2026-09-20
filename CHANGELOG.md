# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/); versions follow [SemVer](https://semver.org/).

## [1.0.0-RC1] — 2026-09-20

Initial release candidate.

### Added
- `nfse-spring-boot-danfse`, an optional module that renders the DANFSe v2.0 (NT 008/2026) locally from the
  `NFSe` XML with Apache PDFBox and ZXing: one A4 page after the model of Anexo I, QR Code of the public query,
  restricted-production warning, CANCELADA / SUBSTITUÍDA watermark from the events, optional "Canhoto"
  (`nfse.danfse.stub`). With the module on the classpath `NfseClient.danfse()` renders instead of calling the
  suspended ADN service (`nfse.danfse.enabled=false` restores the call); `DanfsePdfRenderer` is the hook.
- Contract aligned with the OpenAPI specs of the four services (`docs/specs/`): `GET/HEAD /dps/{id}` with the full
  id, single-`erro` error bodies, `alertas[]` on emission (`NfseResult.alerts`), `event(accessKey, type, sequence)`,
  the event list and the distribution by NSU through the ADN (`events`, `distribution`), municipal parameters
  through the ADN Parâmetros Municipais (`MunicipalParametersClient`, six endpoints), HTTP/1.1 forced,
  `Retry-After` on 429, CPF/CNPJ check-digit preflight (`TaxIdCheckDigits`).
- `NfseClient` for the Sistema Nacional NFS-e: `emit` (DPS → NFS-e, synchronous), `get`, `accessKeyOf` / `exists`
  (DPS lookup), `cancel` (event `e101101`), `events`, `danfse` (PDF), `municipalAgreement` and `municipalParameters`.
- Full `DPS` v1.01 model in Kotlin (`Dps`, `DpsRequest` short form, IBS/CBS group included), `DpsXmlBuilder`,
  `EventXmlBuilder`, `XsdValidator` with the official schemas embedded, `XmlSigner` (XML-DSig, RSA-SHA256),
  `GzipBase64`, `NfseXmlParser`, `DpsId`.
- Auto-configuration from `nfse.*`: ICP-Brasil A1 certificate loading with startup checks (validity, v3, not a CA,
  key usages, CNPJ/CPF extension), mutual-TLS `RestClient` (`nfseRestClient`, `NfseRestClientCustomizer`), typed
  `NfseException` hierarchy, opt-in request logging with payload masking, opt-in Actuator health indicator.
- Sample application emitting a demo NFS-e in restricted production, with a `local` profile that runs the whole
  flow against an in-process fake Sefin Nacional (mTLS, XSD validation, signature verification).
