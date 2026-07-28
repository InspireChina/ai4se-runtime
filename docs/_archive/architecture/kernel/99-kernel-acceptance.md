# 99 · Sprint-0.5 Kernel Acceptance

> 通过本清单后，才允许进入 Java/Spring 实现。

## A. 五大对象是否定义完整

| # | 对象 | 必须包含 | 文档 | Pass? |
|---|------|----------|------|-------|
| A1 | Task | 状态机、字段、事件、与 WorkItem 关系 | `01` | |
| A2 | Artifact | 字段、kind 目录、生命周期、Store 端口 | `02` | |
| A3 | Worker | 统一接口、Kind、示例目录、与 Capability 关系、CLI 约束 | `03` | |
| A4 | Scheduler | WorkItem 状态机、队列、依赖、重试、恢复、并发租约 | `04` | |
| A5 | ExecutionContext | 分区、物化、View、冻结与 Checkpoint | `05` | |

## B. 不变式

| # | 不变式 | Pass? |
|---|--------|-------|
| B1 | I1 副作用经 Worker | |
| B2 | I2 阶段间用 Artifact | |
| B3 | I3 Worker 只见 Context View | |
| B4 | I4 仅 Scheduler 推 Task/WorkItem 状态 | |
| B5 | I5 终态冻结 Context | |
| B6 | I6 WorkItem 幂等重放 | |

## C. 与 Blueprint 统一

| # | 检查项 | Pass? |
|---|--------|-------|
| C1 | 有 reconciliation 文档 | |
| C2 | Task 状态以 kernel/01 为权威 | |
| C3 | Adapter 收敛到 Worker 的叙事清晰 | |
| C4 | Profile/Graph/Knowledge/Rule/Skill 进入 Context | |
| C5 | 仍坚持非 Coding Agent / 无人值守 | |

## D. 可开工（设计层）

检查人应能不靠其他口头知识回答：

1. 新 Task 从提交到 SUCCESS 如何被 Scheduler 推进？  
2. Maven 测试结果如何成为 Artifact 并被下一阶段消费？  
3. Claude CLI 与 Git 为何是同一 Worker 接口？  
4. 崩溃后如何靠 Checkpoint + Scheduler 恢复且不乱改 Context？  
5. Project Profile 与 Knowledge 如何出现在 Worker 视野中？  

## E. 阶段门禁

| 门禁 | 要求 |
|------|------|
| 代码 | **禁止** Java 业务实现 |
| Prompt | **禁止** 生成业务 Prompt 正文 |
| 下一阶段 | Pass 后才开 Sprint-1 Skeleton |

## 签字

| 角色 | Pass/Fail | 签字 | 日期 |
|------|-----------|------|------|
| 架构 | | | |
| 平台 | | | |
| 实现负责人 | | | |
