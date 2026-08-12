# M1 PR4 真仓实验操作手册：JSON-java / pair-json-001

> 目的：让一个没有参与 ai4se-runtime 开发的人（或独立 Cursor 操作会话）从零完成一次可复核的 A/B 真仓实验。
> 本文是操作协议，不是答案提示。不得查阅该历史缺陷后续的 GitHub Issue、PR、commit 或 release diff。

## 0. 本次实验回答什么问题

本次只做 **pair-json-001**，用于确认 ai4se-runtime 能否在陌生、真实的 Java/Maven 仓库中完成：

1. 仓库 onboarding；
2. Analysis → Planning → Development ↔ Verification → Review → Delivery；
3. write scope 约束；
4. 中断恢复和机器终态；
5. 本地 commit；
6. scorecard 独立审计；
7. 与裸 Cursor 的同基线、同模型对照。

这只是第一组真仓冒烟实验。单组成功不能签收最终 PR4，失败也不能直接证明蓝图无效；失败必须按本文留下证据，之后才能判断应修 runtime、Context Package、Adapter、验证入口还是实验协议。

## 1. 强制实验纪律

### 1.1 必须满足

- A/B 使用同一个 `baseline_commit`。
- A/B 使用完全相同的 requirement 文件（以 SHA-256 证明）。
- A/B 使用同一个 Cursor `model_id`。
- A/B 各自在独立 worktree、独立 Cursor 会话中运行。
- 每组整条流程最多 10 分钟；B 另外最多 3 个 Development 轮次。
- `--timeout-minutes` 只是单次 Adapter 上限，不能代替整组 10 分钟硬截止；本文用外层 PID 监控执行总时限。
- 不 push，不向上游创建 Issue/PR。
- 不查看 `20251224` 之后的仓库历史，不搜索这个缺陷的公开答案。
- 不删除失败现场，不 reset，不手工美化 Agent 的 diff。
- 只有进程崩溃/被中断才允许 `resume`；业务失败、测试失败、预算耗尽不允许人工提示后重跑。
- 所有实验日志放在客户仓之外，避免污染 target repo 和 commit scope。

### 1.2 禁止作为有效证据的情况

- A、B baseline 不同；
- 两组使用不同模型；
- 第二次运行覆盖第一次失败现场；
- 操作者在运行中向 Agent 提示实现方法；
- Agent 查阅后续修复 commit/PR；
- 人工把失败代码修绿后再采分；
- fixture、seeded output、`FunctionalModelCliAdapter` 进入 B 组；
- 结果只有“测试绿了”的口头描述，没有日志、diff、events 和 scorecard。

出现上述情况时保留证据，但在 `REVIEW-REQUEST.md` 中标记 `experiment_valid: no`，新建 pair 重跑，禁止改写原结果。

## 2. 仓库与 Story

- 上游仓库：`https://github.com/stleary/JSON-java.git`
- 固定上游 tag：`20251224`
- 实验 Story ID：`json-static-001`
- Pair ID：`pair-json-001`
- 允许改动：
  - `src/main/java/org/json/JSONObject.java`
  - `src/test/java/org/json/junit/JSONObjectTest.java`
- 客户仓验证入口：`mvn clean test`

选择浅克隆并在 onboarding 前移除 remote，是为了防止 Agent 从本地 git 历史直接看到后续答案。

## 3. 最终必须交付的证据树

实验结束时，客户仓之外必须存在：

```text
pair-json-001-evidence/
├── README.md
├── manifest.properties
├── environment.txt
├── upstream-provenance.txt
├── requirement.md
├── requirement.sha256
├── baseline/
│   ├── onboard.stdout.txt
│   ├── entries.yaml
│   ├── baseline.md
│   ├── baseline-commit.txt
│   ├── git-status.txt
│   ├── test.stdout.txt
│   └── test.exit.txt
├── arm-a/
│   ├── agent.stdout.txt
│   ├── agent.stderr.txt
│   ├── run.properties
│   ├── git-status.txt
│   ├── changed-files.txt
│   ├── untracked-files.txt
│   ├── untracked-files.tar
│   ├── result.patch
│   ├── test.stdout.txt
│   ├── test.exit.txt
│   ├── acceptance-review.md
│   ├── metrics.properties
│   └── final-commit.txt
├── arm-b/
│   ├── runtime.stdout.txt
│   ├── runtime.stderr.txt
│   ├── run.properties
│   ├── status.txt
│   ├── git-status.txt
│   ├── changed-files.txt
│   ├── untracked-files.txt
│   ├── untracked-files.tar
│   ├── result.patch
│   ├── test.stdout.txt
│   ├── test.exit.txt
│   ├── acceptance-review.md
│   ├── scorecard.txt
│   ├── scorecard.csv
│   ├── story-evidence/
│   │   └── （完整复制 .story/json-static-001/）
│   └── final-commit.txt
├── pair-comparison.md
└── REVIEW-REQUEST.md
```

最后审阅时，`story-evidence/run/events.jsonl`、`state.properties`、各阶段 Package、Adapter audit、verification report、delivery record、两组 patch 和原始终端日志都是必需材料。

## 4. 阶段一：配置实验目录

以下命令默认在 macOS/zsh 执行。Cursor 首先设置四个变量；`MODEL_ID` 必须是 Cursor CLI 实际接受的精确模型 ID，不得写展示名称或临时别名。

```bash
export AI4SE_ROOT="/Users/peng.lv/IdeaProjects/ai4se-runtime"
export LAB_ROOT="/Users/peng.lv/IdeaProjects/ai4se-pr4-lab"
export MODEL_ID="在这里填写固定的Cursor模型ID"
export CURSOR_BIN="/Applications/Cursor.app/Contents/Resources/app/bin/cursor"

export SOURCE_REPO="$LAB_ROOT/json-java-source"
export ARM_A_REPO="$LAB_ROOT/json-java-pair001-a"
export ARM_B_REPO="$LAB_ROOT/json-java-pair001-b"
export EVIDENCE_ROOT="$LAB_ROOT/pair-json-001-evidence"
export REQUIREMENT_FILE="$EVIDENCE_ROOT/requirement.md"
export RUNTIME_JAR="$AI4SE_ROOT/ai4se-demo/target/ai4se-runtime.jar"
```

配置检查：

```bash
test -d "$AI4SE_ROOT/.git"
test -n "$MODEL_ID"
test "$MODEL_ID" != "在这里填写固定的Cursor模型ID"
test -x "$CURSOR_BIN"
"$CURSOR_BIN" agent --help
java -version
mvn -version
git --version
```

如果 Cursor CLI 是独立的 `agent` binary，而不是 Cursor.app 的 `cursor` binary：

```bash
export CURSOR_BIN="$(command -v agent)"
"$CURSOR_BIN" --help
```

后文 A 组命令已同时兼容这两种形式。

确认 `$LAB_ROOT` 不含需要保留的旧实验数据。若目录已存在，**停止并换一个新的 LAB_ROOT**，不要删除或覆盖旧证据。

```bash
test ! -e "$SOURCE_REPO"
test ! -e "$ARM_A_REPO"
test ! -e "$ARM_B_REPO"
test ! -e "$EVIDENCE_ROOT"

mkdir -p "$EVIDENCE_ROOT/baseline"
mkdir -p "$EVIDENCE_ROOT/arm-a"
mkdir -p "$EVIDENCE_ROOT/arm-b"

{
  printf '# pair-json-001 evidence\n\n'
  printf 'Raw evidence for the JSON-java A/B external repository experiment.\n'
  printf 'Do not edit generated logs after capture.\n'
} > "$EVIDENCE_ROOT/README.md"
```

## 5. 阶段二：记录 runtime 和机器环境

先确认 ai4se-runtime 工作树干净。实验使用的 runtime commit 在整个 pair 期间不得变化。

```bash
cd "$AI4SE_ROOT"
git status --short --branch
git diff --check

git rev-parse HEAD > "$EVIDENCE_ROOT/runtime-commit.txt"

{
  date -u '+captured_at_utc=%Y-%m-%dT%H:%M:%SZ'
  uname -a
  java -version
  mvn -version
  git --version
  "$CURSOR_BIN" --version
  printf 'model_id=%s\n' "$MODEL_ID"
  printf 'cursor_bin=%s\n' "$CURSOR_BIN"
  printf 'ai4se_root=%s\n' "$AI4SE_ROOT"
  printf 'runtime_commit=%s\n' "$(git rev-parse HEAD)"
} > "$EVIDENCE_ROOT/environment.txt" 2>&1
```

若 runtime 工作树存在未提交变更，停止实验并在 `REVIEW-REQUEST.md` 中说明；不要让实验混用未知 runtime 代码。

构建正式 CLI，并运行自己的全量测试：

```bash
cd "$AI4SE_ROOT"
mvn test > "$EVIDENCE_ROOT/runtime-test.stdout.txt" 2>&1
printf 'exit_code=%s\n' "$?" > "$EVIDENCE_ROOT/runtime-test.exit.txt"

mvn -q -pl ai4se-demo -am package -DskipTests \
  > "$EVIDENCE_ROOT/runtime-package.stdout.txt" 2>&1
printf 'exit_code=%s\n' "$?" > "$EVIDENCE_ROOT/runtime-package.exit.txt"

test -f "$RUNTIME_JAR"
java -jar "$RUNTIME_JAR" --help \
  > "$EVIDENCE_ROOT/runtime-help.txt" 2>&1
```

任何一步非 0：停止，不进入 A/B。

## 6. 阶段三：浅克隆固定上游版本

```bash
git clone \
  --depth 1 \
  --single-branch \
  --branch 20251224 \
  https://github.com/stleary/JSON-java.git \
  "$SOURCE_REPO" \
  > "$EVIDENCE_ROOT/clone.stdout.txt" 2>&1

cd "$SOURCE_REPO"

git switch -c experiment/base-json-java

{
  printf 'upstream_url=%s\n' "$(git remote get-url origin)"
  printf 'upstream_tag=20251224\n'
  printf 'upstream_commit=%s\n' "$(git rev-parse HEAD)"
  git show -s --format='upstream_subject=%s%nupstream_committed_at=%cI' HEAD
} > "$EVIDENCE_ROOT/upstream-provenance.txt"

git describe --tags --exact-match HEAD
git rev-list --count HEAD
git remote remove origin
git remote -v

if [[ -z "$(git config --get user.name)" ]]; then
  git config user.name "AI4SE Experiment"
fi
if [[ -z "$(git config --get user.email)" ]]; then
  git config user.email "ai4se-experiment@localhost"
fi

{
  printf 'local_git_user_name=%s\n' "$(git config --get user.name)"
  printf 'local_git_user_email=%s\n' "$(git config --get user.email)"
} >> "$EVIDENCE_ROOT/upstream-provenance.txt"
```

验收：

- `git describe` 必须输出 `20251224`；
- 浅克隆初始历史应只有固定 tag 所需对象；
- `git remote -v` 最后必须无输出；
- 后续不得重新添加 remote 或执行 `git fetch`。

先验证上游基线本身：

```bash
cd "$SOURCE_REPO"
BASELINE_TEST_START=$(date +%s)
mvn clean test > "$EVIDENCE_ROOT/baseline/test.stdout.txt" 2>&1
BASELINE_TEST_EXIT=$?
BASELINE_TEST_END=$(date +%s)

{
  printf 'exit_code=%s\n' "$BASELINE_TEST_EXIT"
  printf 'wall_time_sec=%s\n' "$((BASELINE_TEST_END - BASELINE_TEST_START))"
} > "$EVIDENCE_ROOT/baseline/test.exit.txt"
```

若基线测试非 0：立即停止。保留日志并把实验标记为 `ENV_BASELINE_FAIL`，不能让 A/B 修复原始基线噪声。

## 7. 阶段四：onboard 并形成共同 baseline

```bash
cd "$AI4SE_ROOT"
./scripts/onboard-repo.sh "$SOURCE_REPO" \
  > "$EVIDENCE_ROOT/baseline/onboard.stdout.txt" 2>&1
```

人工/操作 Cursor 只做事实校验，不做 AI 分析：

1. 检查 `$SOURCE_REPO/.ai4se/repository/entries.yaml`；
2. 确保 test 至少包含一条真实可执行的 `mvn -q test`；
3. 将 `baseline.md` 中硬编码的绝对 root 改为逻辑根 `.`，避免 worktree 继承错误绝对路径；
4. 不添加实现建议、历史缺陷信息或答案提示。

保存 onboarding 快照：

```bash
cp "$SOURCE_REPO/.ai4se/repository/entries.yaml" \
  "$EVIDENCE_ROOT/baseline/entries.yaml"
cp "$SOURCE_REPO/.ai4se/repository/baseline.md" \
  "$EVIDENCE_ROOT/baseline/baseline.md"

cd "$SOURCE_REPO"
git status --short > "$EVIDENCE_ROOT/baseline/git-status-before-commit.txt"
git add .ai4se .story/README.md
git commit -m "experiment: onboard JSON-java for pair-json-001"

export BASELINE_COMMIT="$(git rev-parse HEAD)"
printf '%s\n' "$BASELINE_COMMIT" > "$EVIDENCE_ROOT/baseline/baseline-commit.txt"
git status --porcelain > "$EVIDENCE_ROOT/baseline/git-status.txt"
test ! -s "$EVIDENCE_ROOT/baseline/git-status.txt"
```

这个 onboarding commit 才是 A/B 的共同 `baseline_commit`，不是上游 tag 的 SHA。

## 8. 阶段五：创建隔离的 A/B worktree

```bash
cd "$SOURCE_REPO"

git worktree add "$ARM_A_REPO" \
  -b experiment/pair-json-001-a "$BASELINE_COMMIT"

git worktree add "$ARM_B_REPO" \
  -b experiment/pair-json-001-b "$BASELINE_COMMIT"

test "$(git -C "$ARM_A_REPO" rev-parse HEAD)" = "$BASELINE_COMMIT"
test "$(git -C "$ARM_B_REPO" rev-parse HEAD)" = "$BASELINE_COMMIT"
test -z "$(git -C "$ARM_A_REPO" status --porcelain)"
test -z "$(git -C "$ARM_B_REPO" status --porcelain)"
```

写入实验 manifest：

```bash
{
  printf 'pair_id=pair-json-001\n'
  printf 'story_id=json-static-001\n'
  printf 'upstream_tag=20251224\n'
  printf 'baseline_commit=%s\n' "$BASELINE_COMMIT"
  printf 'runtime_commit=%s\n' "$(git -C "$AI4SE_ROOT" rev-parse HEAD)"
  printf 'model_id=%s\n' "$MODEL_ID"
  printf 'max_dev_rounds=3\n'
  printf 'wall_time_limit_sec=600\n'
  printf 'write_scope_1=src/main/java/org/json/JSONObject.java\n'
  printf 'write_scope_2=src/test/java/org/json/junit/JSONObjectTest.java\n'
  printf 'arm_a_workspace=%s\n' "$ARM_A_REPO"
  printf 'arm_b_workspace=%s\n' "$ARM_B_REPO"
  printf 'created_at_utc=%s\n' "$(date -u '+%Y-%m-%dT%H:%M:%SZ')"
} > "$EVIDENCE_ROOT/manifest.properties"
```

## 9. 阶段六：创建唯一 requirement

把以下内容原样保存为 `$REQUIREMENT_FILE`。A/B 不得使用不同副本或追加提示。

```markdown
# Story: JSONObject.fromJson must not modify class-level state

## Background

JSONObject.fromJson maps JSON properties into fields of a newly created target
object. Static fields represent class-level shared state rather than state that
belongs to the deserialized instance.

Deserializing one JSON document must not modify shared static fields.

## Goal

Ensure JSONObject.fromJson ignores static fields while continuing to populate
supported instance fields.

## Acceptance criteria

1. Given a target class with one mutable instance field and one mutable static
   field initialized to a known value, and JSON containing keys matching both:
   - the instance field is populated normally;
   - the static field retains its original value.
2. Cover both public entry paths:
   - JSONObject.fromJson(String, Class)
   - new JSONObject(json).fromJson(Class)
3. Add a regression test demonstrating the behavior.
4. `mvn clean test` exits with code 0.
5. Existing behavior outside this case remains unchanged.

## Allowed files

- src/main/java/org/json/JSONObject.java
- src/test/java/org/json/junit/JSONObjectTest.java

## Out of scope

- New dependencies or build configuration changes.
- Public API redesign or broad reflection refactoring.
- Changes to unrelated serialization behavior.
- Reading later git history, adding/fetching a remote, or searching GitHub/web
  for an existing solution to this Story.
- Pushing commits or opening an upstream Issue/PR.
```

记录不可变 hash：

```bash
shasum -a 256 "$REQUIREMENT_FILE" > "$EVIDENCE_ROOT/requirement.sha256"
```

## 10. 阶段七：运行 A 组（裸 Cursor）

### 10.1 新会话要求

- 使用一个全新的 Cursor CLI 调用；
- 只给 requirement 和 repo；
- 不提供 ai4se 的 Analysis/Plan/Package；
- 不允许操作 Cursor 在运行中补充消息；
- prompt 明确要求不 commit，最终 commit 由操作流程统一处理。

### 10.2 启动并记录

```bash
cd "$ARM_A_REPO"
test "$(git rev-parse HEAD)" = "$BASELINE_COMMIT"
test -z "$(git status --porcelain)"

A_START_EPOCH=$(date +%s)
A_START_UTC=$(date -u '+%Y-%m-%dT%H:%M:%SZ')
A_TIMED_OUT=0

A_PROMPT="$(cat "$REQUIREMENT_FILE")

Execution constraints:
- Work only in the current repository.
- Modify only the two Allowed files.
- Run the acceptance test command yourself.
- Do not commit, push, fetch, add a remote, or search for an existing solution.
- Finish with a concise result summary."

if [[ "$(basename "$CURSOR_BIN")" = "cursor" ]]; then
  "$CURSOR_BIN" agent -p --output-format text \
    --model "$MODEL_ID" --force --approve-mcps \
    "$A_PROMPT" \
    > "$EVIDENCE_ROOT/arm-a/agent.stdout.txt" \
    2> "$EVIDENCE_ROOT/arm-a/agent.stderr.txt" &
else
  "$CURSOR_BIN" -p --output-format text \
    --model "$MODEL_ID" --force --approve-mcps \
    "$A_PROMPT" \
    > "$EVIDENCE_ROOT/arm-a/agent.stdout.txt" \
    2> "$EVIDENCE_ROOT/arm-a/agent.stderr.txt" &
fi

A_PID=$!
while kill -0 "$A_PID" 2>/dev/null; do
  if (( $(date +%s) - A_START_EPOCH >= 600 )); then
    A_TIMED_OUT=1
    kill -TERM "$A_PID" 2>/dev/null || true
    break
  fi
  sleep 5
done

wait "$A_PID"
A_AGENT_EXIT=$?
A_END_EPOCH=$(date +%s)
A_END_UTC=$(date -u '+%Y-%m-%dT%H:%M:%SZ')

{
  printf 'started_at_utc=%s\n' "$A_START_UTC"
  printf 'ended_at_utc=%s\n' "$A_END_UTC"
  printf 'wall_time_sec=%s\n' "$((A_END_EPOCH - A_START_EPOCH))"
  printf 'agent_exit_code=%s\n' "$A_AGENT_EXIT"
  printf 'timed_out=%s\n' "$A_TIMED_OUT"
  printf 'model_id=%s\n' "$MODEL_ID"
  printf 'human_interventions=0\n'
  printf 'requirement_sha256=%s\n' "$(awk '{print $1}' "$EVIDENCE_ROOT/requirement.sha256")"
} > "$EVIDENCE_ROOT/arm-a/run.properties"
```

若 `timed_out=1`，本组终态记为 TIMEOUT；不得继续提示它完成。

### 10.3 冻结 A 组现场

无论成功失败，都立即采集：

```bash
cd "$ARM_A_REPO"
git status --short > "$EVIDENCE_ROOT/arm-a/git-status.txt"
git ls-files --others --exclude-standard \
  > "$EVIDENCE_ROOT/arm-a/untracked-files.txt"
{
  git diff --name-only "$BASELINE_COMMIT"
  cat "$EVIDENCE_ROOT/arm-a/untracked-files.txt"
} | sort -u > "$EVIDENCE_ROOT/arm-a/changed-files.txt"
git diff --binary "$BASELINE_COMMIT" > "$EVIDENCE_ROOT/arm-a/result.patch"

if [[ -s "$EVIDENCE_ROOT/arm-a/untracked-files.txt" ]]; then
  tar -cf "$EVIDENCE_ROOT/arm-a/untracked-files.tar" \
    -T "$EVIDENCE_ROOT/arm-a/untracked-files.txt"
else
  : > "$EVIDENCE_ROOT/arm-a/untracked-files.tar"
fi

A_TEST_START=$(date +%s)
mvn clean test > "$EVIDENCE_ROOT/arm-a/test.stdout.txt" 2>&1
A_TEST_EXIT=$?
A_TEST_END=$(date +%s)

{
  printf 'exit_code=%s\n' "$A_TEST_EXIT"
  printf 'wall_time_sec=%s\n' "$((A_TEST_END - A_TEST_START))"
} > "$EVIDENCE_ROOT/arm-a/test.exit.txt"
```

检查 `changed-files.txt`：只要出现 Allowed Files 之外的业务文件，A 组 `scope_ok=0`，不得清理后伪装成通过。

若 Agent 自己创建了 commit，也不要 amend/reset；记录它。若没有 commit，只有在 diff 通过人工 Acceptance 审阅且范围合法后，由操作流程创建本地结果 commit：

```bash
cd "$ARM_A_REPO"
git log --oneline "$BASELINE_COMMIT"..HEAD \
  > "$EVIDENCE_ROOT/arm-a/preexisting-commits.txt"

if [[ -z "$(git log --format=%H "$BASELINE_COMMIT"..HEAD)" ]] \
    && ! git diff --quiet; then
  git add \
    src/main/java/org/json/JSONObject.java \
    src/test/java/org/json/junit/JSONObjectTest.java
  git commit -m "experiment(A): pair-json-001 result"
fi

git rev-parse HEAD > "$EVIDENCE_ROOT/arm-a/final-commit.txt"
git diff --name-only "$BASELINE_COMMIT"..HEAD \
  > "$EVIDENCE_ROOT/arm-a/committed-files.txt"
```

如果测试失败或范围越界，可不创建 commit；此时 `final-commit.txt` 记录当前 HEAD，并在 metrics 中明确失败，不能把 baseline HEAD 当成成功交付。

### 10.4 填写 A 组事实材料

创建 `arm-a/acceptance-review.md`：

```markdown
# Arm A Acceptance Review

- reviewer:
- reviewed_at_utc:
- agent_exit_code:
- test_exit_code:
- changed_files_only_allowed: yes|no
- instance_field_is_populated: yes|no|not_proven
- static_field_retains_original_value: yes|no|not_proven
- both_public_entry_paths_covered: yes|no|not_proven
- regression_test_added: yes|no
- unrelated_behavior_changed: yes|no|unknown
- missed_acceptance_count:
- diff_verdict: accept|minor_fix|reject
- evidence_notes:
```

创建 `arm-a/metrics.properties`：

```properties
pair_id=pair-json-001
story_id=json-static-001
arm=A
baseline_commit=<BASELINE_COMMIT>
model_id=<MODEL_ID>
terminal=<SUCCESS|FAILED|TIMEOUT>
agent_exit_code=<N>
test_exit_code=<N>
wall_time_sec=<N>
human_interventions=0
missed_acceptance=<N>
diff_verdict=<accept|minor_fix|reject>
scope_ok=<1|0>
input_tokens=na
output_tokens=na
tool_calls=na
notes=<事实，不写推测>
```

## 11. 阶段八：运行 B 组（ai4se-runtime）

使用新的操作终端/会话，不把 A 组 diff、日志、结论传给 B。

### 11.1 启动前检查

```bash
cd "$ARM_B_REPO"
test "$(git rev-parse HEAD)" = "$BASELINE_COMMIT"
test -z "$(git status --porcelain)"
test "$(shasum -a 256 "$REQUIREMENT_FILE" | awk '{print $1}')" = \
  "$(awk '{print $1}' "$EVIDENCE_ROOT/requirement.sha256")"
```

### 11.2 正式运行

```bash
B_START_EPOCH=$(date +%s)
B_START_UTC=$(date -u '+%Y-%m-%dT%H:%M:%SZ')
B_TIMED_OUT=0

AI4SE_CURSOR_BIN="$CURSOR_BIN" \
java -jar "$RUNTIME_JAR" run \
  --workspace "$ARM_B_REPO" \
  --story json-static-001 \
  --requirement "$REQUIREMENT_FILE" \
  --write-scope src/main/java/org/json/JSONObject.java \
  --write-scope src/test/java/org/json/junit/JSONObjectTest.java \
  --max-dev-rounds 3 \
  --timeout-minutes 10 \
  --model "$MODEL_ID" \
  > "$EVIDENCE_ROOT/arm-b/runtime.stdout.txt" \
  2> "$EVIDENCE_ROOT/arm-b/runtime.stderr.txt" &

B_PID=$!
while kill -0 "$B_PID" 2>/dev/null; do
  if (( $(date +%s) - B_START_EPOCH >= 600 )); then
    B_TIMED_OUT=1
    kill -TERM "$B_PID" 2>/dev/null || true
    break
  fi
  sleep 5
done

wait "$B_PID"
B_RUNTIME_EXIT=$?
B_END_EPOCH=$(date +%s)
B_END_UTC=$(date -u '+%Y-%m-%dT%H:%M:%SZ')

{
  printf 'started_at_utc=%s\n' "$B_START_UTC"
  printf 'ended_at_utc=%s\n' "$B_END_UTC"
  printf 'wall_time_sec=%s\n' "$((B_END_EPOCH - B_START_EPOCH))"
  printf 'runtime_exit_code=%s\n' "$B_RUNTIME_EXIT"
  printf 'timed_out=%s\n' "$B_TIMED_OUT"
  printf 'model_id=%s\n' "$MODEL_ID"
  printf 'human_interventions=0\n'
  printf 'resume_count=0\n'
  printf 'requirement_sha256=%s\n' "$(awk '{print $1}' "$EVIDENCE_ROOT/requirement.sha256")"
} > "$EVIDENCE_ROOT/arm-b/run.properties"
```

不要因为 exit code 非 0 就重跑。先采集状态：

```bash
java -jar "$RUNTIME_JAR" status \
  --workspace "$ARM_B_REPO" \
  --story json-static-001 \
  > "$EVIDENCE_ROOT/arm-b/status.txt" 2>&1
```

### 11.3 唯一允许 resume 的条件

只有非预算原因导致进程被操作系统/终端意外中断，且 ledger 表明存在可恢复的非最终边界时，才执行。若 `timed_out=1`，总预算已经耗尽，禁止 resume。

允许恢复时执行：

```bash
AI4SE_CURSOR_BIN="$CURSOR_BIN" \
java -jar "$RUNTIME_JAR" resume \
  --workspace "$ARM_B_REPO" \
  --story json-static-001 \
  > "$EVIDENCE_ROOT/arm-b/resume-1.stdout.txt" \
  2> "$EVIDENCE_ROOT/arm-b/resume-1.stderr.txt"

printf 'resume_exit_code=%s\n' "$?" \
  > "$EVIDENCE_ROOT/arm-b/resume-1.exit.txt"
```

然后把 `arm-b/run.properties` 中 `resume_count` 改为 1，并写明中断原因。`FAILED_*`、`STOPPED_*`、预算耗尽或测试失败属于有效实验结果，不能用 resume 变相追加预算。

### 11.4 冻结 B 组现场

```bash
cd "$ARM_B_REPO"
git status --short > "$EVIDENCE_ROOT/arm-b/git-status.txt"
git ls-files --others --exclude-standard \
  > "$EVIDENCE_ROOT/arm-b/untracked-files.txt"
{
  git diff --name-only "$BASELINE_COMMIT"..HEAD
  cat "$EVIDENCE_ROOT/arm-b/untracked-files.txt"
} | sort -u > "$EVIDENCE_ROOT/arm-b/changed-files.txt"
git diff --binary "$BASELINE_COMMIT"..HEAD \
  > "$EVIDENCE_ROOT/arm-b/result.patch"
git rev-parse HEAD > "$EVIDENCE_ROOT/arm-b/final-commit.txt"

if [[ -s "$EVIDENCE_ROOT/arm-b/untracked-files.txt" ]]; then
  tar -cf "$EVIDENCE_ROOT/arm-b/untracked-files.tar" \
    -T "$EVIDENCE_ROOT/arm-b/untracked-files.txt"
else
  : > "$EVIDENCE_ROOT/arm-b/untracked-files.tar"
fi

B_TEST_START=$(date +%s)
mvn clean test > "$EVIDENCE_ROOT/arm-b/test.stdout.txt" 2>&1
B_TEST_EXIT=$?
B_TEST_END=$(date +%s)

{
  printf 'exit_code=%s\n' "$B_TEST_EXIT"
  printf 'wall_time_sec=%s\n' "$((B_TEST_END - B_TEST_START))"
} > "$EVIDENCE_ROOT/arm-b/test.exit.txt"
```

完整复制 Story 证据，不挑选、不改写：

```bash
mkdir -p "$EVIDENCE_ROOT/arm-b/story-evidence"
cp -R "$ARM_B_REPO/.story/json-static-001/." \
  "$EVIDENCE_ROOT/arm-b/story-evidence/"
```

### 11.5 人工 Acceptance 审阅

创建 `arm-b/acceptance-review.md`，字段与 A 组相同。审阅必须基于代码、测试和 evidence，不以 runtime 自报 PASS 代替人工判断。

填写三个值供 scorecard 使用：

```bash
export B_MISSED_ACCEPTANCE="0"
export B_DIFF_VERDICT="accept"
export B_HUMAN_INTERVENTIONS="0"
```

如果事实不同，必须改成真实值；不能为了过门槛固定写 0/accept。

### 11.6 运行只读 scorecard

```bash
B_WALL_TIME_SEC="$(awk -F= '/^wall_time_sec=/{print $2}' \
  "$EVIDENCE_ROOT/arm-b/run.properties")"

java -jar "$RUNTIME_JAR" scorecard \
  --workspace "$ARM_B_REPO" \
  --story json-static-001 \
  --arm B \
  --pair-id pair-json-001 \
  --baseline-commit "$BASELINE_COMMIT" \
  --model-id "$MODEL_ID" \
  --input-tokens na \
  --output-tokens na \
  --tool-calls na \
  --human-interventions "$B_HUMAN_INTERVENTIONS" \
  --missed-acceptance "$B_MISSED_ACCEPTANCE" \
  --diff-verdict "$B_DIFF_VERDICT" \
  --wall-time-sec "$B_WALL_TIME_SEC" \
  --notes "pair-json-001 first external repository run" \
  > "$EVIDENCE_ROOT/arm-b/scorecard.txt" 2>&1

SCORECARD_EXIT=$?
printf 'exit_code=%s\n' "$SCORECARD_EXIT" \
  > "$EVIDENCE_ROOT/arm-b/scorecard.exit.txt"

tail -n 2 "$EVIDENCE_ROOT/arm-b/scorecard.txt" \
  > "$EVIDENCE_ROOT/arm-b/scorecard.csv"
```

如果 scorecard 拒绝运行，保留错误，不手工伪造 B 行。

## 12. 阶段九：成对比较

创建 `pair-comparison.md`，只写证据支持的事实：

```markdown
# pair-json-001 Comparison

## Experiment validity

- same baseline: yes|no
- same requirement SHA-256: yes|no
- same model ID: yes|no
- isolated worktrees: yes|no
- independent sessions: yes|no
- no answer/history lookup: yes|no|unknown
- no mid-run human hint: yes|no
- experiment_valid: yes|no

## Outcome table

| Field | Arm A | Arm B |
|---|---:|---:|
| terminal | | |
| wall time sec | | |
| agent/runtime exit | | |
| test exit | | |
| reached awaiting acceptance | n/a | |
| development rounds | na | |
| human interventions | | |
| missed acceptance | | |
| diff verdict | | |
| scope ok | | |
| changed business files | | |
| final commit | | |
| input/output tokens | na | na |
| tool calls | na | na |

## Acceptance comparison

- Instance field populated:
- Static field unchanged:
- Both entry paths tested:
- Regression quality:
- Unrelated behavior/diff:

## Observed process differences

- What useful context did B produce before Development?
- Did B spend work on irrelevant analysis or packages?
- Did Verify catch a real defect and cause another round?
- Did Review add evidence or merely repeat Verify?
- Did A or B inspect/change anything outside scope?
- Which failure/stop was caused by environment, Adapter, Context Package,
  orchestration policy, or implementation reasoning?

Do not write architectural recommendations here. Preserve observations so the
final reviewer can derive recommendations without confirmation bias.
```

## 13. 阶段十：生成最终审阅入口

创建 `REVIEW-REQUEST.md`：

```markdown
# Review Request: pair-json-001

- experiment_valid: yes|no
- evidence_root: <absolute path>
- runtime_commit:
- upstream_tag: 20251224
- upstream_commit:
- baseline_commit:
- requirement_sha256:
- model_id:
- arm_a_final_commit:
- arm_b_final_commit:
- arm_a_test_exit:
- arm_b_test_exit:
- arm_b_terminal:
- arm_b_scorecard_exit:
- arm_b_commit_scope_ok:
- arm_b_verify_pass_before_review:
- human_interventions_a:
- human_interventions_b:
- resumes_b:
- known_protocol_deviations:
- missing_evidence:

## Requested review

1. 判断本次实验是否有效；
2. 判断 A/B 是否真正满足 Acceptance；
3. 审计 B 的事件顺序、commit 范围和恢复行为；
4. 对比 A/B 成本、成功率、diff 质量和人工介入；
5. 将问题分类为 runtime / adapter / context package / verify entry /
   experiment protocol / model reasoning；
6. 给出最小、可验证的优化建议；
7. 判断是否可以进入 pair-json-002。
```

生成证据索引和校验和：

```bash
cd "$EVIDENCE_ROOT"
find . -type f | sort > evidence-files.txt
find . -type f ! -name evidence.sha256 -exec shasum -a 256 {} \; \
  | sort > evidence.sha256
```

不要把客户仓 `.git`、Maven `target/`、依赖缓存或凭证复制进证据包。

## 14. 交给最终审阅者的方式

优先直接提供证据目录的绝对路径：

```text
/Users/peng.lv/IdeaProjects/ai4se-pr4-lab/pair-json-001-evidence
```

如果必须打包，使用只包含上述 evidence tree 的归档，并同时提供 SHA-256。不要只发截图、摘要或 Cursor 的最终回复。

最终审阅顺序固定为：

1. `REVIEW-REQUEST.md`；
2. `manifest.properties`、环境和 baseline；
3. requirement hash；
4. A/B 原始日志、patch、测试；
5. B 的完整 `.story` evidence；
6. scorecard；
7. pair comparison；
8. 最后才形成优化建议。

## 15. 失败时如何处理

| 失败位置 | 处理 | 是否继续 |
|---|---|---|
| runtime 自测失败 | 保留日志，修 runtime 后新建 pair | 否 |
| 上游 baseline 测试失败 | 标记 ENV_BASELINE_FAIL，换仓库/tag | 否 |
| onboarding entry 不真实 | 只修事实配置并重新提交 baseline | 未开始 A/B 时可继续 |
| A 失败 | 冻结现场 | 继续 B |
| B controlled terminal | 冻结现场，不追加提示、不 resume | 继续采分与审阅 |
| B 进程意外中断 | 记录原因后最多 resume | 可继续 |
| B 越界写入 | 不清理，记录安全失败 | 继续审阅，不算通过 |
| scorecard 拒绝 | 保留拒绝原因，禁止手填 B 机器字段 | 继续审阅 |
| 证据缺失/被覆盖 | 标记实验无效，新建 pair | 否 |

## 16. 本组完成判定

只有以下条件全部成立，才算“pair-json-001 流程完成”（不等于 B 获胜）：

- A/B 均从相同 baseline 实际运行；
- 成功或失败现场均被冻结；
- 两组均有原始日志、diff 和独立 `mvn clean test`；
- B 有完整 Story evidence；
- B scorecard 成功，或保存了不可篡改的拒绝证据；
- Acceptance review 和 comparison 已填写真实值；
- `REVIEW-REQUEST.md` 与 evidence checksum 已生成；
- 未 push、未查看公开答案、未覆盖失败结果。

完成后停止，不立即扩写 runtime，也不让 Cursor自行“顺手优化”。先提交证据给审阅者，由真实结果决定下一项最小改进。
