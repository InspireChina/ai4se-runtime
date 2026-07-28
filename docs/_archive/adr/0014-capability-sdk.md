# ADR-0014 · 官方 Capability SDK

- Status: **Accepted**
- Date: 2026-07-28
- Tags: sdk, capability

## Context

仅有 SPI 时，扩展质量依赖口头约定，无法跨项目稳定复用 Capability 开发方式。

## Decision

1. 提供 `capability-sdk` 与 `capability-sdk-test`
2. Engine 只依赖 `spi-capability`，**不**依赖 SDK
3. Plugin 开发标准路径强制经 SDK（文档与脚手架）
4. SDK 版本与 `runtimeApiVersion` 对齐

## Consequences

- 扩展者有明确测试与打包方式
- 需要维护 SDK 兼容性
