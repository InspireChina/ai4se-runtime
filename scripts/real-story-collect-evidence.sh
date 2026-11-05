#!/usr/bin/env bash
# Collect a finished real-story run without modifying the customer worktree.
# Usage:
#   real-story-collect-evidence.sh <evidence-root> <workspace> <story-id>
#     <baseline-commit> <runtime-jar> <pair-id> <model-id>
#     <human-interventions> <missed-acceptance> <diff-verdict>
set -euo pipefail

if [[ $# -ne 10 ]]; then
  echo "Usage: $0 <evidence-root> <workspace> <story-id> <baseline-commit> <runtime-jar> <pair-id> <model-id> <human-interventions> <missed-acceptance> <diff-verdict>" >&2
  exit 2
fi

EVIDENCE_ROOT=$1
WORKSPACE=$2
STORY_ID=$3
BASELINE=$4
RUNTIME_JAR=$5
PAIR_ID=$6
MODEL_ID=$7
HUMAN_INTERVENTIONS=$8
MISSED_ACCEPTANCE=$9
DIFF_VERDICT=${10}
ARM="$EVIDENCE_ROOT/arm-b"
STORY_ROOT="$WORKSPACE/.story/$STORY_ID"

test -d "$EVIDENCE_ROOT"
test -e "$WORKSPACE/.git"
test -d "$STORY_ROOT"
test -f "$RUNTIME_JAR"
test -f "$ARM/run.properties"
test "$(git -C "$WORKSPACE" rev-parse --verify "$BASELINE^{commit}")" = "$BASELINE"

for target in \
  "$ARM/status.txt" "$ARM/scorecard.txt" "$ARM/scorecard.csv" \
  "$ARM/RUN-SUMMARY.md" "$ARM/REVIEW-REQUEST.md" \
  "$ARM/baseline..HEAD.patch" "$ARM/changed-files.txt" \
  "$ARM/final-git-status.txt" "$ARM/git-log.txt" "$ARM/final-head.txt" \
  "$ARM/run.properties.frozen" "$ARM/story" "$EVIDENCE_ROOT/EVIDENCE-SHA256.txt"; do
  if [[ -e "$target" ]]; then
    echo "Refusing to overwrite existing evidence artifact: $target" >&2
    exit 2
  fi
done

wall_time_sec=$(awk -F= '$1 == "wall_time_sec" { print $2; exit }' "$ARM/run.properties")
if [[ ! "$wall_time_sec" =~ ^[0-9]+$ ]]; then
  wall_time_sec=na
fi

java -jar "$RUNTIME_JAR" status \
  --workspace "$WORKSPACE" --story "$STORY_ID" > "$ARM/status.txt"
java -jar "$RUNTIME_JAR" scorecard \
  --workspace "$WORKSPACE" --story "$STORY_ID" --arm B \
  --pair-id "$PAIR_ID" --baseline-commit "$BASELINE" --model-id "$MODEL_ID" \
  --human-interventions "$HUMAN_INTERVENTIONS" \
  --missed-acceptance "$MISSED_ACCEPTANCE" --diff-verdict "$DIFF_VERDICT" \
  --wall-time-sec "$wall_time_sec" > "$ARM/scorecard.txt"
awk '/^pair_id,/{capture=1} capture {print}' "$ARM/scorecard.txt" > "$ARM/scorecard.csv"

git -C "$WORKSPACE" diff --binary "$BASELINE" > "$ARM/baseline..HEAD.patch"
git -C "$WORKSPACE" diff --name-only "$BASELINE" > "$ARM/changed-files.txt"
git -C "$WORKSPACE" status --porcelain=v1 > "$ARM/final-git-status.txt"
git -C "$WORKSPACE" log --oneline --decorate "$BASELINE..HEAD" > "$ARM/git-log.txt"
git -C "$WORKSPACE" rev-parse HEAD > "$ARM/final-head.txt"
cp "$ARM/run.properties" "$ARM/run.properties.frozen"
cp -R "$STORY_ROOT" "$ARM/story"

terminal=$(awk -F= '$1 == "terminal" { print $2; exit }' "$STORY_ROOT/run/state.properties")
commit=$(awk -F': ' '/commit_sha:/ { print $2; exit }' "$STORY_ROOT/delivery/delivery.md" 2>/dev/null || true)
{
  echo '# Run summary'
  echo
  echo "- story_id: \`$STORY_ID\`"
  echo "- terminal: \`${terminal:-unknown}\`"
  echo "- wall_time_sec: \`$wall_time_sec\`"
  echo "- baseline_commit: \`$BASELINE\`"
  echo "- final_head: \`$(cat "$ARM/final-head.txt")\`"
  echo "- delivery_commit: \`${commit:--}\`"
  echo "- status: [status.txt](status.txt)"
  echo "- scorecard: [scorecard.txt](scorecard.txt)"
  echo "- verification: [report-round-1.md](story/verification/report-round-1.md)"
} > "$ARM/RUN-SUMMARY.md"
{
  echo '# Review request'
  echo
  echo "Review the frozen R-001-style run for story \`$STORY_ID\`."
  echo
  echo "- [Run summary](RUN-SUMMARY.md)"
  echo "- [Status](status.txt)"
  echo "- [Scorecard](scorecard.txt)"
  echo "- [Patch](baseline..HEAD.patch)"
  echo "- [Changed files](changed-files.txt)"
  echo "- [Story snapshot](story/)"
} > "$ARM/REVIEW-REQUEST.md"

find "$EVIDENCE_ROOT" -type f ! -name EVIDENCE-SHA256.txt -print0 \
  | sort -z | xargs -0 shasum -a 256 > "$EVIDENCE_ROOT/EVIDENCE-SHA256.txt"
