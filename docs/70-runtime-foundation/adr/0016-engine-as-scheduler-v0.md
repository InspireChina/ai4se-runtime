# ADR-0016 · Engine-as-Scheduler-v0（契约对齐）

- Status: **Accepted**
- Date: 2026-07-30
- Tags: s0, engine, scheduler-deferred, checkpoint, honesty
- 客户运行手册：`docs/90-status/m1-real-customer-story-runbook-v1.md`

## Context

蓝图里有 Scheduler / WorkItem 队列；仓库现状是 **Runtime Engine 串行推进单 Task**（Demo 另有串行 Delivery，≠ StageRunner）。若不写清，会出现：

1. 对外口头「有 Scheduler」但代码没有；
2. Checkpoint 被误读成「已可 Resume」；
3. Demo/Analysis 的 Stop / Clarification 被误标成 Runtime S4 人闸。

S0 只对齐契约，**不实现** Scheduler 模块。

## Decision

### 1) v0 推进者

- **由 Runtime Engine 推进 Task 状态机**（submit → Worker → Artifact/Checkpoint → Result）。
- **Scheduler / WorkItem 队列：Deferred（延后）**，直至出现可复现的多步并发痛点并满足手册 §1.3 解锁条件。
- Demo 层 `SerialDeliveryRunner` 是 **交付编排旁路**，不是 Engine 内 Scheduler，不得称为 Scheduler 完成。

### 2) v0 支持的 Task 路径（Supported path）

实现上常见成功路径（子集，非枚举全状态机承诺）：

```text
CREATED → VALIDATING → QUEUED → SCHEDULED → STARTING → RUNNING → SUCCEEDED
                                                         ↘ FAILED / CANCELLED
```

状态机枚举中还存在（如 `WAITING_DEPENDENCY`、`RETRY_WAIT`、`CHECKPOINTING`、`BLOCKED_POLICY` 等），**v0 不宣称产品级人机等待闭环已通**。尤其：

| 能力 | v0 状态 |
|------|---------|
| ShellWorker / FileEditWorker 窄通路 | ✅ 有（S2） |
| RuntimeResult 含 checkpointId 等 | ✅ 部分（S3） |
| Checkpoint **写入** | ✅ 有 |
| Checkpoint **Resume / 恢复执行** | ❌ 未做（见下） |
| Task 级 Human-Wait / 澄清阻塞在 Engine 内 | ✅ 最小（BLOCKED_POLICY + clarify/approve Artifact API） |
| 独立 Scheduler 服务 | ❌ 未做且本 ADR 明确延后 |

### 3) Checkpoint 语义（写死）

- **v0 = write-only until Resume milestone**：成功路径可写 Checkpoint，供观测与后续恢复阶使用。
- **不得**因「能写 Checkpoint」宣称「已支持 Resume / 断点续跑」。
- Resume 另开台阶；未解锁前禁止实现 Resume 引擎。

### 4) 定位不变

- 继续遵守 ADR-0007：**工程 Runtime ≠ Coding Agent**。
- 本 ADR **不引入**新 Domain 对象；不改 Frozen Invariants 正文。

## 门禁自检（S0）

| 门禁 | 结果 |
|------|------|
| 是否宣称实现了未实现的 Scheduler？ | **否** |
| Checkpoint 是否写明「可写未 Resume」？ | **是** |
| 是否仍指向「工程 Runtime 非 Agent」？ | **是**（ADR-0007） |

## Consequences

### Positive

- 水位表 S0 可关闭；口头与文档对齐，减少假完成。
- S4 / Resume / Scheduler 有明确「未做」边界，验证通路不得偷标。

### Negative

- 仍无独立调度器；多 Task 并发不在 v0 范围。
- 读者须同时看本 ADR 与建造手册水位，不能只看状态机枚举「有名字」当已实现。

### Follow-ups

- ~~下一真缺口默认：S4 Runtime 人闸~~ **已做最小版**（2026-07-30）。
- 下一默认建议：**S5 阶段门禁**（Engine 内缺 Artifact 拒下一阶段）。
- Resume 仅在 Checkpoint write 稳定且有可复现恢复痛点后解锁。
