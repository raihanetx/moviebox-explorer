#!/usr/bin/env bash
# Enforces the Kotlin file-size policy for this repository.
#
#   Target:  <= 400 lines per source file
#   Hard cap: 500 lines without a written justification at the top of the file
#
# Usage: scripts/check-file-size.sh        (fails the build over the hard cap)
#        scripts/check-file-size.sh --list (print every file over the target)
set -euo pipefail

cd "$(dirname "$0")/.."

SOFT=400
HARD=500

count=0
violations=0

while IFS= read -r -d '' file; do
  lines=$(wc -l < "$file")
  count=$((count + 1))
  if [ "$lines" -gt "$HARD" ]; then
    echo "FAIL  ${lines} lines  ${file}" >&2
    violations=$((violations + 1))
  elif [ "${1:-}" = "--list" ] && [ "$lines" -gt "$SOFT" ]; then
    echo "warn  ${lines} lines  ${file}"
  fi
done < <(find app/src core/src smoketest/src -name '*.kt' -print0 | sort -z)

echo "Checked ${count} Kotlin files (target ${SOFT}, hard cap ${HARD})."

if [ "$violations" -gt 0 ]; then
  echo "${violations} file(s) exceed the hard cap. Split by responsibility — never by arbitrary line count." >&2
  exit 1
fi
