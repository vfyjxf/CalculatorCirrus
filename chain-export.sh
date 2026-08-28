#!/usr/bin/env bash
# Parallel offline recipe-chain export: shards packs across bare JVM workers
# (no gradle overhead, no game launch). Big packs get a dedicated worker and a
# bigger heap.
#
# Usage:
#   bash chain-export.sh                 # all packs, default parallelism
#   bash chain-export.sh -j 6            # 6 workers
#   bash chain-export.sh fom,create-train  # only these packs
#   bash chain-export.sh --overwrite       # regenerate even if manifest exists
#
# Env overrides: EXPORT_DIR, OUT_DIR, PER_PACK, BUDGET_MS
set -uo pipefail
cd "$(dirname "$0")"

EXPORT_DIR="${EXPORT_DIR:-../exports}"
OUT_DIR="${OUT_DIR:-../chain-exports}"
JOBS=4
PACK_FILTER=""
EXTRA_ARGS=()

while [[ $# -gt 0 ]]; do
  case "$1" in
    -j) JOBS="$2"; shift 2 ;;
    --) shift; EXTRA_ARGS+=("$@"); break ;;
    -*) EXTRA_ARGS+=("$1"); shift ;;
    *) PACK_FILTER="$1"; shift ;;
  esac
done

if [[ ! -d "$EXPORT_DIR" ]]; then
  echo "export dir not found: $EXPORT_DIR" >&2
  exit 2
fi

# 1. classpath (gradle only once per checkout; --offline keeps it self-contained)
./gradlew :Mod:chainExportClasspath --offline -q || ./gradlew :Mod:chainExportClasspath -q
CP="$(cat Mod/build/chainexport/cp.txt)"

# 2. discover packs with recipes-file sizes as weights
declare -a PACKS=() WEIGHTS=()
while IFS= read -r line; do
  PACKS+=("$(basename "$(dirname "$line")")")
  WEIGHTS+=("$(stat -f%z "$line" 2>/dev/null || stat -c%s "$line")")
done < <(find "$EXPORT_DIR" -mindepth 2 -maxdepth 2 -name 'recipes-*.json' | sort)

if [[ ${#PACKS[@]} -eq 0 ]]; then
  echo "no packs with recipes-*.json under $EXPORT_DIR" >&2
  exit 2
fi

# apply filter (bash 3.2 quirk: array lookups inside [[ ]] patterns misparse,
# so hoist every element into a plain variable first)
declare -a SELECTED=() SEL_WEIGHTS=()
for i in "${!PACKS[@]}"; do
  pack="${PACKS[$i]}"
  weight="${WEIGHTS[$i]}"
  if [[ -z "$PACK_FILTER" ]]; then
    SELECTED+=("$pack"); SEL_WEIGHTS+=("$weight")
  elif [[ ",$PACK_FILTER," == *",$pack,"* ]]; then
    SELECTED+=("$pack"); SEL_WEIGHTS+=("$weight")
  fi
done
if [[ ${#SELECTED[@]} -eq 0 ]]; then
  echo "no packs matched filter: $PACK_FILTER" >&2
  exit 2
fi
if [[ "$JOBS" -gt ${#SELECTED[@]} ]]; then JOBS=${#SELECTED[@]}; fi

echo "chain-export: ${#SELECTED[@]} pack(s), $JOBS worker(s), out=$OUT_DIR"

# 3. shard: biggest-first into N bins (largest pack always lands alone in the
#    first-bin-emptiest slot, which naturally isolates monsters like atm10)
declare -a BINS=()
for ((b = 0; b < JOBS; b++)); do BINS+=(""); done
declare -a BIN_LOAD=()
for ((b = 0; b < JOBS; b++)); do BIN_LOAD+=(0); done
declare -a BIN_MAX=()
for ((b = 0; b < JOBS; b++)); do BIN_MAX+=(0); done

ORDER=$(for i in "${!SELECTED[@]}"; do echo "${SEL_WEIGHTS[$i]} $i"; done | sort -rn)
while read -r _weight idx; do
  lightest=0
  for ((b = 1; b < JOBS; b++)); do
    if (( BIN_LOAD[b] < BIN_LOAD[lightest] )); then lightest=$b; fi
  done
  BINS[lightest]="${BINS[lightest]},${SELECTED[$idx]}"
  BIN_LOAD[lightest]=$(( BIN_LOAD[lightest] + SEL_WEIGHTS[idx] ))
  (( SEL_WEIGHTS[idx] > BIN_MAX[lightest] )) && BIN_MAX[lightest]=${SEL_WEIGHTS[idx]}
done <<< "$ORDER"

# 4. spawn workers (skip finished packs unless --overwrite)
PIDS=()
LOG_DIR="$OUT_DIR/.logs"
mkdir -p "$LOG_DIR" "$OUT_DIR"
for ((b = 0; b < JOBS; b++)); do
  PACKS_CSV="${BINS[b]:1}"
  [[ -z "$PACKS_CSV" ]] && continue
  # heap from the biggest file in the shard: 3g floor, 1g per 100MB of dump, 10g cap
  HEAP=$(( BIN_MAX[b] / 100000000 + 3 ))
  (( HEAP > 10 )) && HEAP=10
  LOG="$LOG_DIR/worker-$b.log"
  echo "worker $b (heap ${HEAP}g): $PACKS_CSV -> $LOG"
  java -Xmx"${HEAP}g" -Xms512m -cp "$CP" \
    dev.vfyjxf.cirrus.recipe.chainexport.ChainExportMain \
    --export-dir "$EXPORT_DIR" --out "$OUT_DIR" --packs "$PACKS_CSV" \
    ${PER_PACK:+--per-pack "$PER_PACK"} ${BUDGET_MS:+--budget-ms "$BUDGET_MS"} \
    ${EXTRA_ARGS[@]+"${EXTRA_ARGS[@]}"} > "$LOG" 2>&1 &
  PIDS+=($!)
done

FAIL=0
for pid in "${PIDS[@]}"; do
  wait "$pid" || FAIL=1
done

tail -n 2 "$LOG_DIR"/worker-*.log 2>/dev/null
if (( FAIL )); then
  echo "chain-export: some workers FAILED — see $LOG_DIR/worker-*.log" >&2
  exit 1
fi
echo "chain-export: all workers finished"
