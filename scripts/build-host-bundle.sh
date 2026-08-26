#!/usr/bin/env bash
set -euo pipefail

# Creates a small, reviewable customer-desktop bundle. It does not download a JRE, install a
# model CLI, edit a customer repository, or overwrite an existing release directory.

if [[ $# -ne 1 ]]; then
  echo "usage: $0 <empty-output-directory>" >&2
  exit 2
fi

OUTPUT_DIR="$1"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
RUNTIME_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
JAR="$RUNTIME_ROOT/ai4se-demo/target/ai4se-runtime.jar"

if [[ -e "$OUTPUT_DIR" ]]; then
  echo "refusing to overwrite existing output directory: $OUTPUT_DIR" >&2
  exit 2
fi

(cd "$RUNTIME_ROOT" && mvn -pl ai4se-demo -am package -DskipTests)
mkdir -p "$OUTPUT_DIR/bin" "$OUTPUT_DIR/lib" "$OUTPUT_DIR/docs"
cp "$JAR" "$OUTPUT_DIR/lib/ai4se-runtime.jar"
cp "$RUNTIME_ROOT/docs/90-status/customer-host-bridge-runbook-v1.md" \
  "$OUTPUT_DIR/docs/customer-host-bridge-runbook-v1.md"
cp "$RUNTIME_ROOT/docs/00-product/customer-host-bridge-day-one-guide.md" \
  "$OUTPUT_DIR/docs/customer-host-bridge-day-one-guide.md"
cp "$RUNTIME_ROOT/docs/00-product/portable-host-harness-contract-v1.md" \
  "$OUTPUT_DIR/docs/portable-host-harness-contract-v1.md"
cp "$RUNTIME_ROOT/templates/host-bundle/ai4se" "$OUTPUT_DIR/bin/ai4se"
cp "$RUNTIME_ROOT/templates/host-bundle/README.md" "$OUTPUT_DIR/README.md"
chmod +x "$OUTPUT_DIR/bin/ai4se"

echo "bundle=READY"
echo "root=$OUTPUT_DIR"
echo "command=$OUTPUT_DIR/bin/ai4se"
