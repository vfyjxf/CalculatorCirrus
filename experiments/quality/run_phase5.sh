#!/bin/sh
# Phase 5 — the deep-cone benchmark and the caching study.
#
#   ./run_phase5.sh                  # every stage on the packs each one needs
#   ./run_phase5.sh --packs fom      # one pack (fast, for a smoke run)
#
# Reads cache/, out/<slug>/, out/features/, out/models/ and
# ../../../chain-exports/ (all read-only) and writes only out/planner-bench/.
# Nothing here changes a default plan: every option goes through
# planner.PlanOptions, whose default is the Phase 3 code path byte for byte.
#
# Never invoke bare `python3`: the system interpreter is 3.9 and has neither
# pyarrow nor numpy.  Everything runs through the pinned venv.
set -e

HERE=$(cd "$(dirname "$0")" && pwd)
PY="$HERE/.venv/bin/python"
CACHE="${CACHE_DIR:-$HERE/cache}"
OUT="${OUT_DIR:-$HERE/out}"
BENCH="$OUT/planner-bench"

DEEP_PACKS="fom,minecolonies-dimensional-adventure,atm10"
MID_PACKS="fom,lost-world,atm10-lite,minecolonies-dimensional-adventure,atm10"
BUDGET_PACKS="atm10-lite,atm10"
PACKS=""
EXTRA=""
while [ $# -gt 0 ]; do
    case "$1" in
        --packs) PACKS="$2"; shift 2 ;;
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
mkdir -p "$BENCH"

if [ -n "$PACKS" ]; then
    DEEP_PACKS="$PACKS"; MID_PACKS="$PACKS"; BUDGET_PACKS="$PACKS"
fi

echo "== step 1/6  deep-cone benchmark (50 deepest + 150 random per pack)"
time "$PY" "$HERE/bench_planner.py" --cache-dir "$CACHE" --out-dir "$OUT" \
    --stage cone --packs "$DEEP_PACKS" --deep 50 --random 150 \
    --profile atm10 --profile-targets 10 \
    --report "$BENCH/cone.json" $EXTRA

echo
echo "== step 2/6  build-time breakdown, cold vs warm, and the cache inventory"
time "$PY" "$HERE/bench_planner.py" --cache-dir "$CACHE" --out-dir "$OUT" \
    --stage build --packs "fom,atm10-lite,minecolonies-dimensional-adventure,atm10" \
    --report "$BENCH/build.json" $EXTRA

echo
echo "== step 3/6  cross-request cost-solve memo"
time "$PY" "$HERE/bench_planner.py" --cache-dir "$CACHE" --out-dir "$OUT" \
    --stage memo --packs "$MID_PACKS" --random 150 \
    --report "$BENCH/memo.json" $EXTRA

echo
echo "== step 4/6  fold-based pruning"
time "$PY" "$HERE/bench_planner.py" --cache-dir "$CACHE" --out-dir "$OUT" \
    --stage fold --packs "$MID_PACKS" --deep 50 --random 150 \
    --report "$BENCH/fold.json" $EXTRA

echo
echo "== step 5/6  budget shapes (three shapes x three bases)"
time "$PY" "$HERE/bench_planner.py" --cache-dir "$CACHE" --out-dir "$OUT" \
    --stage budget --packs "$BUDGET_PACKS" --deep 50 --random 150 \
    --report "$BENCH/budget.json" $EXTRA

echo
echo "== step 6/6  invariants"
time "$PY" "$HERE/tests/test_phase5.py"
