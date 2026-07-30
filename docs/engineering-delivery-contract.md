# Engineering Delivery Contract

> **Status:** Contract（规范）· 非实现  
> **Scope:** 定义 AI4SE Runtime 之上的 **Delivery** 必须遵守什么。  
> **Non-scope:** 不实现 Scheduler / Workflow / StageRunner / Knowledge / AI Worker 等。  
> **依据：** Frozen Invariants · First Production Delivery · Boundary Validation/Stress · Production Input Layer  

本文回答一件事：

> **一次软件工程交付，在什么条件下算成功；各角色（人 / Delivery Pipeline / Runtime / Worker）各保证什么。**

---

## 1. Stage Definition

约定阶段名（逻辑阶段，**不是** Runtime Kernel 对象，**不是** Workflow DSL）。

当前实现可用 Production Input + Demo 串行多次 `Runtime.submit` 覆盖其中可自动部分；**Clarification / Approval** 今日多为人工闸，合同仍保留位置。

### 1.1 Discovery

| 项 | 定义 |
|----|------|
| **输入** | Requirement（已满足 §4 最低条件）· Workspace 路径 · （可选）discovery 命令 / 空 |
| **输出** | Discovery Report（文件或 Artifact 载荷：检索摘要、repo 事实、或显式「跳过」记录） |
| **Owner** | **Delivery Pipeline** 发起；**Worker** 执行摸底动作；人可改命令或跳过 |
| **Artifact** | Delivery：`discovery` 报告；Runtime：该步 `WorkResult`→COMMITTED Artifact（若执行） |
| **PASS** | 合同允许空 Discovery 且记录跳过；或命令成功且产出可读报告 |
| **FAIL** | 合同要求非空 Discovery 但命令失败 / 无报告 |
| **人工** | 选择是否跳过、改命令、解读结果；**不**由 Runtime 解读业务含义 |

### 1.2 Clarification

| 项 | 定义 |
|----|------|
| **输入** | Requirement + Discovery（可空）中的不确定点 |
| **输出** | Clarified Requirement（修订文本）或「无需澄清」声明 |
| **Owner** | **Human**（主）；Pipeline 只承载产物 |
| **Artifact** | Delivery / Business：澄清记录；**非** Runtime Kernel 新类型 |
| **PASS** | 歧义已关闭或显式声明无歧义 |
| **FAIL** | 仍存在阻塞性未知却进入 Planning |
| **人工** | **必须可人工**；今日无 Runtime Human-Wait 实现时，澄清在 Runtime **外**完成 |

### 1.3 Planning

| 项 | 定义 |
|----|------|
| **输入** | Clarified Requirement · Discovery |
| **输出** | Plan（步骤、拟改路径、Verification 预期） |
| **Owner** | **Human / 外部作者** 写内容；Pipeline 落盘；Worker 可写文件 |
| **Artifact** | Delivery：`PLAN.md`（或 input `plan.md`）；Runtime：落盘步的 FileEdit manifest |
| **PASS** | 步骤可执行、指向可运行的 Verification、范围不超出 Requirement |
| **FAIL** | 空计划 / 与需求无关 / 无法映射到 Execution 补丁 |
| **人工** | **写计划**；Runtime **不保证**计划质量 |

### 1.4 Approval

| 项 | 定义 |
|----|------|
| **输入** | Plan（+ 高风险时的补丁预览） |
| **输出** | Approve / Reject 记录 |
| **Owner** | **Human** |
| **Artifact** | Delivery：approval 记录（文件即可）；非 Kernel |
| **PASS** | 显式 Approve，或合同声明本交付「免 Approval」（低风险样本） |
| **FAIL** | 需 Approval 却未批准即 Execution |
| **人工** | **闸口在人**；今日可在 Runtime 外完成 |

### 1.5 Execution

| 项 | 定义 |
|----|------|
| **输入** | Approved Plan · Patches（Production Input `patches/` 或等价） |
| **输出** | 工作区变更 |
| **Owner** | **Worker** 执行写/改；补丁内容 **Human/Input** |
| **Artifact** | Business：源码/配置等；Runtime：`file-edit-manifest` 等 COMMITTED Artifact |
| **PASS** | 补丁全部落地、路径不逃逸 workspace、Worker OK |
| **FAIL** | Worker FAIL / 路径拒绝 / 部分写入合同不允许 |
| **人工** | 提供补丁；不在 Worker 内「现场发明」业务方案（合同层禁止假装 AI Worker 已存在） |

### 1.6 Verification

| 项 | 定义 |
|----|------|
| **输入** | Execution 后的 Workspace · verify 命令（Input `verify.yaml`） |
| **输出** | Verification Report（stdout/stderr · exit） |
| **Owner** | **Worker** 跑命令；命令由 **Input/人** 指定 |
| **Artifact** | Delivery：验证报告；Runtime：`shell-stdout` 等 Artifact + Task 成败 |
| **PASS** | 合同指定命令 exit 0（如 allowlist 内 `mvn … test`） |
| **FAIL** | 非 0 / 超时 / 命令不在允许策略内 |
| **人工** | 指定/调整 verify；不可把 FAIL 改成口头 PASS |

### 1.7 Review

| 项 | 定义 |
|----|------|
| **输入** | 全阶段结果 · RuntimeResult 字段 · 工作区 |
| **输出** | Review Report（是否接受交付、缺口、建议） |
| **Owner** | **Human / Delivery 叙述**；Worker 可落盘 |
| **Artifact** | Delivery：`REVIEW_REPORT.md`；Runtime：落盘步 Artifact |
| **PASS** | 报告含：需求、步骤、验收、是否完成、Runtime 不足（若有） |
| **FAIL** | 缺关键字段 / 与 RuntimeResult 或工作区事实矛盾 |
| **人工** | **写评审结论**；Runtime 不自动「架构通过」 |

### 1.8 Delivery

| 项 | 定义 |
|----|------|
| **输入** | Review 通过意图 · 全阶段 RuntimeResult |
| **输出** | Delivery Report（可观测汇总） |
| **Owner** | **Delivery Pipeline** 汇总；字段来自 **Runtime**；Worker 可落盘 |
| **Artifact** | Delivery：`DELIVERY_REPORT.md`；Runtime：各步 Result/Checkpoint 引用 |
| **PASS** | 见 §5 SUCCESS；报告字段与事实一致 |
| **FAIL** | 见 §5 FAILED；或报告造假/缺审计字段 |
| **人工** | 可补充叙述；不可伪造 Checkpoint/Trace |

---

## 2. Artifact Contract

### 2.1 按 Stage 的「必须产物」

| Stage | 必须产物 | 类别 |
|-------|----------|------|
| Discovery | 跳过声明 **或** Discovery Report | Delivery（内容）+ Runtime Artifact（若执行） |
| Clarification | 澄清后 Requirement 或「无歧义」声明 | Delivery / Business |
| Planning | Plan 文档 | Delivery |
| Approval | Approve/Reject 或「免审批」声明 | Delivery |
| Execution | 工作区文件变更 + 变更清单 | Business + Runtime manifest Artifact |
| Verification | 命令输出与成败 | Delivery 报告 + Runtime Artifact |
| Review | Review Report | Delivery |
| Delivery | Delivery Report | Delivery（引用 Runtime 审计字段） |

### 2.2 三类归属

| 类别 | 含义 | 例子 |
|------|------|------|
| **Runtime** | Kernel 结算对象：Task 绑定的 Artifact / Trace / Checkpoint / RuntimeResult | `shell-stdout.txt`、`file-edit-manifest.txt`、`checkpointId` |
| **Delivery** | 交付合同文件与报告（Pipeline/Input 层） | `requirement.md`、`plan.md`、`REVIEW_REPORT.md`、`DELIVERY_REPORT.md` |
| **Business** | 目标工程里的业务资产 | `FeatureFlags.java`、`UserApi.java`、`application.properties` |

**规则：**

- Runtime **只保证**其类别产物的生命周期与可观测性。  
- Delivery / Business 正确性 **不**由 Runtime 语义保证。  
- 禁止把 Business 类型提升为新 Kernel Object（本契约 Non Goal）。

---

## 3. Worker Contract

### 3.1 Worker 能做什么

- 在 `WorkRequest` + `ExecutionContextView` 下执行 **一次** 明确动作。  
- 返回 `WorkResult`（OK / FAIL + metrics + message）。  
- 在策略内产生副作用：写 workspace 文件、跑 allowlist 命令等。  
- 保持 **不依赖** Runtime Engine / Kernel 实现细节（SPI 方向）。

### 3.2 Worker 不能做什么

- 推进 Task 状态机、提交/废弃 Artifact、写 Checkpoint、关 Trace（**Runtime** 的事）。  
- 编排多阶段 Delivery（Discovery→…→Delivery）。  
- 定义 Requirement、批准 Plan、判定架构 Review 通过。  
- 假装拥有未实现的 Capability（AI 推理、跨仓调度、Resume）。  
- 逃逸 workspace / 突破 allowlist。

### 3.3 Runtime 必须保证

- 单次 `submit`：合法 Task 生命周期 · Context 冻结 · Trace 完整 · Artifact COMMITTED/ABANDONED · 成功路径 Checkpoint 只写 · `RuntimeResult` 可观测字段。  
- 只依赖 Worker SPI，不依赖具体工人实现。  
- 不把 Worker 失败静默当成业务成功。

### 3.4 Delivery Pipeline 必须保证

- 阶段顺序与门闸（含 Clarification/Approval 人工点）。  
- Input Contract 装载（`requirement` / plan / patches / verify）。  
- 跨阶段报告汇总与 SUCCESS/FAILED 合同判定（§5）。  
- 不把 Pipeline 逻辑塞进 Kernel。

---

## 4. Requirement Contract

### 4.1 进入 Runtime **之前**必须满足

1. **非空**目标文本（可验收意图可读）。  
2. **Workspace** 已指定且可访问。  
3. **Acceptance 口径**可指向 Verification（至少一条可执行 verify，或显式声明「仅人工验收」——后者今日生产路径不推荐）。  
4. **补丁或执行物来源**已具备（Production Input `patches/` 或等价）；**禁止**「只有一句话、指望 Runtime 现场发明补丁」。  
5. 已过 **Clarification** 或显式「无歧义」。  
6. 已过 **Approval** 或合同允许免批。

未满足 1–6：**不得**对 Execution 调用 Runtime（可对 Discovery 等前置步调用，但不得宣称 Delivery SUCCESS）。

### 4.2 哪些必须经过 Discovery

| 情况 | 要求 |
|------|------|
| 仓库陌生 / 影响面不清 | **必须**非空 Discovery |
| 样本工程、影响面已由 Plan 钉死 | **允许**跳过（须书面记录） |
| Production Input 缺省 | 允许 `echo skip-discovery`，但 Review 须标明 Discovery 薄弱 |

### 4.3 哪些必须经过 Clarification

- Requirement 含互斥目标、未指定模块、验收不可测、权限/环境未知。  
- Discovery 暴露与原文冲突的事实。  

无上述情况：可「无歧义」声明。

### 4.4 哪些必须人工确认

- Clarification 关闭歧义。  
- Approval（高风险：删数据、公共 API、权限、跨模块契约）。  
- Review 最终是否接受业务结果。  
- 免批/免澄清声明的签署（即使是文件勾选）。

---

## 5. Delivery Contract（整单 SUCCESS / FAILED）

### 5.1 SUCCESS（全部满足）

1. §4 Requirement 前置条件已满足。  
2. 合同要求的 Stage 均已达到各自 PASS（跳过项有合法声明）。  
3. **Execution** Worker OK 且 Business 变更在 workspace 内。  
4. **Verification** PASS（合同命令成功）。  
5. **Review** 明确接受（或合同定义的自动接受条件——今日默认需报告且无矛盾）。  
6. **Delivery Report** 存在，且 TaskId / Trace / Checkpoint（成功步）/ 成败与 RuntimeResult **一致**。  
7. 未使用本契约 Non Goal 中的能力冒充已实现。

### 5.2 必须 FAILED（任一即可）

1. Verification FAIL。  
2. Execution FAIL 或补丁逃逸。  
3. 应 Clarification/Approval 未做却进入 Execution。  
4. Review 拒绝或报告与事实矛盾。  
5. Delivery Report 缺失关键审计字段或伪造 Runtime 结果。  
6. 将「仅 Demo 绿灯、无 Verification」宣称为生产 SUCCESS。

**注意：** 单步 Runtime Task SUCCEEDED ≠ 整单 Delivery SUCCESS。

---

## 6. Architecture Boundary

```text
Requirement  (Human / Input files)
      │
      ▼
Delivery Pipeline  (Loader · 阶段门闸 · 报告汇总 · SUCCESS 合同)
      │  多次调用（今日：Demo SerialDeliveryRunner）
      ▼
Runtime  (单步 Orchestrator：Task · Context · Trace · Artifact · Checkpoint · RuntimeResult)
      │  Worker SPI
      ▼
Worker  (Shell / FileEdit / …：副作用)
      │
      ▼
Artifact  (Runtime 结算)  +  Business files  (工作区)
      │
      ▼
Review / Delivery Reports  (Delivery 层产物，可再经 Worker 落盘)
```

| 边界 | 允许穿过 | 禁止穿过 |
|------|----------|----------|
| Requirement → Pipeline | 文本、路径、yaml/md | 直接改 Kernel |
| Pipeline → Runtime | `RuntimeRequest` / 多次 submit | 阶段状态机进 Kernel |
| Runtime → Worker | `WorkRequest` + ContextView | Engine 依赖工人实现类 |
| Worker → Artifact | 仅经 Runtime 提交 WorkResult | Worker 自封 COMMITTED |
| → Review | 读 Result + 工作区 | Review「通过」写回改写历史 Trace |

---

## 7. Non Goal（目前绝不实现）

下列名词 **不得**因本契约而被排期为实现，也不得在 Delivery Report 中宣称已具备：

- Scheduler  
- Workflow（DSL / 引擎）  
- Knowledge（库/引擎）  
- Learning  
- Memory（会话记忆平台）  
- AI Worker（Claude/Codex 等）  
- Resume（从 Checkpoint 恢复执行）  
- Platform / Plugin / Capability 体系  

本契约只冻结 **交付语义**；实现仍停留在：Input 文件 + Demo/CLI 编排 + 现有 Runtime + 现有 Worker。

---

## 文档信息

| 项 | 值 |
|----|----|
| 文件 | `docs/engineering-delivery-contract.md` |
| 同伴 | Production Input CONTRACT · sprint-8-production-validation · Frozen `docs/architecture/` |
| 变更 | 改契约须评审；**不**自动授权改 Kernel |
