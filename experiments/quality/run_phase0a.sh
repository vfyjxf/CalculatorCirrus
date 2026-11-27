#!/bin/sh
# Phase 0a — export loader + ontology induction over every pack.
#
#   ./run_phase0a.sh              # incremental (cached packs are skipped)
#   ./run_phase0a.sh --force      # re-read every export from scratch
#
# Never invoke bare `python3`: the system interpreter is 3.9 and has neither
# pyarrow nor ijson.  Everything runs through the pinned venv.
set -e

HERE=$(cd "$(dirname "$0")" && pwd)
PY="$HERE/.venv/bin/python"
EXPORTS="${EXPORTS_ROOT:-/Users/vfyjxf/ProjectDir/Planner-Final/exports}"
CACHE="${CACHE_DIR:-$HERE/cache}"
OUT="${OUT_DIR:-$HERE/out}"

if [ ! -x "$PY" ]; then
    echo "missing venv interpreter at $PY" >&2
    exit 1
fi

echo "== step 1/3  load exports -> $CACHE"
time "$PY" "$HERE/load_exports.py" \
    --exports-root "$EXPORTS" --cache-dir "$CACHE" --packs all "$@"

echo
echo "== step 2/3  induce ontology -> $OUT"
time "$PY" "$HERE/ontology.py" \
    --cache-dir "$CACHE" --out-dir "$OUT" --packs all --onboarding full

echo
echo "== step 3/3  invariants"
time "$PY" "$HERE/tests/test_phase0a.py"
