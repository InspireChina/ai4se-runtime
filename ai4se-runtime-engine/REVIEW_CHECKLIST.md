# Sprint-5 Review Checklist — Checkpoint Foundation

□ 是否实现 Resume / Recovery / Retry / Scheduler？ — **否**
□ 是否接入 ClaudeWorker / Capability / Knowledge / Rule / Workflow？ — **否**
□ Checkpoint 是否为 Kernel 对象？ — **是**（`kernel.checkpoint.Checkpoint`）
□ 字段是否仅含约定集合？ — **是**（checkpointId / taskId / sequence / createdAt / artifactSnapshot / contextRevision / integrity）
□ CheckpointStore SPI 是否仅 save/load/latest/list？ — **是**
□ SPI 是否无具体存储依赖？ — **是**
□ MemoryCheckpointStore 是否仅内存？ — **是**（无 DB / FS）
□ Checkpoint 是否改变 Task 生命周期（进入 CHECKPOINTING）？ — **否**
□ Checkpoint 是否参与调度 / 恢复？ — **否**
□ 成功路径是否 Artifact → Checkpoint → Task End？ — **是**
□ ArchitectureTest 通过？ — `mvn clean test`
□ CheckpointIntegrationTest 通过？ — `mvn clean test`
□ mvn clean test 全部通过？ — 交付标准

## ADR Suggestion（不改 Frozen 文档）

- TODO: Resume 应从 `CheckpointLifecycleService` + `ExecutionBootstrap` 恢复，不得改写历史 Checkpoint。
- TODO: 失败路径当前不写 Checkpoint；若需失败耐久点，经 ADR 明确与 C4/C6 关系。
