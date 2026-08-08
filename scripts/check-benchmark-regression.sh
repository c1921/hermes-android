#!/usr/bin/env bash
set -euo pipefail

results_dir=${1:?usage: $0 RESULTS_DIR BASELINE_JSON}
baseline_file=${2:?usage: $0 RESULTS_DIR BASELINE_JSON}

command -v jq >/dev/null || { echo "jq is required" >&2; exit 2; }
test -d "$results_dir" || { echo "benchmark results directory not found: $results_dir" >&2; exit 2; }
test -f "$baseline_file" || { echo "benchmark baseline not found: $baseline_file" >&2; exit 2; }

tmp_dir=$(mktemp -d)
trap 'rm -rf "$tmp_dir"' EXIT

candidate="$tmp_dir/candidate.tsv"
baseline="$tmp_dir/baseline.tsv"

while IFS= read -r -d '' file; do
    jq -e '(.benchmarks? | type) == "array"' "$file" >/dev/null 2>&1 || continue
    jq -r '
        def median:
          sort as $values |
          if ($values | length) == 0 then null
          elif ($values | length) % 2 == 1 then $values[(($values | length) / 2) | floor]
          else (($values[(($values | length) / 2) - 1] + $values[(($values | length) / 2)]) / 2)
          end;
        .benchmarks[]? as $benchmark |
        ($benchmark.metrics // {}) | to_entries[] |
        select(.key != "frameCount") |
        (.value.median // ((.value.runs // []) | map(select(type == "number")) | median)) as $value |
        select(($value | type) == "number" and ($value | isfinite) and $value >= 0) |
        [$benchmark.name, .key, $value] | @tsv
    ' "$file" >> "$candidate"
done < <(find "$results_dir" -type f -name '*.json' -print0)

test -s "$candidate" || { echo "no AndroidX benchmark JSON results found under $results_dir" >&2; exit 1; }

jq -r '.metrics | to_entries[] | [.key, .value] | @tsv' "$baseline_file" > "$baseline"
test -s "$baseline" || { echo "benchmark baseline has no metrics: $baseline_file" >&2; exit 1; }

awk -F '\t' '
  NR == FNR { baseline[$1] = $2; next }
  { candidate[$1 "." $2] = $3 }
  END {
    failed = 0
    for (key in baseline) {
      if (!(key in candidate)) {
        printf "missing candidate metric: %s\n", key > "/dev/stderr"
        failed = 1
        continue
      }
      limit = baseline[key] * 1.10
      if (candidate[key] > limit) {
        printf "benchmark regression: %s baseline=%s candidate=%s limit=%s\n", key, baseline[key], candidate[key], limit > "/dev/stderr"
        failed = 1
      }
    }
    for (key in candidate) if (!(key in baseline)) {
      printf "missing baseline metric: %s\n", key > "/dev/stderr"
      failed = 1
    }
    exit failed
  }
' "$baseline" "$candidate"

echo "Benchmark regression check passed: $(wc -l < "$candidate" | tr -d ' ') metrics within 10% of baseline."
