# ADR-0011 · Trace 为观测中心

- Status: **Accepted**
- Date: 2026-07-28
- Tags: trace, observability

## Context

仅有应用日志无法支撑 Task 复盘、成本核算与 Console 展示。

## Decision

1. 以 Trace/Span 树作为主观测模型；Audit 为可关联事件
2. 关键路径强制埋点（Task/Iteration/Step/Rule/Skill/Capability/Model/Checkpoint/Knowledge）
3. Vue Console 以 Trace 时间线为排障主界面
4. v1 内部 TraceStore；OTel 导出可选

## Consequences

- Observation Engine 以 Trace 为中心升级
- 契约测试可检测缺失埋点
