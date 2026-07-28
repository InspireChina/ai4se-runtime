# ADR-0004 · Adapter 隔离外部系统

- Status: **Accepted**
- Date: 2026-07-28
- Tags: adapter, ports

## Context

LLM、Git、CI、Tracker 等厂商 SDK 变更频繁，且不利于测试。

## Decision

采用 Ports & Adapters：

- SPI 定义 Port 接口
- Adapter 模块实现 Port
- Engine / Capability 只依赖 Port
- Host 或 Plugin 负责注册具体 Adapter

禁止 Runtime 模块直接依赖厂商 SDK。

## Consequences

### Positive

- 可替换、可 Fake、可多实现并存（qualifier）
- 厂商升级不影响 Engine

### Negative

- Port 抽象需要前瞻设计
- 部分高级特性可能滞后暴露

### Follow-ups

- RFC-0007 Model Adapter
- 为每个 Port 提供至少一套 Fake
