# OpenAPI specs of the Sistema Nacional NFS-e

Swagger/OpenAPI documents of the four services the library talks to, as served by the restricted-production
environment (the Swagger UIs are only reachable with an ICP-Brasil certificate):

| File | Service | Base URL (restricted production) |
|---|---|---|
| `sefin-nacional.openapi.json` | Sefin Nacional — emission, lookup, events | `https://sefin.producaorestrita.nfse.gov.br/SefinNacional` |
| `adn-contribuinte.openapi.json` | ADN Contribuintes — distribution by NSU, events by key | `https://adn.producaorestrita.nfse.gov.br/contribuintes` |
| `adn-danfse.openapi.json` | ADN DANFSe — PDF | `https://adn.producaorestrita.nfse.gov.br/danfse` |
| `adn-parametrizacao.openapi.json` | ADN Parâmetros Municipais | `https://adn.producaorestrita.nfse.gov.br/parametrizacao` |

Provenance: first captured on 2026-04-16 by the [open-nfse](https://github.com/Fm-s/open-nfse) project
(MIT License, © 2026 Mergen Soluções Tecnológicas LTDA), then **verified on 2026-09-29 against restricted
production with this project's own ICP-Brasil certificate** (`scripts/fetch-swagger.sh`). The ADN documents came
back byte for byte identical; the Sefin one, refreshed here, differs only in the `dataHoraProcessamento` examples
(generated per request) and in two `summary` strings the Receita has since corrected — the events `POST` and `GET`
described the emission endpoint by mistake. **No part of the contract changed**, so every field name, path and
error shape the library assumes is confirmed first-hand.

`adn-danfse.openapi.json` could not be refreshed: `…/danfse/docs/index.html` answers 404, consistent with the
service NT 008/2026 suspended on 2026-08-03. The file is kept as the record of what it looked like.

The specs are not shipped in the jar, and they are not decoration: `ApiSpecConformanceTest` compares the client
with them on every build — every path in `NfseApiPaths`, every field bound by `ApiPayloads`, and the exact list of
spec fields the client deliberately ignores. Refreshing a document is therefore enough to make a change on the
government's side fail the build.

```
scripts/fetch-swagger.sh /path/to/certificado.pfx            # fetch and diff, no writes
scripts/fetch-swagger.sh --update /path/to/certificado.pfx   # …and refresh these files
./gradlew test                                                # what the change broke, if anything
```
