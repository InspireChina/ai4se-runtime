# ADR-0003 · Capability 作为副作用唯一入口

- Status: **Accepted**
- Date: 2026-07-28
- Tags: capability, security

## Context

AI 闭环必然触达文件系统、VCS、网络与进程。若 Skill / Model 回调任意执行，无法做权限、预算与审计。

## Decision

1. **所有外部副作用必须经 Capability Engine** 调用已注册 Capability。
2. Capability 声明 `sideEffects`、`requiredPermissions`、输入输出 schema。
3. Rule 可在 Capability 调用前后门禁，但 Rule 自身不得产生副作用。
4. Model 调用：优先经 Model Engine；若需统一权限管道，可暴露 `model.invoke` Capability 作为桥（二选一在实现 RFC 冻结，默认 **Kernel → Model Engine**，Capability 桥可选）。

错误分类采用：`RETRYABLE` / `FATAL` / `NEEDS_POLICY_EXCEPTION` / `VALIDATION`。  
（初版 `NEEDS_HUMAN` 已统一更名为策略例外，见 ADR-0007 / Architecture 21。）

## Consequences

### Positive

- 权限与审计有单一卡口
- Skill/Workflow 保持编排纯度

### Negative

- 细粒度 Capability 数量上升
- 每次调用有管道开销

### Follow-ups

- RFC-0003 Capability SPI
- builtin 最小 Capability 集冻结
