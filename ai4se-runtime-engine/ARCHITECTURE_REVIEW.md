# Sprint-5 Architecture Review — Checkpoint Foundation

工程审查结论（非设计文档；不改 Frozen Architecture）。

## Verdict

最小可运行持久化已落地：Kernel `Checkpoint` + `CheckpointStore` SPI + `MemoryCheckpointStore`；成功路径在 Artifact 之后、Task 终态之前写入 Checkpoint；**不**实现 Resume / Recovery / Retry。

## 1. Domain

| 字段 | 说明 |
|------|------|
| checkpointId | `CheckpointId` |
| taskId | 绑定唯一 Task（C1） |
| sequence | 同 Task 单调递增（C2，由 Service 分配） |
| createdAt | 创建时刻 |
| artifactSnapshot | ArtifactId 索引，无 blob |
| contextRevision | Context 修订摘要（phase + index size） |
| integrity | SHA-256 载荷摘要（C5 结构完备） |

不可变：无 mutate API；Store `save` 拒绝同 id 覆盖（C3）。

## 2. SPI / 实现

```
kernel.checkpoint.CheckpointStore  — save / load / latest / list
engine.store.MemoryCheckpointStore — 内存 ConcurrentHashMap
engine.service.CheckpointLifecycleService — createAtBoundary + 序列号/integrity
```

Kernel 不依赖 Engine；Engine 实现 SPI。

## 3. Runtime 编排

成功路径：

```
… → Worker → Artifact(COMMITTED+index) → Checkpoint(save + markCheckpoint) → SUCCEEDED → freeze/finish
```

- 不进入 `CHECKPOINTING`
- 失败路径不写 Checkpoint（本 Sprint 范围）
- Checkpoint 不调度、不恢复

## 4. 依赖与门禁

- ArchUnit：依赖方向保持；`Task.markCheckpoint` 仅经 Lifecycle Service
- 无循环依赖；无 God Resume 逻辑

## 5. 测试

- `CheckpointIntegrationTest`：Task → Worker → Artifact → Checkpoint → Task End；失败无 Checkpoint
- 既有 `ArchitectureTest` / `RuntimeIntegrationTest` / Walking Skeleton 保持绿色

## 6. 明确不做（下 Sprint）

Resume、Recovery、Retry、Scheduler、ClaudeWorker、DB/FS Store。
