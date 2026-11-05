# ADR-0015 · Kernel 五大核心对象

- Status: **Accepted**
- Date: 2026-07-28
- Tags: kernel, sprint-0.5

## Context

Blueprint 子系统很多。若直接编码，团队会在 Engine 边界上重复发明：任务状态、产物、执行器、调度、上下文。需要在实现前冻结 Kernel 主对象。

## Decision

1. 进入实现前完成 **Sprint-0.5 Kernel Design**。
2. Kernel 稳定核心对象固定为五个：**Task、Artifact、Worker、Scheduler、ExecutionContext**。
3. Capability / Plugin / Adapter / Workflow / Skill / Rule / Graph / Knowledge / Profile / Checkpoint / Trace 仍然有效，但必须 **映射到** 这五个对象上（见 `kernel/06-reconciliation.md`）。
4. **暂不进入 Java 业务开发**，直到 `kernel/99-kernel-acceptance.md` 通过。

## Consequences

- 调度与执行语义以 Scheduler + Worker 为准
- 产物统一为 Artifact，避免“临时文件/字符串/附件”多套模型
- ExecutionContext 取代散落的隐式全局上下文
- 与初版“Loop”叙事继续以 Task 为准
