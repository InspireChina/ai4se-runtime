# 客户真仓单卡使用手册 v1

本手册对应 Runtime 的实际入口，不要求人手写 `.story` 下的 Gap、答案、Plan 或 Approval 文件。

## 边界

- Runtime 是客户仓交付控制面，不是另一个聊天 Agent；模型只执行被控制面提交的阶段 Work Order。
- 人只做四类决策：提供需求/附件、回答 Analysis 问题、批准 Plan、最终业务验收。
- Development → Verification → 缺陷修复（最多三轮）→ Review → 本地 commit 由 Runtime 受控执行；绝不 push。
- 每次正式启动使用一个干净的客户 worktree。`resume` 使用同一 Story 的持久化状态，不能拿到另一张卡重跑。

## 首次准备（每个客户仓一次）

先建立仓库槽位和事实基线：

```bash
java -jar "$AI4SE_JAR" onboard \
  --workspace /absolute/customer-worktree \
  --runtime-root /Users/peng.lv/IdeaProjects/ai4se-runtime
```

`onboard` 只建立可审计槽位和可观察事实，不会凭空生成业务知识。随后必须实际验证并修正 `.ai4se/repository/entries.yaml`、`.ai4se/index/knowledge.yaml` 和知识文档；不能以空模板冒充。这个前置是为了避免模型在未知项目中猜构建入口、模块边界或编码规范。

建议先在 Runtime 仓构建发行 jar：

```bash
mvn -pl ai4se-demo -am package
```

将输出 jar 的绝对路径记为 `AI4SE_JAR`。要求本机至少注册一个受控 CLI adapter（`cursor`、`codex` 或 `claude`）并可在客户 worktree 中工作。

## 单张需求卡

1. 将需求写成 Markdown（含 3–5 条可验证 AC；附件写入需求声明的路径），准备好限定的写入范围。
2. 启动：

```bash
java -jar "$AI4SE_JAR" run --interactive \
  --workspace /absolute/customer-worktree \
  --story customer-order-action-001 \
  --requirement /absolute/customer-order-action-001.md \
  --write-scope litemall-admin-api/src/main/java \
  --write-scope litemall-admin-api/src/test/java \
  --adapter codex \
  --max-dev-rounds 3
```

3. 若 `status` 显示 `STOPPED_NEEDS_CLARIFICATION`，阅读：

```bash
java -jar "$AI4SE_JAR" status --workspace /absolute/customer-worktree --story customer-order-action-001
# .story/customer-order-action-001/analysis/clarification.questions.md
```

回答后不要手改 Gap；记录答案并重启：

```bash
printf '%s\n' '业务确认：退款按钮仅在已支付且未发货时显示。' > /tmp/customer-order-action-001.answer.md
java -jar "$AI4SE_JAR" resume \
  --workspace /absolute/customer-worktree \
  --story customer-order-action-001 \
  --answers /tmp/customer-order-action-001.answer.md \
  --adapter codex
```

这次 resume 会把原问题和人的答案作为 Analysis P1 输入重新判定；它不会由控制面直接把 Gap 改成 CLEAR。若仍有真实歧义，会生成新的问题并再次停止。

4. Analysis CLEAR 后，Planning 会生成这些可审阅制品并停在计划批准：

```text
.story/<story>/planning/plan.md
.story/<story>/planning/effective-constraints.md
.story/<story>/planning/effective-constraints.properties
```

确认设计、Allowed Files、测试策略和约束后：

```bash
java -jar "$AI4SE_JAR" resume \
  --workspace /absolute/customer-worktree \
  --story customer-order-action-001 \
  --approve-plan \
  --approval-note '已确认范围与验收方式' \
  --adapter codex
```

5. 此后 Runtime 自动执行受限开发、独立 probes、客户声明的验证入口、最多三轮 Defect 回环、Review 和本地 commit。完成后阅读：

```text
.story/<story>/verification/
.story/<story>/defects/
.story/<story>/review/
.story/<story>/delivery/delivery-report.md
```

只有 `terminal=AWAITING_HUMAN_ACCEPTANCE`、所有 AC probes 为 `PROVEN`、Review 为 `PASS` 且存在本地 commit，才进入业务人工验收。`CONDITIONAL`、`REJECT`、范围越界、环境失败或轮次耗尽均是诚实停止，不自动重试。
