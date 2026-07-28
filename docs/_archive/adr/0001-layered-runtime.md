# ADR-0001 · 采用分层 Runtime + 单向依赖

- Status: **Accepted**
- Date: 2026-07-28
- Tags: architecture, dependency

## Context

AI Loop 需要同时支持：可嵌入宿主、可治理副作用、可替换模型与工具、可扩展领域流程。若采用「大单体 Agent 类」或随意依赖，后续无法做权限、审计与多团队并行开发。

## Decision

采用七层架构（Host → Facade → Kernel → Engines → SPI → Extensions → External），**依赖只允许向下**。Engines 不编译依赖 Plugin/Adapter 实现；由 Host 装配。

层职责与禁止事项见 `docs/architecture/11-layers-and-dependencies.md`。

## Consequences

### Positive

- 边界清晰，适合多模块并行
- 可用 ArchUnit 等锁定依赖
- 测试可用 Fake Adapter 替换

### Negative

- 样板模块更多，初期启动成本高
- 跨 Engine 协作需经 Kernel，可能增加一次跳转

### Follow-ups

- 实现阶段加入 architecture tests
- Facade API 稳定后再开放外部 Host SDK
