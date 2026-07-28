# ADR-0010 · Checkpoint 强制恢复点

- Status: **Accepted**
- Date: 2026-07-28
- Tags: checkpoint, reliability

## Context

无人值守 Task 可能运行数十分钟到数小时，进程与节点会失败。无耐久恢复点则无法生产使用。

## Decision

1. Checkpoint 为一等子系统，由 Kernel Checkpoint Service 管理
2. Iteration CONTINUE 边界与进入 BLOCKED_POLICY 前 **必须** 建 Checkpoint
3. WRITE/NETWORK/PROCESS Capability 必须支持幂等或显式高危策略
4. 工作区一致性策略由 Profile 配置，默认 `REQUIRE_CLEAN_MATCH`

## Consequences

- 持久化成为 v1 必需，不只是日志
- Workflow cursor 必须可序列化
- 增加存储与测试成本，但换来可恢复性
