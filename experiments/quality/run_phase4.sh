#!/bin/sh
# Phase 4 — the minimum-annotation feedback layer.
#
#   ./run_phase4.sh                      # every stage, every pack it needs
#   ./run_phase4.sh --packs fom          # one pack (family + latency only)
#
# Reads cache/, out/<slug>/, out/features/, out/labels/, out/models/ and
# ../../../chain-exports/ (Phases 0a-0c-2 and Phase 3, all read-only) and
# writes only out/<slug>/feedback/ and out/planner-eval/feedback-*.json.
#
# Never invoke bare `python3`: the system interpreter is 3.9 and has neither
# pyarrow nor numpy.  Everything runs through the pinned venv.
set -e

HERE=$(cd "$(dirname "$0")" && pwd)
PY="$HERE/.venv/bin/python"
CACHE="${CACHE_DIR:-$HERE/cache}"
OUT="${OUT_DIR:-$HERE/out}"
EVAL="$OUT/planner-eval"

# the packs each stage needs: every pack carrying an audit reading, plus the
# label-heavy ones the inquiry stage ranks over, plus the two deep ones
AUDIT_PACKS="farmers-colonies,tfc-enhanced,technical-electrical,lost-world,mythcrafts,atm10-lite"
DEEP_PACKS="atm10,minecolonies-dimensional-adventure,fom"
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
mkdir -p "$EVAL"

if [ -n "$PACKS" ]; then
    AUDIT_PACKS="$PACKS"; DEEP_PACKS="$PACKS"
fi

echo "== step 1/6  family generalisation (one ban, one family)"
time "$PY" "$HERE/feedback.py" --cache-dir "$CACHE" --out-dir "$OUT" \
    --stage family --packs "fom,$AUDIT_PACKS" --families 3 --targets 150 \
    --report "$EVAL/feedback-family.json" $EXTRA

echo
echo "== step 2/6  the audit cases under one click each"
time "$PY" "$HERE/feedback.py" --cache-dir "$CACHE" --out-dir "$OUT" \
    --stage audit --packs "$AUDIT_PACKS" \
    --report "$EVAL/feedback-audit.json" $EXTRA

echo
echo "== step 3/6  the minimum-annotation learning curve (5 orders)"
time "$PY" "$HERE/feedback.py" --cache-dir "$CACHE" --out-dir "$OUT" \
    --stage curve --packs "$AUDIT_PACKS" --seeds 5 --curve-targets 60 \
    --report "$EVAL/feedback-curve.json" $EXTRA

echo
echo "== step 4/6  inquiry precision on the label-heaviest packs"
time "$PY" "$HERE/feedback.py" --cache-dir "$CACHE" --out-dir "$OUT" \
    --stage inquiry --packs all --packs-top 5 --targets 150 \
    --report "$EVAL/feedback-inquiry.json" $EXTRA

echo
echo "== step 5/6  per-call latency"
time "$PY" "$HERE/feedback.py" --cache-dir "$CACHE" --out-dir "$OUT" \
    --stage latency --packs "$DEEP_PACKS" --calls 200 \
    --report "$EVAL/feedback-latency.json" $EXTRA

echo
echo "== step 6/6  persistence round trip + invariants"
time "$PY" "$HERE/feedback.py" --cache-dir "$CACHE" --out-dir "$OUT" \
    --stage persist --packs "fom,$AUDIT_PACKS" \
    --report "$EVAL/feedback-persist.json" $EXTRA
time "$PY" "$HERE/tests/test_phase4.py"
