# M1 PR4 真仓实验操作手册：JSON-java / pair-json-002

> 目的：在 pair-json-001 证据链已证明无人值守闭环可走通之后，用**第二个、不同的**历史 Story 继续 A/B 冒烟。
> 本文是操作协议，不是答案提示。不得查阅该历史缺陷后续的 GitHub Issue、PR、commit 或 release diff。
>
> **暂停状态：** 已解除（`74a0da8` Review fail-closed 审阅通过；attempt5 B-only 精确 `decision=PASS` 证据仍有效）。
> **隔离纪律：** 严禁复用 attempt2–5 的 worktree、Story ID、模型会话或证据目录。必须使用下方全新 `LAB_ROOT`。
>
> **命题隔离（硬性）：** pair-json-001 中所有 `STATIC_VALUE`、static field、reflection、`fromJson` 相关的
> Acceptance review 字段、comparison 字段和人工检查说明均**不适用于**本实验，**不得**复制进 pair-json-002 证据。
> A/B 的 `acceptance-review.md` 与 `pair-comparison.md` **必须**使用本文 §9 的专用字段。

## 0. 本对回答什么问题

`pair-json-002`（`json-pointer-space-002`）检验：JSON Pointer URI fragment 是否将空格编码为 `%20`（而非 `+`），并在另一条低到中风险 Story 上完成 A/B 冒烟与 scorecard 审计。

单对成功仍不能签收最终 PR4（需凑满 10×A/B 门槛）。

## 1. 强制纪律（相对 pair-001 的增量）

- **新 LAB_ROOT**，不得指向 attempt2 / 3 / 4 / 5。
- **pair_id=`pair-json-002`**，**story_id=`json-pointer-space-002`**。
- **不同 Story**：JSON Pointer URI-fragment 空格/`+` 编码；禁止 static-field / `STATIC_VALUE` 命题。
- A/B 同 `baseline_commit`、同 requirement SHA-256、同 `model_id`、独立 worktree、独立 Cursor 会话。
- 整组 ≤10 分钟硬截止；B ≤3 Development 轮次；不 push；不查 `20251224` 之后答案。
- entry 与 AC 对齐：正式 test entry = `mvn clean test`。
- wrapper：`set +e` → `wait` → 捕获 exit → `set -e`。
- Review：sidecar 仅 `PASS|CONDITIONAL|REJECT`；仅 PASS 自动 Delivery。
- **requirement.md 冻结后只读**：启动 A/B 前 `chmod a-w`；每次开跑前用 `requirement.sha256` 复核；禁止改写正文。

## 2. 仓库与 ID

| 项 | 值 |
|---|---|
| 上游 | `https://github.com/stleary/JSON-java.git` |
| 固定 tag | `20251224` |
| LAB_ROOT | `/Users/peng.lv/IdeaProjects/ai4se-pr4-lab-pair002` |
| Pair ID | `pair-json-002` |
| Story ID | `json-pointer-space-002` |
| Allowed files | `src/main/java/org/json/JSONPointer.java`；`src/test/java/org/json/junit/JSONPointerTest.java` |
| runtime | 当前放行 HEAD（至少含 `74a0da8`） |
| model_id | `composer-2.5`（与 pair-001 对照固定） |

## 3. Story 状态

Story 正文已冻结于证据包 `requirement.md`（见 `STORY-SELECTION.md`）。

开跑前确认：

1. Acceptance 为 bullet 列表，字符串断言精确。
2. Allowed files 仅上述两文件。
3. 与 pair-001 命题不同。
4. 未查阅 `20251224` 之后修复。
5. `shasum -a 256 requirement.md` 与 `requirement.sha256` 一致。

## 4. 证据树

```text
pair-json-002-evidence/
├── README.md
├── STORY-SELECTION.md
├── manifest.properties
├── environment.txt
├── upstream-provenance.txt
├── requirement.md              # 冻结只读
├── requirement.sha256
├── baseline/ …
├── arm-a/
│   └── acceptance-review.md    # 必须用 §9 字段
├── arm-b/
│   └── acceptance-review.md    # 必须用 §9 字段
├── pair-comparison.md          # 必须用 §9 字段
└── REVIEW-REQUEST.md
```

保留旁路证据（只读，勿覆盖）：attempt2–5 路径见 manifest。

## 5. 阶段一：配置目录

```bash
export AI4SE_ROOT="/Users/peng.lv/IdeaProjects/ai4se-runtime"
export LAB_ROOT="/Users/peng.lv/IdeaProjects/ai4se-pr4-lab-pair002"
export MODEL_ID="composer-2.5"
export CURSOR_BIN="/Applications/Cursor.app/Contents/Resources/app/bin/cursor"

export SOURCE_REPO="$LAB_ROOT/json-java-source"
export ARM_A_REPO="$LAB_ROOT/json-java-pair002-a"
export ARM_B_REPO="$LAB_ROOT/json-java-pair002-b"
export EVIDENCE_ROOT="$LAB_ROOT/pair-json-002-evidence"
export REQUIREMENT_FILE="$EVIDENCE_ROOT/requirement.md"
export RUNTIME_JAR="$AI4SE_ROOT/ai4se-demo/target/ai4se-runtime.jar"
export TIMED_CAPTURE="$AI4SE_ROOT/scripts/pr4-timed-capture.sh"
export STORY_ID="json-pointer-space-002"

test -d "$AI4SE_ROOT/.git"
test -n "$MODEL_ID"
test -x "$CURSOR_BIN"
test -f "$REQUIREMENT_FILE"
test "$(shasum -a 256 "$REQUIREMENT_FILE" | awk '{print $1}')" = \
  "$(awk '{print $1}' "$EVIDENCE_ROOT/requirement.sha256")"
```

若需新建 clone/worktree：`test ! -e "$SOURCE_REPO"` 等；**不得**删除已有 attempt2–5。

## 6. 后续阶段（过程步骤继承；验收字段不继承）

克隆 `20251224`、baseline `mvn clean test`、onboard（entries 仅 Maven + `mvn clean test`）、共同 baseline commit、A/B worktree、A 裸 Cursor、B `ai4se-runtime.jar run`、冻结证据、scorecard —— **过程步骤**可参照 [m1-pr4-json-java-pair001-runbook.md](./m1-pr4-json-java-pair001-runbook.md) §5 起的命令骨架，但必须替换：

- `pair_id` / `story_id` / 路径 / write-scope / requirement
- **全部** Acceptance review / comparison / 人工检查字段 → 本文 §9

**禁止**从 pair-001 证据或 runbook 复制下列字段或检查说明：

- `instance_field_is_populated`
- `static_field_retains_original_value`
- `both_public_entry_paths_covered`
- 任何 `STATIC_VALUE` / `staticValue` / static field / reflection / `fromJson` 相关说明

B 启动：

```bash
AI4SE_CURSOR_BIN="$CURSOR_BIN" \
java -jar "$RUNTIME_JAR" run \
  --workspace "$ARM_B_REPO" \
  --story json-pointer-space-002 \
  --requirement "$REQUIREMENT_FILE" \
  --write-scope src/main/java/org/json/JSONPointer.java \
  --write-scope src/test/java/org/json/junit/JSONPointerTest.java \
  --max-dev-rounds 3 \
  --timeout-minutes 10 \
  --model "$MODEL_ID" \
  > "$EVIDENCE_ROOT/arm-b/runtime.stdout.txt" \
  2> "$EVIDENCE_ROOT/arm-b/runtime.stderr.txt" &
```

开跑前再次锁定 requirement：

```bash
chmod a-w "$REQUIREMENT_FILE"
test "$(shasum -a 256 "$REQUIREMENT_FILE" | awk '{print $1}')" = \
  "$(awk '{print $1}' "$EVIDENCE_ROOT/requirement.sha256")"
```

## 7. 放行本对的最低条件

- `experiment_valid: yes`
- A/B 同 baseline / requirement / model
- B：`Review` sidecar `decision=PASS`（或诚实非 PASS 终态 + `run_settled=1`）
- 若声称成功 Delivery：commit 存在且 `commit_scope_ok=1`
- ledger / workflow 终态一致
- 未覆盖 attempt2–5
- acceptance-review / pair-comparison **仅**含 §9 字段（无 pair-001 static 字段）

## 8. 交接

证据根：

```text
/Users/peng.lv/IdeaProjects/ai4se-pr4-lab-pair002/pair-json-002-evidence
```

审阅入口：`REVIEW-REQUEST.md`。不自动启动下一条 Story。

## 9. pair-json-002 专用 Acceptance 审阅模板

> **硬性说明：** pair-json-001 中所有 STATIC_VALUE、static field、reflection 相关的 Acceptance review 字段、comparison 字段和人工检查说明均不适用于本实验，不得复制进 pair-json-002 证据。A、B 两组的 `acceptance-review.md` 和最终 `pair-comparison.md` 都使用下列字段。

### 9.1 `arm-a/acceptance-review.md` 与 `arm-b/acceptance-review.md`

```markdown
# Arm <A|B> Acceptance Review

- reviewer:
- reviewed_at_utc:
- agent_exit_code:          # Arm B: runtime_exit_code
- test_exit_code:
- changed_files_only_allowed: yes|no
- space_encoded_as_percent20: yes|no|not_proven
- literal_plus_encoded_as_percent2b: yes|no|not_proven
- round_trip_preserves_space_and_plus: yes|no|not_proven
- existing_fragment_cases_preserved: yes|no|not_proven
- clean_test_passed: yes|no
- missed_acceptance_count:
- diff_verdict: accept|minor_fix|reject
- evidence_notes:
```

判定提示（事实，不写推测）：

- `space_encoded_as_percent20`：`new JSONPointer("/a b").toURIFragment()` 是否**精确**为 `#/a%20b`（不得为 `#/a+b`）。
- `literal_plus_encoded_as_percent2b`：`new JSONPointer("/a+b").toURIFragment()` 是否**精确**为 `#/a%2Bb`。
- `round_trip_preserves_space_and_plus`：对 `/a b+c`，`toURIFragment()` → `new JSONPointer(fragment)` → `toString()` 是否**精确**得到 `/a b+c`。
- `existing_fragment_cases_preserved`：`%` / `^` / `|` / `~` 相关既有用例是否仍通过（不得以「测试绿了」代替；需有 diff/测试证据或明确 not_proven）。
- `clean_test_passed`：冻结时 `mvn clean test` exit code 是否为 0。

任一精确字符串断言未证明时，对应字段写 `not_proven` 并增加 `missed_acceptance_count`，不得因 suite 全绿写 `accept`。

### 9.2 `arm-a/metrics.properties` / Arm B scorecard notes

```properties
pair_id=pair-json-002
story_id=json-pointer-space-002
arm=<A|B>
# … 其余通用字段同实验协议；notes 只写 pointer fragment 事实，禁止 static/STATIC_VALUE 用语
```

### 9.3 `pair-comparison.md` Acceptance 段（替换 pair-001 对照表）

```markdown
## Acceptance comparison

- Space → `%20` (`#/a%20b`):
- Literal `+` → `%2B` (`#/a%2Bb`):
- Round-trip `/a b+c`:
- Existing `%` / `^` / `|` / `~` cases:
- `mvn clean test`:
- Unrelated behavior / scope:
```

不得出现 instance/static field / `STATIC_VALUE` / `fromJson` 对照行。
