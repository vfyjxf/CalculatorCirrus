#!/usr/bin/env bash
# Usage: perf/run-bench.sh <include-regex> <label> [extra jmh args]
# Runs ./gradlew :Mod:jmhRun for the benchmarks matching <include-regex> and writes
# perf/results/<label>.json, then prints a one-line score summary per benchmark.
# Extra args (e.g. -f 1 -wi 0 -i 1 -r 1s -w 1s for a smoke run) are forwarded to
# -PcirrusJmhExtra; without them the annotation defaults apply.
# Set CIRRUS_OFFLINE=0 to drop --offline from the Gradle invocation.
set -euo pipefail

if [ $# -lt 2 ]; then
    echo "usage: $0 <include-regex> <label> [extra jmh args]" >&2
    exit 1
fi

INCLUDE="$1"
LABEL="$2"
shift 2

cd "$(dirname "$0")/.."
mkdir -p perf/results
RESULT="perf/results/${LABEL}.json"

GRADLE_ARGS=(":Mod:jmhRun" "-PcirrusJmhInclude=${INCLUDE}" "-PcirrusJmhResult=${RESULT}")
if [ $# -gt 0 ]; then
    GRADLE_ARGS+=("-PcirrusJmhExtra=$*")
fi
if [ "${CIRRUS_OFFLINE:-1}" != "0" ]; then
    GRADLE_ARGS+=("--offline")
fi
# the champeau jmh plugin's jmhJar is not configuration-cache compatible; the repo
# enables the cache globally, so disable it just for these runs
GRADLE_ARGS+=("--no-configuration-cache")

./gradlew "${GRADLE_ARGS[@]}"

python3 - "$RESULT" <<'PY'
import json
import math
import sys

with open(sys.argv[1]) as handle:
    entries = json.load(handle)


def as_float(value):
    # JMH writes NaN as the string "NaN" when an iteration count leaves no error estimate
    if isinstance(value, (int, float)):
        return float(value)
    return float("nan")


print()
print(f"{'benchmark':<70} {'params':<40} {'score':>12} {'error':>12}  unit")
for entry in entries:
    name = entry["benchmark"]
    params = entry.get("params") or {}
    param_text = ",".join(f"{key}={value}" for key, value in sorted(params.items()))
    metric = entry["primaryMetric"]
    score = as_float(metric["score"])
    error = as_float(metric.get("scoreError"))
    error_text = "NaN" if math.isnan(error) else f"{error:,.4f}"
    print(f"{name:<70} {param_text:<40} {score:>12,.4f} {error_text:>12}  {metric['scoreUnit']}")
PY
