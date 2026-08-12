# M1 PR4 · 真实 Story 签收 + Context Engineering A/B

> 本批**暂停功能扩写**。只做真实验证、记分与被真实证据触发的修复。
> 计划真源：[m1-production-loop-execution-plan.md](./m1-production-loop-execution-plan.md) §PR4。

## 1. 目标

在同一 Java/Maven 技术栈上，用 **10 个历史、低到中风险 Story** 对比：

| 组 | 做法 |
|---|---|
| **A** | Cursor agent 仅获 requirement + repo，自主探索（不用 AI4SE 分阶段 Package 主链） |
| **B** | 相同模型/预算上限，走正式 `ai4se-runtime.jar run`（Context Package + BoundedDeliveryLoop） |

M1 签收不是「单测绿」，而是留下可复跑证据包（见计划 §9）。

## 2. Story 选取纪律

纳入：

- Acceptance 明确、客户 `entries.yaml` 可执行。
- 改动面可被 `--write-scope` 覆盖。
- 低到中风险业务改动。

排除：

- 数据库迁移、生产凭证、跨仓大重构、需多人协调的需求。

每个 Story 在客户仓外维护一份脱敏 requirement（本仓**不落客户正文**）；本仓只记 story id、日期、记分行。

## 3. 运行协议

### 3.1 公共前置

```bash
./scripts/onboard-repo.sh /path/to/customer-repo   # 若尚未 onboard
# 工作树必须干净；entries 至少一条可用 test
```

预算约定（A/B 必须一致，记入同一 `pair_id`）：

- 同一 `baseline_commit`
- 同一 Cursor `model_id`（或同一 role-models 配置）
- 同一 wall-time / max Development 轮次上限（B 用 `--max-dev-rounds`；A 用人工程序等价约束）
- 同一 write-scope 列表

### 3.2 B 组（AI4SE）

```bash
java -jar ai4se-demo/target/ai4se-runtime.jar run \
  --workspace /path/to/customer-repo \
  --story story-NNN \
  --requirement /path/to/seed.md \
  --write-scope src/main/java \
  --write-scope src/test/java \
  --max-dev-rounds 3

# 进程中断后：
java -jar ai4se-demo/target/ai4se-runtime.jar resume \
  --workspace /path/to/customer-repo \
  --story story-NNN

# 跑完采集（只读；缺 run/state 或 events 会拒跑，不会创建目录）：
java -jar ai4se-demo/target/ai4se-runtime.jar scorecard \
  --workspace /path/to/customer-repo \
  --story story-NNN \
  --arm B \
  --pair-id pair-001 \
  --baseline-commit <sha> \
  --model-id <model> \
  --input-tokens na \
  --output-tokens na \
  --tool-calls na \
  --human-interventions 0 \
  --diff-verdict accept \
  --wall-time-sec 420
```

证据宿主（客户仓）：

- `.story/<id>/run/{state.properties,events.jsonl,failure-fingerprint}`
- `.story/<id>/packages/**`
- `.story/<id>/pathway-evidence/**`（若写出）
- Delivery `delivery.md` commit SHA（禁止 push）

### 3.3 A 组（裸 Cursor）

1. 打开同一仓库干净 worktree / 同 `baseline_commit`。
2. 只提供 requirement 文本与「允许改动路径」说明；**不**注入 AI4SE Analysis/Plan/Dev/Review Package。
3. 人工或脚本约束同等轮次/时间预算与同一 `model_id`。
4. 结束后填写记分板 A 行（同一 `pair_id`）；取不到的 token/tool-call 显式写 `na`。

## 4. 每个 Story 必记字段

机器可采（B / `scorecard`，独立核验优先于事件关键字）：

- 实验条件：`pair_id`、`baseline_commit`、`model_id`、`write_scope`（ledger）
- 终态：`terminal` / exit / `awaiting_acceptance`
- 轮次：`rounds_used` / `max_dev_rounds` / `last_round_outcome`
- Package：总字节、`p1_bytes` / `p2_bytes`（启发式）
- 安全：`commit_exists`、`commit_scope_ok`（`baseline` 须为 Delivery 祖先；审计 `baseline..delivery` 全路径 ⊆ write_scope）、`verify_pass_before_review`（诚实链：`VERIFY_PASS` seq < `VERIFICATION stage_completed` seq < Review/Delivery/awaiting；报告 `outcome: PASS` 严格行解析）
- 事件关键字计数 `write_scope_violation_events` 仅作旁证，**以 `commit_scope_ok` 为准**
- CLI `scorecard` 仅采 **arm B**（拒 `--arm A`）；B 必填 `pair-id` / `baseline-commit` / `model-id`；计数非负或 `na`；`diff-verdict` ∈ accept|minor_fix|reject

人工 / 外采（取不到写 `na`）：

- `input_tokens` / `output_tokens` / `tool_calls`
- 中途人工介入次数、漏掉 Acceptance、diff 判定、wall time

模板：[`m1-pr4-scorecard.csv`](./m1-pr4-scorecard.csv)

## 5. M1 签收门槛（不得放宽）

- 10 个 Story 中至少 **7** 个无需中途人工聊天，到达 awaiting acceptance。
- **0** 次越出 write scope 并成功 commit（看 `commit_scope_ok=1` 且 awaiting）。
- **0** 次测试未通过却进入 Review/Commit（看 `verify_pass_before_review=1`）。
- 所有停止都有机器终态 + 可复查证据。
- B 相对 A 至少在「成功率、token、工具调用、人工修正量」中有 **两项**明确改善；否则不得继续扩建 Context 域，先调 Package。

任一项出现 `fixture` / `seeded` / `FunctionalModelCliAdapter` / runner 代写业务产物 → **不得**作为 M1 签收证据。

## 6. 本仓交付（PR4 实验工具）

| 交付物 | 说明 |
|--------|------|
| 本 playbook | 实验纪律与命令 |
| `m1-pr4-scorecard.csv` | 完整表头（禁止伪造结果行） |
| `ProductionRunScorecard` | 只读采集：`openExisting`、storyId 单段校验、`baseline..delivery` scope、events 序核验 |
| CLI `scorecard` | arm B only；打印 human summary + CSV 行 |
| 单测 | 路径逃逸拒收、缺 story 不建目录、中间越界 commit→scope_ok=0、Review 后补 PASS→verify=0、VERIFICATION 先完成但 PASS 在 Review 后→verify=0 |

**放行口径：** 本批最多放行「PR4 实验工具」。**真实 10×A/B 结果 + 门槛判定**仍是最终 PR4 签收条件，不得在本仓伪造。

## 7. 被真实证据触发的修复

仅当某次真实 B 跑出现可复现缺陷时，才开修复提交；修复必须附带：

- Story id / 终态 / events 片段
- 复现命令
- 回归测试（优先加在 orchestration）

禁止借 PR4 之名新增 Scheduler、多 Story 队列、sandbox、Knowledge 自动晋升。

## 8. 第一组外部真仓操作手册

`pair-json-001` 使用 JSON-java 固定历史 tag，包含从浅克隆、onboarding、A/B worktree、真实运行、证据冻结、scorecard 到最终审阅交接的完整步骤：

- [m1-pr4-json-java-pair001-runbook.md](./m1-pr4-json-java-pair001-runbook.md)

该手册的 evidence tree 是第一组实验的过程真源；不得用聊天摘要或截图替代原始日志、patch、测试结果和 `.story` 证据。
