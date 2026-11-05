#!/usr/bin/env bash
# First contact at a customer repo: install host bridge, deterministic onboard, model discovery.
# Does not modify business source, does not push, does not create a delivery commit.
set -euo pipefail

AI4SE_FIELD_SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=lib/resolve-bundle.sh
source "$AI4SE_FIELD_SCRIPT_DIR/lib/resolve-bundle.sh"

usage() {
  cat <<'EOF'
用法：
  ./scripts/bootstrap-customer.sh <客户仓绝对路径> <cursor|codex|claude> [--model <id>] [--rebuild]

作用：
  构建/定位 Host Bundle → ai4se-flow bootstrap（安装、确定性扫描、模型 Discovery、working 知识）

环境变量：
  AI4SE_BUNDLE   已有 Bundle 根目录（跳过自动构建）
  AI4SE_REBUILD_BUNDLE=1  强制重建 dist/host-bundle
EOF
}

if [[ $# -lt 2 ]]; then
  usage
  exit 2
fi

WORKSPACE="$1"
ADAPTER="$2"
shift 2
MODEL=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --model) MODEL="${2:-}"; shift 2 ;;
    --rebuild) AI4SE_REBUILD_BUNDLE=1; shift ;;
    -h|--help) usage; exit 0 ;;
    *) _ai4se_fail "unknown option: $1" ;;
  esac
done

ai4se_require_dir "$WORKSPACE"
WORKSPACE="$(cd "$WORKSPACE" && pwd)"
ai4se_require_adapter "$ADAPTER"
ai4se_resolve_bundle

ARGS=(bootstrap --workspace "$WORKSPACE" --adapter "$ADAPTER")
if [[ -n "$MODEL" ]]; then
  ARGS+=(--model "$MODEL")
fi

echo "bundle=$AI4SE_BUNDLE_ROOT"
echo "workspace=$WORKSPACE"
echo "adapter=$ADAPTER"
exec "$AI4SE_FLOW" "${ARGS[@]}"
