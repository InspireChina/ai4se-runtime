#!/usr/bin/env bash
# Persist wall-clock timing and exit code for PR4 A/B captures.
# Usage:
#   pr4-timed-capture.sh <properties-out> -- <command> [args...]
# Writes started_at_utc / ended_at_utc / wall_time_sec / exit_code (never blank).
set -euo pipefail

if [[ $# -lt 3 || "$2" != "--" ]]; then
  echo "Usage: $0 <properties-out> -- <command> [args...]" >&2
  exit 2
fi

OUT="$1"
shift 2

START_EPOCH=$(date +%s)
START_UTC=$(date -u '+%Y-%m-%dT%H:%M:%SZ')
set +e
"$@"
EXIT=$?
set -e
END_EPOCH=$(date +%s)
END_UTC=$(date -u '+%Y-%m-%dT%H:%M:%SZ')
WALL=$((END_EPOCH - START_EPOCH))

{
  printf 'started_at_utc=%s\n' "$START_UTC"
  printf 'ended_at_utc=%s\n' "$END_UTC"
  printf 'wall_time_sec=%s\n' "$WALL"
  printf 'exit_code=%s\n' "$EXIT"
} > "$OUT"

exit "$EXIT"
