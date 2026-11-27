#!/bin/sh
# Phase 0c-1 — the label layer and the feature layer over Phase 0a/0b.
#
#   ./run_phase0c1.sh                     # every cached pack
#   ./run_phase0c1.sh --packs fom         # one pack
#   ./run_phase0c1.sh --packs fom,atm10-lite
#
# Reads cache/<slug>/*.parquet, out/<slug>/*.parquet and
# out/<slug>/economy/*.parquet plus the four external label sources
# (rule replay, gold labels, chain audits, chain exports) and writes
# out/labels/ and out/features/.  Phase 0a/0b outputs are never touched.
#
# Never invoke bare `python3`: the system interpreter is 3.9 and has neither
# pyarrow nor numpy.  Everything runs through the pinned venv.
set -e

HERE=$(cd "$(dirname "$0")" && pwd)
PY="$HERE/.venv/bin/python"
CACHE="${CACHE_DIR:-$HERE/cache}"
OUT="${OUT_DIR:-$HERE/out}"

if [ ! -x "$PY" ]; then
    echo "missing venv interpreter at $PY" >&2
    exit 1
fi
if [ ! -f "$CACHE/packs.json" ]; then
    echo "no Phase 0a cache at $CACHE — run ./run_phase0a.sh first" >&2
    exit 1
fi
if [ ! -d "$OUT/fom/economy" ] && [ ! -d "$OUT/economy" ]; then
    echo "no Phase 0b economy tables under $OUT — run ./run_phase0b.sh first" >&2
    exit 1
fi

# Default to every pack; any argument list replaces the default entirely.
if [ $# -eq 0 ]; then
    set -- --packs all
fi

echo "== step 1/4  labels   -> $OUT/labels"
time "$PY" "$HERE/labels.py" --cache-dir "$CACHE" --out-dir "$OUT" "$@"

echo
echo "== step 2/4  features -> $OUT/features"
time "$PY" "$HERE/features.py" --cache-dir "$CACHE" --out-dir "$OUT" "$@"

echo
echo "== step 3/4  the LLM labeller stays a stub (dry run, zero budget)"
"$PY" "$HERE/llm_labels.py" --out-dir "$OUT" --limit 0 --budget-usd 0 >/dev/null \
    && echo "   ok  llm_labels.py sent nothing"

echo
echo "== step 4/4  invariants"
time "$PY" "$HERE/tests/test_phase0c1.py" --cache-dir "$CACHE" --out-dir "$OUT"
