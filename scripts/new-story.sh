#!/usr/bin/env bash
# Submit a new customer Story for prepare (specification → analysis → planning → readiness).
# Background JVM continues after this command returns; use story-ctl.sh status to watch.
set -euo pipefail

AI4SE_FIELD_SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=lib/resolve-bundle.sh
source "$AI4SE_FIELD_SCRIPT_DIR/lib/resolve-bundle.sh"

usage() {
  cat <<'EOF'
用法：
  ./scripts/new-story.sh <客户仓> <story-id> <需求.md> <cursor|codex|claude> \
      [--model <id>] [--attachment <文件>]... [--max-dev-rounds N]

作用：
  deliver prepare —— 后台跑到 READY（或停在待澄清）；不自动开始无人值守开发

下一步：
  ./scripts/story-ctl.sh status <客户仓> <story-id>
  ./scripts/story-ctl.sh answer <客户仓> <story-id> --answer '…' --actor <姓名>
  ./scripts/story-ctl.sh start  <客户仓> <story-id> --actor <姓名>
  ./scripts/story-ctl.sh report <客户仓> <story-id>
EOF
}

if [[ $# -lt 4 ]]; then
  usage
  exit 2
fi

WORKSPACE="$1"
STORY="$2"
REQUEST="$3"
ADAPTER="$4"
shift 4
MODEL=""
MAX_ROUNDS="3"
ATTACHMENTS=()
while [[ $# -gt 0 ]]; do
  case "$1" in
    --model) MODEL="${2:-}"; shift 2 ;;
    --max-dev-rounds) MAX_ROUNDS="${2:-}"; shift 2 ;;
    --attachment) ATTACHMENTS+=(--attachment "${2:-}"); shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) _ai4se_fail "unknown option: $1" ;;
  esac
done

ai4se_require_dir "$WORKSPACE"
WORKSPACE="$(cd "$WORKSPACE" && pwd)"
[[ -f "$REQUEST" ]] || _ai4se_fail "request file not found: $REQUEST"
REQUEST="$(cd "$(dirname "$REQUEST")" && pwd)/$(basename "$REQUEST")"
[[ -n "$STORY" ]] || _ai4se_fail "story id required"
ai4se_require_adapter "$ADAPTER"
ai4se_resolve_bundle

ARGS=(
  deliver prepare
  --workspace "$WORKSPACE"
  --story "$STORY"
  --request-file "$REQUEST"
  --adapter "$ADAPTER"
  --max-dev-rounds "$MAX_ROUNDS"
)
if [[ -n "$MODEL" ]]; then
  ARGS+=(--model "$MODEL")
fi
ARGS+=("${ATTACHMENTS[@]}")

echo "bundle=$AI4SE_BUNDLE_ROOT"
echo "workspace=$WORKSPACE"
echo "story=$STORY"
echo "adapter=$ADAPTER"
"$AI4SE_BIN" "${ARGS[@]}"
echo
echo "next=./scripts/story-ctl.sh status \"$WORKSPACE\" \"$STORY\""
