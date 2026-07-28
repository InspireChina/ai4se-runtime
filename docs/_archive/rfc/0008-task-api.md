# RFC-0008 · Task API & Lifecycle

- Status: **Accepted-Draft**（架构冻结，实现前可微调字段名）
- Related: ADR-0009, Architecture 15, supersedes Loop-centric surface of RFC-0001

## Motivation

定义 Host 与 Kernel 之间以 Task 为中心的稳定 API。

## Task Facade

| Method | Semantics |
|--------|-----------|
| `submit(TaskRequest): TaskHandle` | 校验 Profile、入队 |
| `get(taskId): TaskSnapshot` | 状态与摘要 |
| `cancel(taskId, reason)` | 取消 |
| `resume(taskId, ResumeCommand)` | 崩溃恢复或策略例外后继续 |
| `retry(taskId, RetryCommand): TaskHandle` | 默认新 TaskId + parentTaskId |
| `grantPolicyException(taskId, Grant)` | 批准例外 |
| `subscribe(taskId, listener)` | 事件 |

## TaskSnapshot

`status, projectId, profileRevision, workflowId, iterationIndex, budgetRemaining, latestCheckpointId, traceId, lastError, blockedReason?`

## Events

`TaskAccepted` `TaskQueued` `TaskStarted` `IterationStarted` `StepStarted` `StepCompleted` `RuleDecision` `CapabilityInvoked` `ModelInvoked` `CheckpointCreated` `KnowledgeCited` `PolicyExceptionRequired` `TaskSucceeded` `TaskFailed` `TaskCancelled`

## Relation to RFC-0001

RFC-0001 中 Loop Facade 映射为 Task Facade；事件名 `Loop*` 弃用。保留 Iteration 概念表达 Plan-Act-Verify-Reflect。
