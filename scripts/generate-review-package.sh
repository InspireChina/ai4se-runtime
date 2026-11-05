#!/usr/bin/env bash
# Sprint-5.5 — generate a ReviewPackage (no AI, no GitHub API).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

export JAVA_HOME="${JAVA_HOME:-$HOME/Library/Java/JavaVirtualMachines/corretto-1.8.0_502/Contents/Home}"
export PATH="${JAVA_HOME}/bin:${PATH}"

ARGS=("$@")
if [[ ${#ARGS[@]} -eq 0 ]]; then
  ARGS=(--repo-root "$ROOT")
fi

mvn -pl ai4se-review-tools -q -DskipTests package
mvn -pl ai4se-review-tools -q exec:java -Dexec.args="${ARGS[*]}"
