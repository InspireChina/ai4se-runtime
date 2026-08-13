# M1 PR4 真仓实验操作手册：JSON-java / pair-json-002

> 目的：在 pair-json-001 证据链已证明无人值守闭环可走通之后，用**第二个、不同的**历史 Story 继续 A/B 冒烟。
> 本文是操作协议，不是答案提示。不得查阅该历史缺陷后续的 GitHub Issue、PR、commit 或 release diff。
>
> **暂停状态：** 已解除（`74a0da8` Review fail-closed 审阅通过；attempt5 B-only 精确 `decision=PASS` 证据仍有效）。
> **隔离纪律：** 严禁复用 attempt2–5 的 worktree、Story ID、模型会话或证据目录。必须使用下方全新 `LAB_ROOT`。

## 0. 本对回答什么问题

`pair-json-002` 检验：在**另一条**低到中风险 Acceptance 明确、write-scope 可覆盖的 Story 上，runtime 是否仍能完成：

1. onboard → Analysis → Planning → Development ↔ Verification → Review PASS → Delivery；
2. 与裸 Cursor（A）同基线、同模型对照；
3. scorecard 独立审计。

单对成功仍不能签收最终 PR4（需凑满 10×A/B 门槛）。

## 1. 强制纪律（相对 pair-001 的增量）

- **新 LAB_ROOT**，不得指向 attempt2 / 3 / 4 / 5。
- **新 pair_id / story_id**，不得复用 `pair-json-001*` / `json-static-001*`。
- **不同 Story**：不得再跑 static-field / `STATIC_VALUE` 同一验收命题。
- A/B 同 `baseline_commit`、同 requirement SHA-256、同 `model_id`、独立 worktree、独立 Cursor 会话。
- 整组 ≤10 分钟硬截止；B ≤3 Development 轮次；不 push；不查 `20251224` 之后答案。
- entry 与 AC 对齐：正式 test entry = `mvn clean test`（见 pair-001 协议修正）。
- wrapper：`set +e` → `wait` → 捕获 exit → `set -e`，禁止非零 exit 提前终止导致 `run.properties` 事后重建。
- Review：sidecar 仅 `PASS|CONDITIONAL|REJECT`；仅 PASS 自动 Delivery。

## 2. 仓库与 ID

| 项 | 值 |
|---|---|
| 上游 | `https://github.com/stleary/JSON-java.git` |
| 固定 tag | `20251224` |
| LAB_ROOT | `/Users/peng.lv/IdeaProjects/ai4se-pr4-lab-pair002` |
| Pair ID | `pair-json-002` |
| Story ID | **选定 Story 后写入**（建议 `json-<topic>-002`） |
| runtime | 当前放行 HEAD（至少含 `74a0da8`） |
| model_id | 与 pair-001 对照时固定同一 ID（当前实验常用 `composer-2.5`） |

## 3. Story 选定门禁（开跑前必须满足）

在写入 `requirement.md` 并启动 A/B 之前，操作者必须确认：

1. Acceptance 可判定（bullet / 编号列表；跨行约束用缩进续行）。
2. Allowed files ⊆ 可写、低到中风险（建议仍限 1–2 个 Java 源文件 + 对应测试）。
3. 与 pair-001 **功能命题不同**（不得再以「fromJson 忽略 static」为主题）。
4. 未查阅 `20251224` 之后的修复 commit / Issue / PR / 网页答案。
5. 客户正式 test entry 定为 `mvn clean test`，并与 AC 中的验证命令一致。

选定后在本文件 §2 与证据 `manifest.properties` 填入最终 `story_id`，并冻结 requirement SHA-256。

> **当前状态：** Story 正文尚未冻结。LAB 与证据目录已建好；**在 Story 选定并写入 requirement 之前禁止启动 A/B。**

## 4. 证据树

```text
pair-json-002-evidence/
├── README.md
├── STORY-SELECTION.md          # 选定记录（命题、allowed files、为何不同于 pair-001）
├── manifest.properties
├── environment.txt
├── upstream-provenance.txt
├── requirement.md              # Story 选定后写入
├── requirement.sha256
├── baseline/ …
├── arm-a/ …
├── arm-b/ …
├── pair-comparison.md
└── REVIEW-REQUEST.md
```

保留旁路证据（只读，勿覆盖）：

- attempt2: `…/ai4se-pr4-lab-attempt2/pair-json-001-evidence`
- attempt3: `…/ai4se-pr4-lab-attempt3/pair-json-001-r2-evidence`
- attempt4: `…/ai4se-pr4-lab-attempt4/pair-json-001-r2-evidence`
- attempt5: `…/ai4se-pr4-lab-attempt5-b-only/pair-json-001-r2-bonly-evidence`

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

test ! -e "$SOURCE_REPO"
test ! -e "$ARM_A_REPO"
test ! -e "$ARM_B_REPO"
# EVIDENCE_ROOT 可由脚手架预先创建；但不得已有 A/B 运行产物
test -d "$AI4SE_ROOT/.git"
test -n "$MODEL_ID"
test -x "$CURSOR_BIN"
```

若 `LAB_ROOT` 已有需保留的旧实验数据：**停止并换新路径**，不要删除。

## 6. 后续阶段

克隆 `20251224`、baseline `mvn clean test`、onboard（entries 仅 Maven + `mvn clean test`）、共同 baseline commit、A/B worktree、requirement 冻结、A 裸 Cursor、B `ai4se-runtime.jar run`、冻结证据、scorecard、`pair-comparison.md`、`REVIEW-REQUEST.md` —— **步骤与纪律同** [m1-pr4-json-java-pair001-runbook.md](./m1-pr4-json-java-pair001-runbook.md) §5 起，仅替换 ID / 路径 / requirement。

B 启动示例：

```bash
AI4SE_CURSOR_BIN="$CURSOR_BIN" \
java -jar "$RUNTIME_JAR" run \
  --workspace "$ARM_B_REPO" \
  --story "<story_id>" \
  --requirement "$REQUIREMENT_FILE" \
  --write-scope <allowed-1> \
  --write-scope <allowed-2> \
  --max-dev-rounds 3 \
  --timeout-minutes 10 \
  --model "$MODEL_ID" \
  > "$EVIDENCE_ROOT/arm-b/runtime.stdout.txt" \
  2> "$EVIDENCE_ROOT/arm-b/runtime.stderr.txt" &
```

## 7. 放行本对的最低条件

- `experiment_valid: yes`
- A/B 同 baseline / requirement / model
- B：`Review` sidecar `decision=PASS`（或诚实非 PASS 终态 + `run_settled=1`）
- 若声称成功 Delivery：commit 存在且 `commit_scope_ok=1`
- ledger / workflow 终态一致
- 未覆盖 attempt2–5

## 8. 交接

证据根：

```text
/Users/peng.lv/IdeaProjects/ai4se-pr4-lab-pair002/pair-json-002-evidence
```

审阅入口：`REVIEW-REQUEST.md`。
