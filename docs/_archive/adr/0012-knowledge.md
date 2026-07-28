# ADR-0012 · Knowledge 子系统

- Status: **Accepted**
- Date: 2026-07-28
- Tags: knowledge, reuse

## Context

跨 Task/跨项目需要可治理的工程知识，而不是把 Prompt 或聊天历史当作记忆。

## Decision

1. 引入 Knowledge Engine 与 Knowledge Pack
2. 与 Repo Graph、Task memory 严格区分
3. 默认检索只读；写入提案制（DRAFT→批准）
4. Profile 声明 knowledge scopes/packs，支撑跨项目复用
5. 每次 Hit 必须进 Trace

## Consequences

- 新增模块与 API
- 组织级 Pack 成为复用单元之一
