#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 2 ]]; then
  echo "usage: $0 <first.apk-or-aab> <second.apk-or-aab>" >&2
  exit 2
fi

first=$1
second=$2
[[ -f "$first" && -f "$second" ]] || {
  echo "both Android archives must exist" >&2
  exit 2
}

work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

manifest() {
  local archive=$1
  local output=$2
  local unpacked=$3
  mkdir -p "$unpacked"
  unzip -qq "$archive" -d "$unpacked"
  (
    cd "$unpacked"
    # R8 emits tool metadata with build-specific content; it is not app payload.
    find . -type f ! -path './META-INF/*.SF' ! -path './META-INF/*.RSA' ! -path './META-INF/*.DSA' ! -path './META-INF/MANIFEST.MF' ! -path './BUNDLE-METADATA/com.android.tools/r8.json' -print0 \
      | LC_ALL=C sort -z \
      | xargs -0 shasum -a 256
  ) > "$output"
}

manifest "$first" "$work/first.sha256" "$work/first"
manifest "$second" "$work/second.sha256" "$work/second"
diff -u "$work/first.sha256" "$work/second.sha256"
echo "matching unsigned payloads: $(basename "$first") and $(basename "$second")"
