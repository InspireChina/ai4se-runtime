# 当前支持状态（S0 对齐）

> 真源：[`docs/adr/0016-engine-as-scheduler-v0.md`](./adr/0016-engine-as-scheduler-v0.md)  
> 排期：[`docs/build-pathway-playbook.md`](./build-pathway-playbook.md) 水位表

## 一句话

**Runtime Engine 串行跑窄通路 Task；没有独立 Scheduler；Checkpoint 能写不能 Resume；S0–S9 最小台阶已通（含两 Profile 同 Runtime）。**

## 有

- Task 状态机由 Engine 推进（常见路径到 SUCCEEDED/FAILED）
- ShellWorker / FileEditWorker
- RuntimeResult（含 checkpointId 等字段）
- **S5 最小：** `StageGate` 在 Worker 前检查；`PLAN` 缺 `discovery.hit-set` 则 FAILED 且不调 Worker
- **S6 最小：** 串行 `PLAN_DESIGN`→`PLAN_TEST` 产出 `plan.design` / `plan.test-strategy`；`EXECUTION` 缺 `plan.approved` 拒绝；编码不得改写测试方案 Artifact
- **S7 最小：** VERIFY 读 `plan.test-strategy` 注入命令；`skip`/空命令不调 Worker；Worker 失败或报告缺 acceptance ID ⇒ FAILED（非 SUCCEEDED）
- **S8a 最小：** `DISCOVERY` 在 Engine 跑 Map+Search，提交 `discovery.hit-set`；gaps 写入澄清问卷并 `BLOCKED_POLICY`；`maxFiles`/`maxPayloadChars` 可截断
- **S9 最小：** `ProjectProfile`（文本配置）驱动不同 `profileId`/`verifyCommand`/`stack`；同一 `Runtime`；vision 四问见 `SecondProjectIntegrationTest`
- Demo 交付串行编排 + Analysis Bundle / Stop（Demo/Analysis 层；与 Engine 门禁互补）
- **Demo↔StageGate 对齐：** `DemoGateBundle` 在 SerialDelivery / FirstProduction 共享 Artifact 店并种子 `discovery.hit-set` / `plan.approved` / `plan.test-strategy`；薄探测用 `GIT_STATUS`（避免误走 Engine MapSearch DISCOVERY）

## 没有（禁止口头说有）

- Scheduler / WorkItem 队列产品
- Checkpoint Resume
- S4–S9 产品化 UI / Workflow DSL / 完整 Policy 引擎 / ProfileEngine
- StageRunner / Graph / Claude 主路径编码工人
- S8b/c 可再生索引 / 决策遗产知识库
- 外部生产多仓接入（S9 仅为双 Profile 最小证明）

## 怎么验收本页

读 ADR-0016 门禁三问；S9 看 `SecondProjectIntegrationTest` 绿即可。  
大白话说明（须与本页一致）：[`docs/project-status-plain-language.md`](./project-status-plain-language.md)。  
压测关闭证据：`s0-s9-pressure-test-evidence.md` · `alt-approach-pressure-evidence.md` · `post-stagegate-regression-evidence.md`（均为 REVIEWER_CLOSE: CLOSED）。
