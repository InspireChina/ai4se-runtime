# ADR-0009 · Task 一等公民（取代对外 Loop API）

- Status: **Accepted**
- Date: 2026-07-28
- Supersedes: 对外 API 以 `Loop` 为主的表述（见 RFC-0001 修订说明）
- Tags: task, lifecycle

## Context

初版蓝图以 `LoopInstance` 为最小执行单元，易与“对话回合 / Agent loop”混淆，也无法表达：排队、调度、跨进程恢复、与 Project Profile 绑定等 SE 平台语义。

## Decision

1. **Task** 是对外与持久化的一等对象。
2. Task 内部包含：`WorkflowInstance`、零到多次 **Iteration**（Plan→Act→Verify→Reflect）、Checkpoint 链、Trace 根。
3. 旧文档中的 “Loop” 在对外语义上迁移为 **Task**；在内部执行语义上迁移为 **Iteration**（或 `TaskIteration`）。
4. Kernel 的 Lifecycle Manager 以 Task 状态机为准。

## Consequences

- API / 控制台 / 存储均以 TaskId 关联
- 需要新增 Task 生命周期专章与 RFC
- 保留 Iteration 以表达闭环迭代，不丢弃原 Plan-Act-Verify-Reflect 模型

## Migration of prior docs

| 旧术语 | 新术语 |
|--------|--------|
| Loop API | Task API |
| LoopInstance | Task（+ 内嵌 WorkflowInstance） |
| Loop 生命周期阶段 Planning/Acting… | Iteration 阶段；Task 另有 Submitted/Running… |
| LoopContext | TaskContext |
