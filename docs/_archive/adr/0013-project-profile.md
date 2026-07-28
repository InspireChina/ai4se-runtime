# ADR-0013 · Project Profile 驱动多项目装配

- Status: **Accepted**
- Date: 2026-07-28
- Tags: profile, multi-project, unattended

## Context

不同仓库的构建、权限、门禁、Workflow 不同。若差异写死在代码或每次 Task 手填，无法无人值守与跨项目复用。

## Decision

1. Project Profile 为一等声明式装配单元
2. Task 创建时快照 ProfileRevision
3. Task 覆盖只允许收紧或白名单字段；放宽走策略例外
4. Profile 选择 Plugin、Workflow 路由、预算、Knowledge、Checkpoint 策略、模型路由

## Consequences

- 新项目接入 = 写 Profile + 复用 Org Plugin/Knowledge
- 校验器成为启动关键路径
