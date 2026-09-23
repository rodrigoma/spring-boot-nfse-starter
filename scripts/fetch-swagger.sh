#!/usr/bin/env bash
# Downloads the OpenAPI specs of the Sistema Nacional NFS-e (restricted production) with the emitter's A1
# certificate and prints the pieces the library assumes (see README → "Notes on the official docs").
#
#   scripts/fetch-swagger.sh /path/to/certificado.pfx [output-dir]
#
# The password is asked interactively and never written to disk or to the shell history.
set -euo pipefail

PFX="${1:?usage: $0 /path/to/certificado.pfx [output-dir]}"
OUT="${2:-docs/swagger}"
mkdir -p "$OUT"
read -r -s -p "Password of $PFX: " PFX_PASS; echo

fetch() { curl -sS --fail --cert-type P12 --cert "$PFX:$PFX_PASS" "$@"; }

# The four services under docs/specs/. The DANFSe endpoint is suspended by NT 008/2026 (the library renders
# the PDF locally), but its Swagger still documents the path — keep fetching it to notice when it returns.
declare -A DOCS=(
  [sefin]="https://sefin.producaorestrita.nfse.gov.br/API/SefinNacional/docs/index"
  [adn]="https://adn.producaorestrita.nfse.gov.br/contribuintes/docs/index.html"
  [danfse]="https://adn.producaorestrita.nfse.gov.br/danfse/docs/index.html"
  [parametrizacao]="https://adn.producaorestrita.nfse.gov.br/parametrizacao/docs/index.html"
)

for name in sefin adn danfse parametrizacao; do
  url="${DOCS[$name]}"
  base="${url%/docs/*}"
  echo "== $name: $url"
  html="$OUT/$name-docs.html"
  if ! fetch "$url" -o "$html"; then echo "   (failed — is the certificate accepted?)"; continue; fi
  # Swagger UI pages reference their spec as .../swagger/<version>/swagger.json (or similar)
  spec_paths=$(grep -oE '"[^"]*swagger[^"]*\.json"|url: *"[^"]+"' "$html" | grep -oE '[^"]*\.json' | sort -u || true)
  if [ -z "$spec_paths" ]; then echo "   no spec link found in the HTML; open it in a browser: $url"; continue; fi
  for path in $spec_paths; do
    case "$path" in http*) spec_url="$path" ;; /*) spec_url="$(echo "$url" | grep -oE 'https://[^/]+')$path" ;; *) spec_url="$base/$path" ;; esac
    file="$OUT/$name-$(basename "$path")"
    echo "   spec: $spec_url -> $file"
    fetch "$spec_url" -o "$file" || echo "   (failed to download $spec_url)"
  done
done

echo
echo "== Things the library assumes — compare with the specs above:"
for f in "$OUT"/*.json; do
  [ -f "$f" ] || continue
  echo "-- $f"
  python3 - "$f" <<'PY'
import json, sys
spec = json.load(open(sys.argv[1]))
print("   paths:", ", ".join(sorted(spec.get("paths", {}))))
schemas = spec.get("components", {}).get("schemas", {}) or spec.get("definitions", {})
for name, schema in schemas.items():
    props = list((schema.get("properties") or {}).keys())
    if props:
        print(f"   {name}: {', '.join(props)}")
PY
done
echo
echo "Send the $OUT/*.json files (they contain no secrets) so the field names can be confirmed."
