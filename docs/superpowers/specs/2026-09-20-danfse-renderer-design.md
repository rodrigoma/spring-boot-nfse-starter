# DANFSe renderer — mapping of NT 008/2026 and effort estimate

Status: **implemented** as `nfse-spring-boot-danfse` (see "As built" at the end). Source: NT 008 v1.02 of 2026-07-14
([`docs/nt-008-danfse-v1-02.pdf`](../../nt-008-danfse-v1-02.pdf)), model in
[`docs/danfse-v2-model.png`](../../danfse-v2-model.png).

## What the NT requires

- The official generation API was suspended on **2026-08-03**; emitters render the PDF themselves.
- **One A4 page**, portrait, margins 0.15–0.20 cm, page border 1 pt, block dividers 0.5 pt, light-grey (5 %)
  background on the header, block titles, "Emitente da NFS-e" and "Valor Líquido + IBS/CBS".
- **Fixed model (Anexo I)** with **13 blocks and ~98 positioned fields** (all coordinates in cm, table 2.4.5):
  header (logo, "DANFSe v2.0 / Documento Auxiliar da NFS-e", municipality/environment), NFS-e identification +
  **QR Code** (`https://www.nfse.gov.br/ConsultaPublica/?tpc=1&chave=<chave>`, ≥ 1.52 cm, at X 17.48 / Y 1.67 cm,
  with the fixed 3-line authenticity text), provider, taker, recipient (IBS/CBS), intermediary, service, municipal
  taxation (ISSQN), federal taxation (until competence 2026), IBS/CBS taxation, totals, additional information,
  optional stub (canhoto).
- **Typography**: Arial for labels (block titles 7 pt bold uppercase, field labels 6 pt bold), Microsoft Sans
  Serif for values (7 pt), header title 9 pt bold. Restricted production adds **"NFS-e SEM VALIDADE JURÍDICA"**
  in red (9 pt bold). Every empty field prints a dash (`-`).
- **Dynamic layout**: taker/recipient/intermediary/ISSQN blocks collapse to a one-line notice when absent
  ("TOMADOR/ADQUIRENTE DA OPERAÇÃO NÃO IDENTIFICADO NA NFS-e", "O DESTINATÁRIO É O PRÓPRIO TOMADOR…",
  "TRIBUTAÇÃO MUNICIPAL (ISSQN) - OPERAÇÃO NÃO SUJEITA AO ISSQN"); the address/e-mail lines and the stub are
  optional; freed height goes to "Descrição do Serviço" / "Informações Complementares". Text that does not fit is
  cut with "…" (suggested character counts per field, e.g. 77 for names/addresses, 1997 for the complementary
  information).
- **Watermarks**: "CANCELADA" / "SUBSTITUÍDA" diagonal, Arial ≥ 50 pt, grey K35 — requires knowing the note's
  events, not only its XML.
- **"Informações Complementares"** is a composed field: `xInfComp | NFS-e Subst.: <chSubstda> | Doc. Ref.: |
  Cod. Obra: | Insc. Imob.: | Cod. Evt.: | Doc. Tec.: | Núm. Ped.: | Item Ped.: | Inf. A. T. Mun.: (xOutInf)` plus the
  mandatory fixed line `Totais Aproximados dos Tributos cfe. Lei nº 12.741/2012: Federais: R$/% ; Estaduais: … ;
  Municipais: …`.
- Descriptions of codes are printed, not codes: `tpEmit`, `cStat`, `opSimpNac`, `regApTribSN`, `regEspTrib`,
  `tribISSQN`, `tpImunidade`, `tpSusp`, `tpBM`, `tpRetISSQN`, `CST`, IBS/CBS `CST`/`cClassTrib`/`cIndOp`.

## What the library still lacks for it

| Need | Today | Work |
|---|---|---|
| Full NFS-e reading | `NfseXmlParser` reads ~10 fields | Parse `infNFSe` (emitter, `valores`, `IBSCBS` computed totals, `xLocIncid`, `xTribNac/xTribMun/xNBS`) **and** the embedded DPS back into `Dps` (the reverse of the builder) — largest single item |
| Municipality names for taker/recipient/intermediary | only `xLocEmi`/`xLocIncid`/`xLocPrestacao` come in the XML | Embed the IBGE table of Anexo A (5 570 municipalities + 250 countries, ~150 KB as a compact resource) |
| Descriptions of every code table | enums carry the code only | Add the official Portuguese descriptions (Anexo I domain tables, IBS/CBS Anexo VII for `cIndOp`) |
| PDF generation | none (JDK has no PDF API) | **Apache PDFBox 3** (Apache 2) — absolute positioning, fonts, images, rotation for watermarks |
| QR Code | none | **ZXing core** (Apache 2), rendered as an image into the page |
| Fonts | — | Arial / MS Sans Serif are proprietary; embed **Liberation Sans** (SIL OFL, metric-compatible with Arial) or use the built-in Helvetica. Decision needed — Liberation Sans is the safer reading of "fonte Arial" |
| Logo | — | Official horizontal PNG from gov.br "Logos da NFS-e" page (usage allowed for the DANFSe) |
| Events for the watermark | `events()` exists | `render(nfse, events)`; `danfse(chave)` fetches both |
| Visual verification | — | Golden-image tests: render fixtures → `pdftoppm` → compare against reviewed PNGs; assert single page and text presence via PDFBox text extraction |

## Proposed shape

- New module **`nfse-spring-boot-danfse`** (published artifact, optional): depends on the autoconfigure module,
  PDFBox and ZXing; nothing flows the other way.
- `DanfseRenderer.render(nfse: Nfse, events: List<NfseEvent> = emptyList(), options: DanfseOptions): ByteArray`;
  `DanfseOptions(emitterLogo: ByteArray? = null, stub: Boolean = false)`.
- Auto-configuration in the module registers a `DanfseRenderer` bean; the core's `NfseClient.danfse(chave)` switches
  to local rendering when a renderer bean is present (fetch XML + events → render), otherwise keeps calling the
  ADN (currently suspended).
- Layout engine: a list of blocks with their fields and rules (from table 2.4.5), a cursor that applies the
  suppressions and grows the free-text areas, one page only.

## Estimate

| Piece | Effort |
|---|---|
| NFS-e/DPS full parser + tests | 1 day |
| Code-table descriptions + IBGE/country tables | ½ day |
| Layout engine + all blocks per table 2.4.5, QR, logo, watermark, dash rule, truncation | 1½–2 days |
| Module/auto-config wiring, README, sandbox integration, golden-image tests | ½ day |
| **Total** | **≈ 3½–4 days of focused work** |

Risks: fidelity judgement (no validator exists — the reference is the model image and the table), font choice,
and the pending NT for "novos fatos geradores" (a different DANFSe for operations that were not invoiced before —
out of scope until published).

## Recommendation

Build it after the first real emission with the certificate (the XML samples from restricted production are the
best fixtures for the parser and for the golden images). Start with the parser, which is useful on its own.

## As built

The module follows the proposed shape with these deviations from the table above:

- **No full `Dps` parser.** The renderer reads the `NFSe` XML through a DOM view (`layout/NfseView`) that walks
  the paths of table 2.4.5 by local name and formats each value; it does not build the `Dps` object model back.
  Cheaper, and the DANFSe needs the text of the fields, not the types.
- **Fonts: Helvetica** (PDF standard 14, metric-compatible with Arial) instead of an embedded Liberation Sans —
  nothing to ship, the PDF stays ~95 KB. Text is sanitised to WinAnsi (characters outside it print as `?`).
- **`DanfseOptions(stub)`** only; the emitter logo option was dropped (the NT fixes the gov.br logo).
- **Watermark** drawn under the content (first), 60 pt Helvetica-Bold, grey 0.65, 45°.
- **Verification**: text-extraction tests with PDFBox (`DanfseRendererTest`) plus a visual check of
  `./gradlew :nfse-spring-boot-danfse:renderSamples` against the model of Anexo I. There is no golden-image test
  and no comparison with a government-generated PDF (none is available since the suspension).
- **Federal block** printed for competences ≤ 2026 (NT note 6); the "Totais Aproximados dos Tributos" line is
  always kept when "Informações Complementares" overflows — the notes are truncated instead.

