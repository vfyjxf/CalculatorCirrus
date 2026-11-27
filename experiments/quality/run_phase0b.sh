#!/bin/sh
# Phase 0b — the economy layer over the Phase 0a cache.
#
#   ./run_phase0b.sh                    # every cached pack
#   ./run_phase0b.sh --packs fom        # one pack (cross-pack stats from it alone)
#   ./run_phase0b.sh --packs fom --cross-all   # one pack, corpus-wide leaf stats
#   ./run_phase0b.sh --stage shape      # stop after a stage, for iteration
#
# Phase 0a must have run first: this reads cache/<slug>/*.parquet and
# out/<slug>/{identity,recipe_template}.parquet and writes
# out/<slug>/economy/ plus out/economy/.
#
# Never invoke bare `python3`: the system interpreter is 3.9 and has neither
# pyarrow nor ijson.  Everything runs through the pinned venv.
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

# Default to every pack; any argument list replaces the default entirely.
if [ $# -eq 0 ]; then
    set -- --packs all
fi

echo "== step 1/2  economy -> $OUT/<slug>/economy"
time "$PY" "$HERE/economy.py" --cache-dir "$CACHE" --out-dir "$OUT" "$@"

echo
echo "== step 2/2  invariants"
time "$PY" "$HERE/tests/test_phase0b.py"
