# Control Contract

> 能力域 **03 · Delivery Orchestration · Control**。  
> 「什么时候做 / 停 / 回环 / Resume」。**不是**独立 Decision Engine。

## 目标

控制 Workflow 各阶段启停与回环，直到 Story 完成或明确 Stop。  
控制流 **永不**交给模型自由 ReAct；**永不**放进 AI Execution Adapter。

## 必须能表达的决策（现网优先）

| 决策 | 含义 |
|------|------|
| 启动 / 停止阶段 | 进入或离开某 Workflow 阶段 |
| Resume vs 重开 Session | 同角色续跑 vs 新 Session（重建包由 02 做） |
| 审批 | Clarification / Plan Approval / 人验收 |
| 回环 | Verification FAIL → Defect Package → Development |
| 熔断 / Stop | token、缺陷轮次、澄清 Stop、连续同失败 |
| 恢复 | 从 Checkpoint / `.story` 状态恢复（产品属 Control；Runtime 提供骨头） |

**闪断 Resume（附录 A，已落地最小）：** `SameRoleFlashResume` — 同角色 `resume`；真源是 `.story`（Allowed/Defect），**不是** Runtime Checkpoint 产品宣称。

后置：并行、细粒度 Budget、复杂 Rollback。

## 与 Context Engineering

| | 负责 |
|--|------|
| **Control** | 是否 Resume / 是否停 / 是否回环 |
| **Context Engineering** | Resume 时 Package 如何压缩重建 |

## 与 Runtime Foundation（07）

Runtime 提供 Task / Artifact / Worker / Trace / Checkpoint / Decision 骨头。  
**Control 是产品控制；Runtime 不是一级交付方法论。**

## FAIL / 上交人工

熔断 → 停止自动推进 → Story 状态可审 → 等人决策。

主链：[capability-map](../../00-product/capability-map.md) · Workflow stages：[../workflow/stages/](../workflow/stages/README.md)
