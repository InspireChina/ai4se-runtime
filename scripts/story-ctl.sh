#!/usr/bin/env bash
# Thin field control for one Story: status / answer / start / report / ready.
set -euo pipefail

AI4SE_FIELD_SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=lib/resolve-bundle.sh
source "$AI4SE_FIELD_SCRIPT_DIR/lib/resolve-bundle.sh"

usage() {
  cat <<'EOF'
用法：
  ./scripts/story-ctl.sh status  <客户仓> <story-id>
  ./scripts/story-ctl.sh ready   <客户仓> <story-id>          # 打印就绪摘要与 fingerprint
  ./scripts/story-ctl.sh answer  <客户仓> <story-id> --answer <原文> --actor <姓名> [--ack-assumptions yes]
  ./scripts/story-ctl.sh start   <客户仓> <story-id> --actor <姓名> [--approve <sha>] [--i-approve]
  ./scripts/story-ctl.sh report  <客户仓> <story-id>

start：
  省略 --approve 时从 .story/<id>/controller/readiness.properties 读取 readiness_sha256。
  未传 --i-approve 时会展示就绪摘要并要求输入 START 才真正批准。
EOF
}

CMD="${1:-}"
if [[ -z "$CMD" || "$CMD" == "-h" || "$CMD" == "--help" ]]; then
  usage
  exit 0
fi
shift || true

case "$CMD" in
  status|ready|answer|start|report) ;;
  *) usage; _ai4se_fail "unknown command: $CMD" ;;
esac

if [[ $# -lt 2 ]]; then
  usage
  exit 2
fi

WORKSPACE="$1"
STORY="$2"
shift 2
ai4se_require_dir "$WORKSPACE"
WORKSPACE="$(cd "$WORKSPACE" && pwd)"
[[ -n "$STORY" ]] || _ai4se_fail "story id required"
ai4se_resolve_bundle

CONTROLLER="$WORKSPACE/.story/$STORY/controller"
READINESS_MD="$CONTROLLER/readiness.md"
READINESS_PROPS="$CONTROLLER/readiness.properties"

read_sha() {
  [[ -f "$READINESS_PROPS" ]] || _ai4se_fail "missing $READINESS_PROPS（Story 尚未 READY）"
  local sha
  sha="$(grep -E '^readiness_sha256=' "$READINESS_PROPS" | head -1 | cut -d= -f2- | tr -d '\r')"
  [[ -n "$sha" ]] || _ai4se_fail "readiness_sha256 missing in $READINESS_PROPS"
  printf '%s' "$sha"
}

case "$CMD" in
  status)
    exec "$AI4SE_BIN" deliver status --workspace "$WORKSPACE" --story "$STORY"
    ;;
  report)
    exec "$AI4SE_BIN" deliver report --workspace "$WORKSPACE" --story "$STORY"
    ;;
  ready)
    if [[ -f "$READINESS_MD" ]]; then
      echo "----- $READINESS_MD -----"
      sed -n '1,200p' "$READINESS_MD"
      echo "----- end -----"
    else
      echo "readiness.md 尚未生成；先看 status。"
    fi
    if [[ -f "$READINESS_PROPS" ]]; then
      echo
      echo "fingerprint:"
      grep -E '^(readiness_sha256|status|at)=' "$READINESS_PROPS" || true
    fi
    "$AI4SE_BIN" deliver status --workspace "$WORKSPACE" --story "$STORY"
    ;;
  answer)
    ANSWER=""
    ACTOR=""
    ACK=""
    while [[ $# -gt 0 ]]; do
      case "$1" in
        --answer) ANSWER="${2:-}"; shift 2 ;;
        --actor) ACTOR="${2:-}"; shift 2 ;;
        --ack-assumptions) ACK="${2:-}"; shift 2 ;;
        *) _ai4se_fail "unknown option: $1" ;;
      esac
    done
    [[ -n "$ANSWER" && -n "$ACTOR" ]] || _ai4se_fail "answer 需要 --answer 与 --actor"
    ARGS=(deliver answer --workspace "$WORKSPACE" --story "$STORY" --answer "$ANSWER" --actor "$ACTOR")
    if [[ -n "$ACK" ]]; then
      ARGS+=(--ack-assumptions "$ACK")
    fi
    exec "$AI4SE_BIN" "${ARGS[@]}"
    ;;
  start)
    ACTOR=""
    APPROVE=""
    I_APPROVE=0
    while [[ $# -gt 0 ]]; do
      case "$1" in
        --actor) ACTOR="${2:-}"; shift 2 ;;
        --approve) APPROVE="${2:-}"; shift 2 ;;
        --i-approve) I_APPROVE=1; shift ;;
        *) _ai4se_fail "unknown option: $1" ;;
      esac
    done
    [[ -n "$ACTOR" ]] || _ai4se_fail "start 需要 --actor"
    if [[ -z "$APPROVE" ]]; then
      APPROVE="$(read_sha)"
    fi
    if [[ -f "$READINESS_MD" ]]; then
      echo "----- 交付就绪摘要 -----"
      sed -n '1,200p' "$READINESS_MD"
      echo "----- end -----"
      echo "readiness_sha256=$APPROVE"
    fi
    if [[ "$I_APPROVE" -ne 1 ]]; then
      echo
      read -r -p "确认开始无人值守请输入 START（其它输入安全停止）: " reply
      if [[ "$reply" != "START" ]]; then
        echo "未记录批准；未启动开发。"
        exit 20
      fi
    fi
    exec "$AI4SE_BIN" deliver start \
      --workspace "$WORKSPACE" \
      --story "$STORY" \
      --approve "$APPROVE" \
      --actor "$ACTOR"
    ;;
esac
