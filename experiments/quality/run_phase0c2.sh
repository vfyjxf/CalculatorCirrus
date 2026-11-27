#!/bin/sh
# Phase 0c-2 — train, evaluate and export the three planner models.
#
#   ./run_phase0c2.sh                # the full experiment (~7 min)
#   ./run_phase0c2.sh --quick        # smoke test: 3 pack groups, 20k pairs
#
# Reads out/features/, out/labels/ and out/<slug>/economy/ (Phase 0a/0b/0c-1,
# all read-only) and writes only out/models/.
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
if [ ! -f "$OUT/features/template_features.parquet" ]; then
    echo "no Phase 0c-1 features under $OUT — run ./run_phase0c1.sh first" >&2
    exit 1
fi

echo "== step 1/4  leaf aggregation -> $OUT/models/recipe_leaf_agg.parquet"
time "$PY" "$HERE/pair_features.py" --cache-dir "$CACHE" --out-dir "$OUT" \
    --packs all

echo
echo "== step 2/4  train + evaluate (stages A-C)"
time "$PY" "$HERE/train_eval.py" --cache-dir "$CACHE" --out-dir "$OUT" \
    --stage all "$@"

echo
echo "== step 3/4  export -> $OUT/models/*.json"
time "$PY" "$HERE/export_model.py" --cache-dir "$CACHE" --out-dir "$OUT"

echo
echo "== step 4/4  invariants"
time "$PY" "$HERE/tests/test_phase0c2.py"
