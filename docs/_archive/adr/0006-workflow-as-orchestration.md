# ADR-0006 · Workflow 作为闭环编排骨架

- Status: **Accepted**
- Date: 2026-07-28
- Tags: workflow, orchestration

## Context

需要把 Intent→Plan→Act→Verify→Reflect 变成可审计、可暂停、可限制迭代的结构。纯代码状态机散落在 Kernel 会导致领域流程无法插件化。

## Decision

- **Workflow Engine** 负责流程定义与实例推进
- Kernel 只做调度与策略，不硬编码具体领域阶段图
- 领域闭环以 Plugin 贡献的 Workflow 交付
- Skill 承载可复用中等粒度程序；Workflow 承载阶段与转移
- Reflect 作为一等节点类型，输出结构化 `CONTINUE | DONE | ABORT`

v1 DSL 为声明式 YAML（或等价），不做 BPMN 全兼容，不做分布式编排。

## Consequences

### Positive

- 闭环可视化、可静态校验
- 多领域流程可并存

### Negative

- 需要维护 DSL 与校验器
- 过度细碎的 Workflow 可能难读（需指南约束）

### Follow-ups

- RFC-0004 Workflow DSL
- 提供 `coding.standard-loop` 作为参考定义（实现阶段，本阶段仅概念示例）
