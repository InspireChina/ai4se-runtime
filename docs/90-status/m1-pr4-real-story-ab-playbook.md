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

预算约定（A/B 必须一致）：

- 同一 Cursor 模型 id（或同一 role-models 配置）。
- 同一 wall-time / max Development 轮次上限（B 用 `--max-dev-rounds`；A 用人工程序等价约束）。
- 同一 write-scope 列表。

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

# 跑完采集机器字段：
java -jar ai4se-demo/target/ai4se-runtime.jar scorecard \
  --workspace /path/to/customer-repo \
  --story story-NNN \
  --arm B
```

证据宿主（客户仓）：

- `.story/<id>/run/{state.properties,events.jsonl,failure-fingerprint}`
- `.story/<id>/packages/**`
- `.story/<id>/pathway-evidence/**`（若写出）
- Delivery `delivery.md` commit SHA（禁止 push）

### 3.3 A 组（裸 Cursor）

1. 打开同一仓库干净 worktree / 同 commit 基线。
2. 只提供 requirement 文本与「允许改动路径」说明；**不**注入 AI4SE Analysis/Plan/Dev/Review Package。
3. 人工或脚本约束同等轮次/时间预算。
4. 结束后人工填写记分板 A 行（机器字段可空）；若产生 commit，记录 SHA 与是否越出 write-scope。

## 4. 每个 Story 必记字段

机器可采（B / `scorecard`）：

- 是否 `AWAITING_HUMAN_ACCEPTANCE`（exit 0）
- `rounds_used` / `max_dev_rounds` / `last_round_outcome`
- Verify report 轮数、Dev Package 数、Adapter audit 数
- Package 总字节
- commit SHA
- events 中疑似 write-scope 违规计数

人工必填：

- 中途人工聊天/介入次数与原因
- 漏掉 Acceptance 条目数
- 最终 diff：接受 / 需小修 / 拒绝
- wall time（秒）
- （能取则取）token、tool call；取不到写 `na`

模板：[`m1-pr4-scorecard.csv`](./m1-pr4-scorecard.csv)

## 5. M1 签收阈值（不得放宽）

- 10 个 Story 中至少 **7** 个无需中途人工聊天，到达 awaiting acceptance。
- **0** 次越出 write scope 并成功 commit。
- **0** 次测试未通过却进入 Review/Commit。
- 所有停止都有机器终态 + 可复查证据。
- B 相对 A 至少在「成功率、token、工具调用、人工修正量」中有 **两项**明确改善；否则不得继续扩建 Context 域，先调 Package。

任一项出现 `fixture` / `seeded` / `FunctionalModelCliAdapter` / runner 代写业务产物 → **不得**作为 M1 签收证据。

## 6. 本仓交付（PR4 代码批）

| 交付物 | 说明 |
|--------|------|
| 本 playbook | 实验纪律与命令 |
| `m1-pr4-scorecard.csv` | 记分表头 + 空行占位 |
| `ProductionRunScorecard` | 只读采集 |
| CLI `scorecard` | 打印 human summary + CSV 行 |
| 单测 | 采集器不依赖真实 Cursor |

**真实 10×A/B 结果不在本批伪造。** 结果应写回客户侧证据目录或本文件的后续附录（脱敏），并更新 `current-support-status.md` 的 PR4 水位。

## 7. 被真实证据触发的修复

仅当某次真实 B 跑出现可复现缺陷时，才开修复提交；修复必须附带：

- Story id / 终态 / events 片段
- 复现命令
- 回归测试（优先加在 orchestration）

禁止借 PR4 之名新增 Scheduler、多 Story 队列、sandbox、Knowledge 自动晋升。
