# RFC-0001 · Runtime Kernel API（修订）

- Status: **Superseded-in-part by RFC-0008**
- Related: ADR-0009

## 修订说明

初版以 Loop Facade 为中心。统一检查后：

| 初版 | 现行 |
|------|------|
| `startLoop` | `submit(TaskRequest)` |
| `LoopHandle` | `TaskHandle` |
| `NEEDS_HUMAN` | `NEEDS_POLICY_EXCEPTION` / `BLOCKED_POLICY` |
| Loop 生命周期状态 | Task 状态机 + 内部 Iteration 阶段 |

本文保留作为历史映射；**新实现以 RFC-0008 为准**。

Observation 中的审计事件与 Trace（RFC-0010）对齐。
