# Sprint-6 Design Note — Walking Skeleton（最小可运行 Runtime）

> 工程 Design Note（非 Frozen Architecture）。证明：**现有 Runtime Kernel 足以驱动完整软件工程生命周期**。

## 1. Goal

不新增 Domain Object / 架构层 / 外部 CLI。仅证明：

```text
Task → Validate → Bind ExecutionContext → Start Trace
  → NoopWorker → Artifact(hello.txt) → Commit
  → Checkpoint → Task Success
```

## 2. Mapping to existing Kernel

| 步骤 | Kernel / Engine | 实现 |
|------|-----------------|------|
| Task create + Validate | Task + TaskLifecycleService | `advanceToStarting`（含 VALIDATING） |
| Bind ExecutionContext | ExecutionContext + ContextLifecycleService | `ExecutionBootstrap.open` |
| Start Trace | TraceRoot + TraceLifecycleService | `open` + span `submit`；span 打日志 |
| FakeWorker | Worker SPI + `NoopWorker` | 固定 OK，无业务 |
| Artifact hello.txt | Artifact + ArtifactLifecycleService | metrics `artifactName` → name |
| Commit | ArtifactLifecycle COMMITTED | + Context index（G5） |
| Checkpoint | Checkpoint + MemoryCheckpointStore | 成功路径、终态前写入 |
| Task Success | TaskStatus.SUCCEEDED | 再 freeze Context + close Trace |

## 3. What we deliberately did **not** add

Scheduler · Workflow · Provider · Capability · Plugin · Memory · Evolution · Claude/Cursor/Maven/Git/Playwright CLI。

## 4. Kernel support verdict

**结论：Kernel 可支撑 Walking Skeleton，无需绕过设计。**

| 观察 | 处理 |
|------|------|
| Checkpoint 不进 `CHECKPOINTING` 状态 | 符合 Sprint-5 Foundation；不改状态机 |
| `Task.markCheckpoint` 禁止终态 | Checkpoint 写在 SUCCEEDED **之前**（RUNNING） |
| Artifact 名称原硬编码 | 仅经 `WorkResult` metrics 传递（Worker SPI），无新 Domain |
| Trace 无外部导出 | `System.out` 日志 + `spanNames` / `closed()` 足够证明完整结束 |
| RuntimeResult 不携带 checkpointId | Demo/测试注入 `MemoryCheckpointStore` 查询（未扩 Domain） |

## 5. Demo & Tests

| 入口 | 说明 |
|------|------|
| `WalkingSkeletonMain` | `mvn -pl ai4se-demo -am exec:java` |
| `WalkingSkeletonTest` | 断言 Task / Context / Artifact / Checkpoint / Trace |
| `NoopWorkerTest` | FakeWorker 契约 |

## 6. Follow-ups（非本 Sprint）

- Resume from Checkpoint（仍不得改写历史 Checkpoint）
- RuntimeResult 可选暴露 `latestCheckpointId`（API，非 Domain）
- Trace 观察者替换 stdout 日志
