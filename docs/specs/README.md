# OpenAPI specs of the Sistema Nacional NFS-e

Swagger/OpenAPI documents of the four services the library talks to, as served by the restricted-production
environment (the Swagger UIs are only reachable with an ICP-Brasil certificate):

| File | Service | Base URL (restricted production) |
|---|---|---|
| `sefin-nacional.openapi.json` | Sefin Nacional — emission, lookup, events | `https://sefin.producaorestrita.nfse.gov.br/SefinNacional` |
| `adn-contribuinte.openapi.json` | ADN Contribuintes — distribution by NSU, events by key | `https://adn.producaorestrita.nfse.gov.br/contribuintes` |
| `adn-danfse.openapi.json` | ADN DANFSe — PDF | `https://adn.producaorestrita.nfse.gov.br/danfse` |
| `adn-parametrizacao.openapi.json` | ADN Parâmetros Municipais | `https://adn.producaorestrita.nfse.gov.br/parametrizacao` |

Provenance: captured on 2026-04-16 by the [open-nfse](https://github.com/Fm-s/open-nfse) project
(MIT License, © 2026 Mergen Soluções Tecnológicas LTDA) and copied here unchanged as reference material.
They are not shipped in the jar. Refresh them with `scripts/fetch-swagger.sh` once you have a certificate.
