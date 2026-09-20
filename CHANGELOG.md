# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/); versions follow [SemVer](https://semver.org/).

## [1.0.0-RC1] — 2026-09-20

Initial release candidate.

### Added
- `NfseClient` for the Sistema Nacional NFS-e: `emit` (DPS → NFS-e, synchronous), `get`, `accessKeyOf` / `exists`
  (DPS lookup), `cancel` (event `e101101`), `events`, `danfse` (PDF), `municipalAgreement` and `municipalParameters`.
- Full `DPS` v1.01 model in Kotlin (`Dps`, `DpsRequest` short form, IBS/CBS group included), `DpsXmlBuilder`,
  `EventXmlBuilder`, `XsdValidator` with the official schemas embedded, `XmlSigner` (XML-DSig, RSA-SHA256),
  `GzipBase64`, `NfseXmlParser`, `DpsId`.
- Auto-configuration from `nfse.*`: ICP-Brasil A1 certificate loading with startup checks (validity, v3, not a CA,
  key usages, CNPJ/CPF extension), mutual-TLS `RestClient` (`nfseRestClient`, `NfseRestClientCustomizer`), typed
  `NfseException` hierarchy, opt-in request logging with payload masking, opt-in Actuator health indicator.
- Sample application emitting a demo NFS-e in restricted production.
