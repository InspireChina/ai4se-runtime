# ADR-0007 · Runtime 不是 Coding Agent

- Status: **Accepted**
- Date: 2026-07-28
- Tags: positioning, unattended, governance

## Context

业界常把“能调工具的 LLM 循环”称为 Agent。若本项目按 Agent 产品思路实现，将导致：模型自由决定控制流、难以无人值守、难以跨项目复用、难以做强门禁与恢复。

本项目目标是 **AI Software Engineering Runtime**：执行结构化软件工程 Task。

## Decision

1. 产品定位冻结为 **AI SE Runtime**，明确非 Coding Agent。
2. 控制流由 **Workflow + Rule** 拥有；Model 只在声明节点内产生**结构化**输出。
3. 默认运行模式为 **Unattended**；Human 仅作策略例外。
4. 对外主 API 以 **Task** 为中心（见 ADR-0009），不以 Chat Session 为中心。
5. 文档与代码审查中，出现“自由 ReAct 主循环”视为架构违规。

## Consequences

### Positive

- 团队对“该不该让模型自己选下一步”有明确否决权
- 无人值守、审计、恢复成为一等需求

### Negative

- 短期不如“对话 Agent Demo”炫酷
- Workflow/Skill/Rule 设计成本更高

### Follow-ups

- 架构验收清单加入“非 Agent”检查项
- Host UI（Vue）以 Task/Trace/Profile 为主，不做通用 Chat 产品
