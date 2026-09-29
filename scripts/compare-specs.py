#!/usr/bin/env python3
"""Compares freshly fetched OpenAPI documents with the ones committed under docs/specs/.

Used by fetch-swagger.sh, and usable on its own:

    scripts/compare-specs.py <fetched-dir> [docs/specs] [update]

`example` values are dropped before comparing: the services regenerate them on every request (they carry the
current timestamp), so they are noise and never part of the contract.
"""

import json
import shutil
import sys
from pathlib import Path

# fetched name -> name under docs/specs/
PAIRS = {
    "sefin": "sefin-nacional",
    "adn": "adn-contribuinte",
    "parametrizacao": "adn-parametrizacao",
    "danfse": "adn-danfse",
}


def strip_examples(node):
    if isinstance(node, dict):
        return {k: strip_examples(v) for k, v in node.items() if k != "example"}
    if isinstance(node, list):
        return [strip_examples(v) for v in node]
    return node


def paths(doc):
    return set(doc.get("paths", {}))


def schemas(doc):
    return doc.get("components", {}).get("schemas", {}) or doc.get("definitions", {}) or {}


def properties(schema):
    return set(schema.get("properties") or {})


def report(old, new):
    """Prints what moved; returns True when only prose changed."""
    structural = False
    for path in sorted(paths(new) - paths(old)):
        print(f"      + path {path}")
        structural = True
    for path in sorted(paths(old) - paths(new)):
        print(f"      - path {path}")
        structural = True
    new_schemas, old_schemas = schemas(new), schemas(old)
    for name in sorted(set(new_schemas) - set(old_schemas)):
        print(f"      + schema {name}")
        structural = True
    for name in sorted(set(old_schemas) - set(new_schemas)):
        print(f"      - schema {name}")
        structural = True
    for name in sorted(set(new_schemas) & set(old_schemas)):
        added = properties(new_schemas[name]) - properties(old_schemas[name])
        removed = properties(old_schemas[name]) - properties(new_schemas[name])
        if added:
            print(f"      ~ {name}: + {', '.join(sorted(added))}")
            structural = True
        if removed:
            print(f"      ~ {name}: - {', '.join(sorted(removed))}")
            structural = True
    return not structural


def main() -> int:
    fetched = Path(sys.argv[1])
    specs = Path(sys.argv[2] if len(sys.argv) > 2 else "docs/specs")
    update = len(sys.argv) > 3 and sys.argv[3] == "true"

    changed = missing = 0
    for name, stored in PAIRS.items():
        new_file, old_file = fetched / f"{name}.openapi.json", specs / f"{stored}.openapi.json"
        if not new_file.exists():
            print(f"   {stored}: not fetched (see above)")
            missing += 1
            continue
        new = strip_examples(json.loads(new_file.read_text()))
        old = strip_examples(json.loads(old_file.read_text())) if old_file.exists() else {}
        if new == old:
            print(f"   {stored}: unchanged")
            continue
        changed += 1
        print(f"   {stored}: CHANGED")
        if report(old, new):
            print("      (only prose: descriptions or summaries)")
        if update:
            shutil.copyfile(new_file, old_file)
            print(f"      updated {old_file}")

    print()
    if changed:
        print(f"{changed} document(s) changed.", "" if update else "Re-run with --update to refresh docs/specs/.")
        print("Then run ./gradlew test — ApiSpecConformanceTest says whether the client still matches.")
    else:
        print("No contract change.", f"{missing} document(s) could not be fetched." if missing else "")
    return 0


if __name__ == "__main__":
    sys.exit(main())
