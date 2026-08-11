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
        .benchmarks[]? as $benchmark |
        ($benchmark.metrics // {}) | to_entries[] |
        select(.key != "frameCount") |
        (if ((.value.runs? | type) == "array" and (.value.runs | length) > 0)
         then .value.runs
         else [(.value.median // null)]
         end)[] as $value |
        select(($value | type) == "number" and ($value | isfinite) and $value >= 0) |
        [$benchmark.name, .key, $value] | @tsv
    ' "$file" >> "$candidate"
done < <(find "$results_dir" -type f -name '*.json' -print0)

test -s "$candidate" || { echo "no AndroidX benchmark JSON results found under $results_dir" >&2; exit 1; }

candidate_medians="$tmp_dir/candidate-medians.tsv"
# ponytail: O(n^2) sorting is bounded by the small managed-device sample count.
awk -F '\t' '
  {
    key = $1 "." $2
    count[key]++
    values[key, count[key]] = $3
  }
  END {
    for (key in count) {
      n = count[key]
      for (i = 1; i <= n; i++) {
        for (j = i + 1; j <= n; j++) {
          if (values[key, i] > values[key, j]) {
            swap = values[key, i]
            values[key, i] = values[key, j]
            values[key, j] = swap
          }
        }
      }
      if (n % 2 == 1) {
        median = values[key, (n + 1) / 2]
      } else {
        median = (values[key, n / 2] + values[key, n / 2 + 1]) / 2
      }
      print key "\t" median
    }
  }
' "$candidate" > "$candidate_medians"
test -s "$candidate_medians" || { echo "benchmark candidate has no numeric metrics: $results_dir" >&2; exit 1; }

jq -r '.metrics | to_entries[] | [.key, .value] | @tsv' "$baseline_file" > "$baseline"
test -s "$baseline" || { echo "benchmark baseline has no metrics: $baseline_file" >&2; exit 1; }

awk -F '\t' '
  NR == FNR { baseline[$1] = $2; next }
  { candidate[$1] = $2 }
  END {
    failed = 0
    for (key in candidate) {
      if (!(key in baseline)) {
        printf "missing baseline metric: %s candidate=%s\n", key, candidate[key] > "/dev/stderr"
        failed = 1
      }
    }
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
    exit failed
  }
' "$baseline" "$candidate_medians"

echo "Benchmark regression check passed: $(wc -l < "$candidate_medians" | tr -d ' ') metrics within 10% of baseline."
