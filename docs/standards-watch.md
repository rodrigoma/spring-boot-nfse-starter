# Standards watch — NFS-e Padrão Nacional

> How to use: ask the assistant to *read `docs/standards-watch.md` and run the standards check*. After each check,
> update the history at the bottom (and the "Standards radar" in the README when something changed).

## State of the library at the last check (2026-09-20, 1.0.0-RC1)

- Aligned with the layout **in production**: XSD bundle **2026-07-27** (RTC v1.01 + NT 007 `tpRetPisCofins` +
  alphanumeric CNPJ), embedded verbatim. The "Documentação Atual" page still lists the 2026-02-09 zip; the newer
  one is on the "Produção Restrita" page.
- **Alphanumeric CNPJ in production since 2026-08-10** (official deployment log). `FederalId.Cnpj`, `DpsId`, the
  cancellation id and the check-digit preflight accept it. Known official inconsistency: `TSChaveNFSe`
  (`[0-9]{6}([0-9A-Z]{14})[0-9]{30}`) places the alphanumeric window at positions 7–20 while the key layout puts
  the federal id at 10–23; the library mirrors each pattern where it applies.
- **IBS/CBS**: per CGNFS-e guidance of 2026-08-07, omission of the `IBSCBS` group does **not** reject the NFS-e
  until 2026-12-31 (but is a compliance breach); mandatory highlighting in waves — **2026-10-01** (LC 116 services
  in general), **2026-12-01** (digital platforms, sub-items 1.03/1.05/1.09/16.01, intangible goods, condominiums,
  rentals), **2027-01-01** (Simples Nacional opt-ins). The model covers the whole group (`DpsRequest.ibsCbs`).
- **Simples Nacional**: mandatory emission through the national emitter postponed to **2026-11-01**
  (Resolução CGSN 191 of 2026-08-04).
- **NT 008/2026** (DANFSe v2.0): the official PDF service was **suspended on 2026-08-03**; emitters render the
  single national layout themselves. `NfseClient.danfse()` remains but is expected to fail until the service
  returns; a local renderer is on the roadmap.
- **NT 009/2026** (v1.0.1; replaces NT 005/007): published without a schedule; brings renames (`vCalcDR` →
  `vCalcAjusteBCISSQN`…), `vDedRed` + `gReeRepRes` → `vAjusteBC`, new groups (`gIBSCBSAjuste`, `gPgtoVinc`,
  `gLocacao`, `gUnidImob`, `gTribSN`), `finNFSe` credit/debit notes, CNPJ N→C. Not implemented — no XSD yet.
- **`tpEmit = 2/3`** (taker/intermediary emission) is rejected by the Sefin in this version (E9996).
- Anexo II (events) v1.01-20260122: pedido id = `PRE` + chave(50) + tipo(6), no `nPedRegEvento`.

## Check task

Search primary sources first:

- News: https://www.gov.br/nfse/pt-br/noticias
- Technical documentation: https://www.gov.br/nfse/pt-br/biblioteca/documentacao-tecnica ("Documentação Atual",
  "Produção Restrita", "RTC", "Atualizações e Implantações")

Questions to answer:

1. Was a schedule for **NT 009** published (restricted production / production dates)? New XSD bundle (> 1.01)?
2. New Notas Técnicas (010+) or new versions of the existing ones? What do they change?
3. Did **Anexo II** (events) change? The library follows v1.01-20260122.
4. Anything on the **IBS/CBS** tolerance (2026-12-31) or the highlighting waves? On the Simples Nacional date?
5. Is the **DANFSe** service back, or is the DANFSe v2.0 layout final?
6. Is there a newer XSD zip on either documentation page? Diff it against `resources/META-INF/nfse/xsd/1.01/`.

Then check the **HTTP contract**, which the pages above never mention (it moves silently, and only when the change
is already in production — the Notas Técnicas are the early warning, this is the confirmation):

```
scripts/fetch-swagger.sh /path/to/certificado.pfx      # asks the password, fetches and diffs against docs/specs/
scripts/fetch-swagger.sh --update /path/to/certif.pfx  # …and refreshes docs/specs/ when something moved
./gradlew test                                          # ApiSpecConformanceTest: does the client still match?
```

The Swagger is behind mutual TLS, so this step cannot run unattended in CI; it needs the certificate and a human
to type its password. What *is* automated is the other direction: `ApiSpecConformanceTest` fails the build when
the client and the committed specs disagree, so refreshing the specs is enough to turn a change on the
government's side into a red build.

What to do with the outcome:

- **New XSDs** → follow "When the standard moves" in `CLAUDE.md` (embed verbatim, walk the type diff, bump MINOR).
- **Schedule only** → update this file and the README radar.
- **Contract changed** → refresh `docs/specs/` with `--update`, let `ApiSpecConformanceTest` show what broke,
  then fix `ApiPayloads` / `NfseApiPaths` / `AdnClient`.
- **Nothing** → record the check below with the next suggested date.

## Check history

| Date | Outcome | Next check |
|---|---|---|
| 2026-10-06 | First NFS-e issued end to end in restricted production (São Caetano do Sul enabled the environment on 2026-10-05, as the municipality had said). Learned from the live service: the ADN returns **the NFS-e itself** in the `Eventos` lote, so `events()` must filter by `TipoDocumento`; a **ME/EPP** provider may not send `indTotTrib` (**E0712**) and `totTrib` is a mandatory XSD choice, so `pTotTribSN` is the only sensible option; the Sefin fills the emitter address from the CNPJ registry **without leading zeros** in the CEP; the alíquota must be absent for ME/EPP apurando pelo SN (**E0625**). Validation order observed: cTribMun → IM → cTribNac → regime rules. The QR Code now points at the restricted-production Consulta Pública for `tpAmb = 2`, a documented deviation from NT 008. | with the next check |
| 2026-09-29 | First emission attempts against restricted production with a real certificate. No note could be generated, for environmental reasons only: the emitter's municipality (São Caetano do Sul) has no convênio there (**E0037**), and pointing the DPS at an active municipality is refused because the Sefin checks the CNPJ's establishment against the CNPJ/CNC registries (**E0084**). Everything short of the generated note was exercised end to end — mTLS, XSD, XML-DSig, gzip+base64 — and five distinct business rules came back correctly parsed (E0037, E0314, E0120, E0312, E0084). Also learned: São Paulo administers `01.09.01.*` but not `01.09.02.*`; Curitiba administers none of the ten codes checked; the `Aliquota` payload carries `DtFim` and a closed window is what E0314 reports. | with the next check |
| 2026-09-29 | The `codigoServico` of the ADN Parâmetros Municipais is the **complete** code — `cTribNac` + the 3-digit municipal complement, with separators (`01.09.02.001`) — and not the `cTribNac` of the DPS, which the service refuses with "deve ser composto por nove dígitos" (it counts digits, so the dots are required). Established against the live service; the OpenAPI does not document it. `ServiceCodes.complete` normalises both spellings. Also observed: São Caetano do Sul (3548807) has **no active convênio in restricted production**, while São Paulo, Rio, Belo Horizonte and Curitiba do — relevant for the first emission test. | with the next check |
| 2026-09-29 | OpenAPI of the four services fetched from restricted production with the emitter's own certificate: ADN Contribuintes and ADN Parâmetros Municipais identical to the 2026-04-16 capture, Sefin different only in generated examples and two corrected summaries, ADN DANFSe 404 (suspended by NT 008). The HTTP contract of the library is confirmed first-hand; `docs/specs/sefin-nacional.openapi.json` refreshed. | **~2026-10-05** (right after the 2026-10-01 IBS/CBS wave), then monthly. |
| 2026-09-20 | Baseline, established from the official pages and the open-nfse standards watch of 2026-08-24: bundle 2026-07-27 adopted; IBS/CBS highlighting wave of 2026-10-01 ahead; NT 008 DANFSe suspended; NT 009 without schedule. | **~2026-10-05** (right after the 2026-10-01 IBS/CBS wave), then monthly. |
