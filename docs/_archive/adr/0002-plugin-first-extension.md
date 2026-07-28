# ADR-0002 · Plugin 作为唯一业务扩展面

- Status: **Accepted**
- Date: 2026-07-28
- Tags: plugin, extension

## Context

领域流程（编码闭环、评审闭环、发布闭环）会快速增长。若允许直接改 Kernel 添加业务，内核将失控。

## Decision

**一切领域贡献通过 Plugin**：Workflow、Skill、Rule、Capability、ModelProvider、GraphEnricher、（可选）Adapter。

Kernel / Engines 只识别 Plugin SPI 与贡献点，不包含领域 Workflow。

v1 采用冷启动发现与加载；不要求热插拔与插件市场。

## Consequences

### Positive

- 业务可独立版本化交付
- Kernel 保持瘦
- 扩展路径统一（见 Extension Guides）

### Negative

- 简单 Demo 也需走 Plugin 包装
- ID 冲突与版本兼容需要治理

### Follow-ups

- RFC-0002 定义 Plugin SPI
- 约定 namespace 规则：`org.segment.feature`
