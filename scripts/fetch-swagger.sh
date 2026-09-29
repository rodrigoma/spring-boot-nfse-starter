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

# The four services under docs/specs/, as "name|docs URL". A plain list and not an associative array on purpose:
# macOS still ships bash 3.2, where `declare -A` silently becomes an indexed array and every key is evaluated as
# an arithmetic index ("sefin: unbound variable" under `set -u`).
# The DANFSe endpoint is suspended by NT 008/2026 (the library renders the PDF locally), but its Swagger still
# documents the path — keep fetching it to notice when the service returns.
DOCS="
sefin|https://sefin.producaorestrita.nfse.gov.br/API/SefinNacional/docs/index
adn|https://adn.producaorestrita.nfse.gov.br/contribuintes/docs/index.html
danfse|https://adn.producaorestrita.nfse.gov.br/danfse/docs/index.html
parametrizacao|https://adn.producaorestrita.nfse.gov.br/parametrizacao/docs/index.html
"

# Redoc keeps the document at `openApi: '<url>'` or `spec-url="<url>"`; a Swagger UI page links a *.json instead.
# Note there is no extension to rely on: the Sefin serves its spec from `/swagger/docs/v1`.
spec_urls() {
  {
    grep -oE "(openApi|spec-url|specUrl)[\"']?[[:space:]]*[:=][[:space:]]*[\"'][^\"']+" "$1" | sed -E "s/.*[\"']//"
    grep -oE "[\"'][^\"']*swagger[^\"']*\.json" "$1" | tr -d "\"'"
  } 2>/dev/null | sort -u || true   # `pipefail` would otherwise make "no match" abort the script
}

for entry in $DOCS; do
  name="${entry%%|*}"
  url="${entry#*|}"
  origin="$(echo "$url" | grep -oE 'https://[^/]+')"
  dir="${url%/*}"
  echo "== $name: $url"
  html="$OUT/$name-docs.html"
  if ! fetch "$url" -o "$html"; then echo "   (failed — is the certificate accepted?)"; continue; fi

  specs="$(spec_urls "$html")"
  if [ -z "$specs" ]; then
    # The ADN pages build themselves in index.js, next to the page, and keep the spec URL there.
    js="$OUT/$name-index.js"
    if fetch "$dir/index.js" -o "$js" 2>/dev/null; then specs="$(spec_urls "$js")"; fi
  fi
  if [ -z "$specs" ]; then echo "   no spec link found; open it in a browser: $url"; continue; fi

  i=0
  for path in $specs; do
    case "$path" in
      http*) spec_url="$path" ;;
      /*) spec_url="$origin$path" ;;
      *) spec_url="$dir/$path" ;;
    esac
    i=$((i + 1))
    if [ "$i" -eq 1 ]; then file="$OUT/$name.openapi.json"; else file="$OUT/$name-$i.openapi.json"; fi
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
