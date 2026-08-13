#!/usr/bin/env bash
# Persist background-run timing and exit code even when the child exits non-zero.
# Usage: pr4-background-run-capture.sh <properties-out> -- <command> [args...]
set -euo pipefail

if [[ $# -lt 3 || "$2" != "--" ]]; then
  echo "Usage: $0 <properties-out> -- <command> [args...]" >&2
  exit 2
fi

OUT="$1"
shift 2

START_EPOCH=$(date +%s)
START_UTC=$(date -u '+%Y-%m-%dT%H:%M:%SZ')
"$@" &
PID=$!
if wait "$PID"; then
  EXIT_CODE=0
else
  EXIT_CODE=$?
fi
END_EPOCH=$(date +%s)
END_UTC=$(date -u '+%Y-%m-%dT%H:%M:%SZ')

{
  printf 'started_at_utc=%s\n' "$START_UTC"
  printf 'ended_at_utc=%s\n' "$END_UTC"
  printf 'wall_time_sec=%s\n' "$((END_EPOCH - START_EPOCH))"
  printf 'runtime_exit_code=%s\n' "$EXIT_CODE"
} > "$OUT"

exit "$EXIT_CODE"
