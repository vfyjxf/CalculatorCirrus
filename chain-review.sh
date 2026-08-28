#!/usr/bin/env bash
# Iterative chain-review loop: generate a batch -> review it (you + LLM) ->
# distill findings into filters.json -> generate the next batch aimed at
# UNCOVERED recipes. Repeats until coverage target or pattern convergence.
#
# Usage:
#   bash chain-review.sh init [packs]    # batch 1 for all (or listed) packs
#   bash chain-review.sh next [packs]    # next batch (reads state + filters)
#   bash chain-review.sh status          # per-pack coverage + convergence
#   bash chain-review.sh add-filter <pack> <name> <targetRegex|recipeRegex> <pattern>
#
# Env: EXPORT_DIR, OUT_DIR (default ../chain-exports), BATCH (default 150), JOBS (default 4)
set -uo pipefail
cd "$(dirname "$0")"

EXPORT_DIR="${EXPORT_DIR:-../exports}"
OUT_DIR="${OUT_DIR:-../chain-exports}"
BATCH="${BATCH:-150}"
JOBS="${JOBS:-4}"
CMD="${1:-help}"
shift || true

run_batches() {
  local packs="${1:-}"
  ./gradlew :Mod:chainExportClasspath --offline -q || ./gradlew :Mod:chainExportClasspath -q
  CP="$(cat Mod/build/chainexport/cp.txt)"
  mkdir -p "$OUT_DIR"

  # discover packs (bash-3.2 safe: hoist array lookups out of [[ ]] patterns)
  declare -a PACKS=()
  while IFS= read -r line; do
    p="$(basename "$(dirname "$line")")"
    if [[ -z "$packs" ]] || [[ ",$packs," == *",$p,"* ]]; then
      PACKS+=("$p")
    fi
  done < <(find "$EXPORT_DIR" -mindepth 2 -maxdepth 2 -name 'recipes-*.json' | sort)
  if [[ ${#PACKS[@]} -eq 0 ]]; then
    echo "no packs matched: $packs" >&2
    exit 2
  fi

  # round-robin shards so each worker gets a mix of pack sizes
  declare -a BINS=()
  for ((b = 0; b < JOBS; b++)); do BINS+=(""); done
  for i in "${!PACKS[@]}"; do
    p="${PACKS[$i]}"
    b=$(( i % JOBS ))
    BINS[b]="${BINS[b]},$p"
  done

  PIDS=()
  mkdir -p "$OUT_DIR/.logs"
  for ((b = 0; b < JOBS; b++)); do
    csv="${BINS[b]:1}"
    [[ -z "$csv" ]] && continue
    LOG="$OUT_DIR/.logs/review-w$b.log"
    echo "worker $b: $csv"
    java -Xmx8g -Xms512m -cp "$CP" \
      dev.vfyjxf.cirrus.recipe.chainexport.ChainExportMain \
      --export-dir "$EXPORT_DIR" --out "$OUT_DIR" --batch "$BATCH" \
      --packs "$csv" > "$LOG" 2>&1 &
    PIDS+=($!)
  done
  FAIL=0
  for pid in "${PIDS[@]}"; do
    wait "$pid" || FAIL=1
  done
  tail -n 3 "$OUT_DIR"/.logs/review-w*.log 2>/dev/null
  (( FAIL )) && { echo "some workers failed — see $OUT_DIR/.logs/"; exit 1; }
}

case "$CMD" in
  init|next)
    run_batches "${1:-}"
    bash "$0" status
    ;;
  status)
    printf "%-42s %6s %9s %8s %9s %11s\n" "pack" "batch" "covered" "kept" "cov%" "converge%"
    for d in "$OUT_DIR"/*/; do
      p="$(basename "$d")"
      cov="$d/coverage.tsv"
      [[ -f "$cov" ]] || continue
      # last line of the ledger carries the latest totals
      last="$(grep -v '^batch' "$cov" | tail -1)"
      [[ -z "$last" ]] && continue
      batch="$(echo "$last" | cut -f1)"
      total="$(echo "$last" | cut -f4)"
      kept="$(echo "$last" | cut -f5)"
      pct="$(echo "$last" | cut -f6)"
      conv="$(echo "$last" | cut -f8)"
      printf "%-42s %6s %9s %8s %8s%% %10s%%\n" "$p" "$batch" "$total" "$kept" "$pct" "$conv"
    done
    ;;
  add-filter)
    pack="$1"; name="$2"; type="$3"; pattern="$4"
    f="$OUT_DIR/$pack/state/filters.json"
    if [[ ! -f "$f" ]]; then
      echo "no state at $f — run 'init' first" >&2
      exit 2
    fi
    python3 - "$f" "$name" "$type" "$pattern" <<'PYEOF'
import json, sys
path, name, ftype, pattern = sys.argv[1:5]
data = json.load(open(path))
data.setdefault("filters", []).append({"name": name, "type": ftype, "pattern": pattern})
json.dump(data, open(path, "w"), indent=2, ensure_ascii=False)
print(f"filter added: {name} ({ftype}: {pattern}) -> {path}")
PYEOF
    ;;
  help|*)
    sed -n '2,12p' "$0"
    ;;
esac
