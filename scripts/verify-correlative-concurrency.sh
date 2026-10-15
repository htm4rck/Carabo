#!/usr/bin/env bash
set -euo pipefail

iterations="${1:-40}"
parallelism="${2:-12}"
sql_file="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/allocate-correlative.sql"

results="$(
  seq 1 "$iterations" |
    xargs -P "$parallelism" -I{} psql -X -Atq -v ON_ERROR_STOP=1 -f "$sql_file"
)"

printf '%s\n' "$results" |
  sort -n |
  awk -v expected_count="$iterations" '
    BEGIN { ok=1; expected=1 }
    { if ($1 != expected) ok=0; expected++ }
    END {
      printf "allocated=%d min=1 max=%d contiguous=%s\n", NR, expected-1, ok ? "yes" : "no"
      if (NR != expected_count || !ok) exit 1
    }
  '
