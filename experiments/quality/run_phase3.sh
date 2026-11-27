#!/bin/sh
# Phase 3 — the hierarchical planner prototype and its offline A/B.
#
#   ./run_phase3.sh                 # build every pack, A/B, audit, invariants
#   ./run_phase3.sh --packs fom     # one pack
#   ./run_phase3.sh --targets 40    # a smaller sample per pack
#
# Reads cache/, out/<slug>/, out/features/, out/models/ and
# ../../../chain-exports/ (Phases 0a-0c-2, all read-only) and writes only
# out/<slug>/planner/ and out/planner-eval/.
#
# Never invoke bare `python3`: the system interpreter is 3.9 and has neither
# pyarrow nor numpy.  Everything runs through the pinned venv.
set -e

HERE=$(cd "$(dirname "$0")" && pwd)
PY="$HERE/.venv/bin/python"
CACHE="${CACHE_DIR:-$HERE/cache}"
OUT="${OUT_DIR:-$HERE/out}"
PACKS="all"
TARGETS="150"
EXTRA=""

while [ $# -gt 0 ]; do
    case "$1" in
        --packs) PACKS="$2"; shift 2 ;;
        --targets) TARGETS="$2"; shift 2 ;;
        *) EXTRA="$EXTRA $1"; shift ;;
    esac
done

if [ ! -x "$PY" ]; then
    echo "missing venv interpreter at $PY" >&2
    exit 1
fi
if [ ! -f "$OUT/models/producer-ranker.json" ]; then
    echo "no Phase 0c-2 models under $OUT — run ./run_phase0c2.sh first" >&2
    exit 1
fi

mkdir -p "$OUT/planner-eval"

echo "== step 1/4  pack index + cached model scores"
time "$PY" "$HERE/planner.py" --cache-dir "$CACHE" --out-dir "$OUT" \
    --stage index --packs "$PACKS" $EXTRA

echo
echo "== step 2/4  offline A/B against the Java StandardPlanner chains"
time "$PY" "$HERE/eval_planner.py" --cache-dir "$CACHE" --out-dir "$OUT" \
    --stage ab --packs "$PACKS" --targets "$TARGETS" \
    --report "$OUT/planner-eval/ab.json" $EXTRA

echo
echo "== step 3/4  audit cases (gold pairs; evaluation only, never tuned on)"
time "$PY" "$HERE/eval_planner.py" --cache-dir "$CACHE" --out-dir "$OUT" \
    --stage audit --packs "$PACKS" \
    --report "$OUT/planner-eval/audit.json" $EXTRA

echo
echo "== step 4/4  invariants"
time "$PY" "$HERE/tests/test_phase3.py"
