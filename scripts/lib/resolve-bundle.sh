#!/usr/bin/env bash
# Shared locator for the customer Host Bundle. Sourced by field scripts; not run alone.
# Callers must set AI4SE_FIELD_SCRIPT_DIR to their own script directory before sourcing.
# Resolution order: AI4SE_BUNDLE → parent of field scripts (Bundle layout) → dist/host-bundle → build.

_ai4se_fail() {
  echo "AI4SE: $*" >&2
  exit 2
}

_ai4se_is_bundle() {
  local root="$1"
  [[ -x "$root/bin/ai4se" && -f "$root/lib/ai4se-runtime.jar" && -x "$root/bin/ai4se-flow" ]]
}

# Sets: AI4SE_BUNDLE_ROOT, AI4SE_BIN, AI4SE_FLOW；可选 AI4SE_RUNTIME_ROOT
ai4se_resolve_bundle() {
  local rebuild="${AI4SE_REBUILD_BUNDLE:-0}"
  local script_root candidate

  script_root="${AI4SE_FIELD_SCRIPT_DIR:-}"
  [[ -n "$script_root" ]] || _ai4se_fail "AI4SE_FIELD_SCRIPT_DIR unset; field script must set it before resolve"

  if [[ -n "${AI4SE_BUNDLE:-}" ]]; then
    candidate="$(cd "$AI4SE_BUNDLE" && pwd)"
    _ai4se_is_bundle "$candidate" || _ai4se_fail "AI4SE_BUNDLE is not a Host Bundle: $candidate"
    AI4SE_BUNDLE_ROOT="$candidate"
  elif _ai4se_is_bundle "$(cd "$script_root/.." && pwd)"; then
    # …/host-bundle/scripts/*.sh
    AI4SE_BUNDLE_ROOT="$(cd "$script_root/.." && pwd)"
  elif _ai4se_is_bundle "$script_root"; then
    AI4SE_BUNDLE_ROOT="$script_root"
  else
    if [[ -f "$script_root/../pom.xml" ]]; then
      AI4SE_RUNTIME_ROOT="$(cd "$script_root/.." && pwd)"
    else
      AI4SE_RUNTIME_ROOT=""
    fi
    candidate="${AI4SE_RUNTIME_ROOT}/dist/host-bundle"
    if [[ "$rebuild" == "1" && -d "$candidate" ]]; then
      rm -rf "$candidate"
    fi
    if ! _ai4se_is_bundle "$candidate"; then
      [[ -n "$AI4SE_RUNTIME_ROOT" ]] || _ai4se_fail "cannot find source checkout or Bundle; set AI4SE_BUNDLE"
      [[ -x "$AI4SE_RUNTIME_ROOT/scripts/build-host-bundle.sh" ]] \
        || _ai4se_fail "missing build-host-bundle.sh under $AI4SE_RUNTIME_ROOT"
      mkdir -p "$(dirname "$candidate")"
      echo "AI4SE: building Host Bundle → $candidate"
      "$AI4SE_RUNTIME_ROOT/scripts/build-host-bundle.sh" "$candidate"
    fi
    AI4SE_BUNDLE_ROOT="$(cd "$candidate" && pwd)"
  fi

  _ai4se_is_bundle "$AI4SE_BUNDLE_ROOT" || _ai4se_fail "invalid Host Bundle: $AI4SE_BUNDLE_ROOT"
  AI4SE_BIN="$AI4SE_BUNDLE_ROOT/bin/ai4se"
  AI4SE_FLOW="$AI4SE_BUNDLE_ROOT/bin/ai4se-flow"
  export AI4SE_BUNDLE_ROOT AI4SE_BIN AI4SE_FLOW
}

ai4se_require_adapter() {
  case "${1:-}" in
    cursor|codex|claude) ;;
    *) _ai4se_fail "adapter must be cursor|codex|claude (got: ${1:-})" ;;
  esac
}

ai4se_require_dir() {
  [[ -d "$1" ]] || _ai4se_fail "not a directory: $1"
}
