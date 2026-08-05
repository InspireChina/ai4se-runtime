#!/usr/bin/env bash
# Open a Story seed under .story/<id>/ (W2 · S2)
# Usage: ./scripts/open-story.sh /path/to/customer-repo <story-id> [requirement.md]
set -euo pipefail

ROOT="${1:-}"
STORY_ID="${2:-}"
SEED="${3:-}"

if [[ -z "$ROOT" || -z "$STORY_ID" || ! -d "$ROOT" ]]; then
  echo "Usage: $0 /path/to/customer-repo <story-id> [path/to/requirement.md]" >&2
  exit 1
fi

if [[ ! "$STORY_ID" =~ ^[A-Za-z0-9][A-Za-z0-9._-]*$ ]]; then
  echo "Invalid story-id: $STORY_ID" >&2
  exit 1
fi

ROOT="$(cd "$ROOT" && pwd)"
if [[ ! -d "$ROOT/.ai4se" || ! -d "$ROOT/.story" ]]; then
  echo "Workspace not onboarded. Run: ./scripts/onboard-repo.sh $ROOT" >&2
  exit 1
fi

DEST="$ROOT/.story/$STORY_ID"
mkdir -p "$DEST/packages"

TEMPLATE="$(cd "$(dirname "$0")/.." && pwd)/templates/story/_template/requirement.md"
if [[ -n "$SEED" ]]; then
  if [[ ! -f "$SEED" ]]; then
    echo "Seed file not found: $SEED" >&2
    exit 1
  fi
  cp "$SEED" "$DEST/requirement.md"
elif [[ ! -f "$DEST/requirement.md" ]]; then
  if [[ ! -f "$TEMPLATE" ]]; then
    echo "Template missing: $TEMPLATE" >&2
    exit 1
  fi
  cp "$TEMPLATE" "$DEST/requirement.md"
  echo "Wrote template requirement — fill acceptance before Analysis Package build."
else
  echo "Story already exists: $DEST (requirement.md kept)"
fi

echo "Story opened: $DEST"
echo "Edit acceptance in requirement.md, then:"
echo "  java -cp ai4se-demo/target/ai4se-runtime.jar \\"
echo "    com.ai4se.runtime.demo.pathway.PathwayWaveMain \\"
echo "    --workspace $ROOT --story $STORY_ID --wave w2"
