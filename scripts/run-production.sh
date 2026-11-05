#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JAR="$ROOT/ai4se-demo/target/ai4se-runtime.jar"
if [[ ! -f "$JAR" ]]; then
  (cd "$ROOT" && mvn -pl ai4se-demo -am package -DskipTests -q)
fi
exec java -jar "$JAR" \
  --workspace "${1:-$ROOT/ai4se-demo/sample-workspace}" \
  --input "${2:-$ROOT/ai4se-demo/sample-input}"
