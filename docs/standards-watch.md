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

What to do with the outcome:

- **New XSDs** → follow "When the standard moves" in `CLAUDE.md` (embed verbatim, walk the type diff, bump MINOR).
- **Schedule only** → update this file and the README radar.
- **Nothing** → record the check below with the next suggested date.

## Check history

| Date | Outcome | Next check |
|---|---|---|
| 2026-09-20 | Baseline, established from the official pages and the open-nfse standards watch of 2026-08-24: bundle 2026-07-27 adopted; IBS/CBS highlighting wave of 2026-10-01 ahead; NT 008 DANFSe suspended; NT 009 without schedule. | **~2026-10-05** (right after the 2026-10-01 IBS/CBS wave), then monthly. |
