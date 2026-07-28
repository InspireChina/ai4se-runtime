# Runtime Invariants

> **Status: Frozen**  
> 任何后续修改必须通过 ADR。  
>
> **Sprint-1 / Task-004**  
> **Scope:** 已确认的 Runtime Kernel 对象  
> **禁止：** 新增对象 · 改模块 · 写 Java · 讨论实现 / Provider / Workflow / CLI  

本文只回答一件事：

> **Runtime 永远不能违反哪些约束？**

权威对象集（不新增）：

| Kernel Object |
|---------------|
| Task |
| Artifact |
| ExecutionContext |
| Checkpoint |
| Trace |
| DecisionRecord（若 Kernel 保留该薄模型；见 §6） |

---

## 0. 全局 Invariants（跨对象）

| ID | Invariant |
|----|-----------|
| G1 | 一切可结算的工作必须以 **Task** 为根；不得用隐式全局状态代替 Task。 |
| G2 | 阶段之间传递产物只允许 **ArtifactId**（或等价不可变引用）；禁止靠未登记的临时副作用传递契约结果。 |
| G3 | **ExecutionContext** 是 Task 执行期唯一合法上下文视图；禁止并行存在第二套「暗上下文」。 |
| G4 | Task 进入终态后，其 ExecutionContext **必须冻结**；冻结后禁止写入。 |
| G5 | 仅 **COMMITTED** Artifact 可作为下游契约输入。 |
| G6 | Checkpoint / Trace 必须可回溯到唯一 `taskId`；禁止无主 Task 的孤儿耐久点/轨迹作为正式恢复依据。 |
| G7 | 错误分类必须落入稳定 taxonomy（如 VALIDATION / RETRYABLE / FATAL / NEEDS_POLICY_EXCEPTION）；禁止用厂商/临时字符串冒充平台终态语义。 |

违反 G\* 的后果（共性）：**不可恢复、不可审计、不可无人值守复盘** → Runtime 失去平台资格。

---

## 1. Task

### 1.1 为什么存在？

Task 是 Runtime 唯一的 **工作单元主实体**：一次可约束、可推进、可终态结算、可审计的软件工程工作请求的根。

没有 Task，就没有统一的预算、权限、恢复与终态。

### 1.2 生命周期是什么？

```text
CREATED
  → VALIDATING
  → QUEUED → SCHEDULED → STARTING → RUNNING
       ↺ WAITING_DEPENDENCY / RETRY_WAIT / CHECKPOINTING / BLOCKED_POLICY
  → SUCCEEDED | FAILED | CANCELLED
```

终态：`SUCCEEDED` · `FAILED` · `CANCELLED`。终态不可再离开。

### 1.3 永远不能违反的约束（Invariant）

| ID | Invariant |
|----|-----------|
| T1 | 每个 Task 有且仅有一个全局唯一 `taskId`。 |
| T2 | Task 状态转移必须落在合法转移表内；非法转移视为缺陷，不得静默发生。 |
| T3 | 终态 Task 禁止再次 `transitionTo` 非终态或其它终态（除只读观测外无生命周期变更）。 |
| T4 | 一个 Task 至多绑定一个 ExecutionContext；绑定后禁止更换。 |
| T5 | `budgetRemaining` 的任何维度不得超过对应 `budget` 上限，且不得为负。 |
| T6 | `runMode`、`goal`、`workspaceRef`、创建时的 `budget` 上限、`profileRevision` 在创建后视为不可替换身份字段（可观测，不可偷换）。 |
| T7 | 权限集在运行中只允许 **收紧**，不允许暗中放宽（放宽必须走显式策略例外记录，见 DecisionRecord）。 |
| T8 | Task 必须始终可回答：当前 `status`、是否终态、是否已绑定 Context、是否有 `lastError`。 |

### 1.4 必须禁止的行为

- 跳过状态机「发明」中间态或直接标记成功而未经过合法路径语义。  
- 终态后继续写入预算、权限、Context 绑定、Checkpoint 指针（只读除外）。  
- 同一 `idempotencyKey`（若使用）创建出语义冲突的第二个存活 Task 而不去重。  
- 用空/`null` goal 或空白 workspace 进入 `RUNNING`。  
- 让 Task 同时处于两个状态。  

### 1.5 必须保证的行为

- 从 CREATED 到终态的每一步都有可观测的状态变迁。  
- STARTING 成功路径上完成 Context 绑定；失败路径进入 FAILED 并保留原因。  
- 终态时触发 Context 冻结（与 G4 一致）。  
- 取消路径从允许取消的非终态进入 CANCELLED，且此后只读。  

### 1.6 违反的后果

- 双状态 / 非法跳跃 → **调度与恢复互相矛盾**，出现重复执行或永远卡死。  
- 终态仍可变 → **审计作废**，无法证明「已完成」。  
- 偷换 workspace/profile → **跨项目串扰**，无人值守结果不可信。  
- 预算为负或超上限未拦截 → **资源失控**。  

---

## 2. Artifact

### 2.1 为什么存在？

Artifact 是 Runtime 的 **产物货币**：所有阶段结果必须以可寻址、可校验、可传递的统一形态存在。

没有 Artifact，阶段契约只能靠隐式副作用，恢复与复用会失败。

### 2.2 生命周期是什么？

```text
PROPOSED → COMMITTED → (SUPERSEDED | EXPIRED)
PROPOSED → ABANDONED
```

### 2.3 永远不能违反的约束（Invariant）

| ID | Invariant |
|----|-----------|
| A1 | 每个 Artifact 有全局唯一 `artifactId`，且必须归属恰好一个 `taskId`。 |
| A2 | `kind`、`storage`、`mediaType` 在创建后不可偷换（身份字段不变）。 |
| A3 | 仅 `PROPOSED` 可变为 `COMMITTED` 或 `ABANDONED`；`COMMITTED` 不得再变回 `PROPOSED`。 |
| A4 | `COMMITTED` 禁止 `abandon`。 |
| A5 | 任何下游「契约输入」只允许引用 `COMMITTED` Artifact。 |
| A6 | `COMMITTED` 的内容定位（storage locator + checksum 策略）必须足够支撑完整性核对；不得在无替换身份的情况下改写已提交内容语义。 |
| A7 | `visibility=RESTRICTED` 的 Artifact 不得对未授权视图暴露正文。 |
| A8 | `sensitivity` 标明的敏感产物不得以明文进入通用只读视图的默认可导出面。 |

### 2.4 必须禁止的行为

- 把 `PROPOSED` / `ABANDONED` 当作下游正式输入。  
- 原地篡改已 `COMMITTED` 的逻辑内容却保留同一 `artifactId` 假装未变。  
- 创建无 `taskId` 的 Artifact 并纳入正式索引。  
- 用未登记文件路径冒充已提交产物。  

### 2.5 必须保证的行为

- 成功路径：产出先 `PROPOSED`，接受后 `COMMITTED`，并进入所属 Task 的 Context 索引。  
- 失败/取消路径：未提交产物进入 `ABANDONED`（或等价不可消费态），不得残留为合法输入。  
- 每个 `COMMITTED` Artifact 可被 `artifactId` 取回元数据。  

### 2.6 违反的后果

- 消费未提交产物 → **幻读**；恢复后结果漂移。  
- 篡改 COMMITTED → **审计链断裂**，无法证明历史。  
- 无主 Artifact → **跨 Task 污染**。  
- 敏感明文外泄 → **安全事故**。  

---

## 3. ExecutionContext

### 3.1 为什么存在？

ExecutionContext 是 Task 执行期的 **唯一合法上下文**：把目标、权限、预算剩余、产物索引与只读快照视图收束到一处，避免全局单例与暗状态。

### 3.2 生命周期是什么？

```text
Materializing → Active → Frozen
```

- Materializing：与 Task STARTING 对齐，完成绑定前的装配。  
- Active：可索引 COMMITTED Artifact；受控可变分区可按规定写入。  
- Frozen：Task 终态后；只读。  

### 3.3 永远不能违反的约束（Invariant）

| ID | Invariant |
|----|-----------|
| E1 | 与 Task **1:1**；禁止一个 Context 服务多个 Task，或一个 Task 绑定多个 Context。 |
| E2 | Immutable Snapshot Zone 在进入 Active 后禁止被替换或重写。 |
| E3 | `frozen=true` 后禁止任何写入（含索引追加、memory、budgetRemaining 经由 Context 的写入路径）。 |
| E4 | Artifact 索引只追加 **COMMITTED** 的 `artifactId`；禁止索引未提交产物。 |
| E5 | 对外执行面只通过 **View** 暴露；View 不得暴露调度内部结构或未授权产物。 |
| E6 | Controlled Mutable Zone 只允许白名单键；禁止任意键空间污染。 |
| E7 | Context 的 `taskId` 必须与所属 Task 一致，永不漂移。 |

### 3.4 必须禁止的行为

- 冻结后写入。  
- 用第二套 ThreadLocal/全局 Map 旁路 Context。  
- 在 Active 期替换 profile/graph 等不可变快照身份。  
- 让 Worker 面看到与本 Task 无关的 Context。  

### 3.5 必须保证的行为

- Task 成功进入可执行态前，Context 已绑定。  
- Task 终态时 Context 已冻结。  
- `listArtifactIds`（或等价）与真实 COMMITTED 索引一致。  
- View 在 frozen 前后均可安全只读。  

### 3.6 违反的后果

- 双上下文 → **决策与产物对不上号**。  
- 冻结后仍写 → **终态谎言**，复盘失败。  
- 索引未提交产物 → 与 A5 叠加，造成级联幻读。  
- 快照被偷换 → **跨项目/跨配置串扰**。  

---

## 4. Checkpoint

### 4.1 为什么存在？

Checkpoint 是 Task 在可信边界上的 **耐久恢复点**：使无人值守运行在中断后仍能从一致点继续，而不是从头猜测。

### 4.2 生命周期是什么？

```text
Created (at durable boundary)
  → Retained (readable)
  → (可选) Pruned by retention — 但不得破坏「最新可用点」语义承诺
```

Checkpoint 本身不是 Task 状态；它挂在 Task 上，随 Task 存活期被创建与保留。

### 4.3 永远不能违反的约束（Invariant）

| ID | Invariant |
|----|-----------|
| C1 | 每个 Checkpoint 必须引用恰好一个存在的 `taskId`。 |
| C2 | 同一 Task 内 `sequence` 严格单调递增。 |
| C3 | Checkpoint 一旦创建，其载荷摘要（cursor/context 修订/artifact 索引摘要/integrity）**不可篡改**；更正只能新增更高 sequence。 |
| C4 | `Task.latestCheckpointId`（若存在）必须指向该 Task 下 sequence 最大的有效 Checkpoint，或显式为空。 |
| C5 | 用于恢复的 Checkpoint 必须通过完整性校验（integrity）；校验失败不得当作合法恢复点。 |
| C6 | Checkpoint 不得成为「无 Task 终态约束」的后门：恢复后仍须遵守 Task/Context/Artifact 全部 Invariants。 |

### 4.4 必须禁止的行为

- 改写历史 Checkpoint 载荷。  
- 用其它 Task 的 Checkpoint 恢复本 Task。  
- 在完整性失败时强行继续并假装一致。  
- 恢复后绕过状态机直接进入终态成功。  

### 4.5 必须保证的行为

- 在约定的耐久边界能够形成可引用的 Checkpoint。  
- 恢复路径只从合法 Checkpoint 出发，并保持 TaskId/Context 一致。  
- 恢复不破坏已 COMMITTED Artifact 的身份与索引事实。  

### 4.6 违反的后果

- 篡改/错用 Checkpoint → **错误续跑**，可能重复危险副作用或丢失进度。  
- 完整性忽略 → **静默腐败状态**。  
- 跨 Task 恢复 → **项目级串扰**。  

---

## 5. Trace

### 5.1 为什么存在？

Trace 是 Task 执行的 **可观测轨迹根**：支撑排障、审计、成本与无人值守复盘。

没有 Trace，终态与中间决策无法被证明。

### 5.2 生命周期是什么？

```text
TraceRoot opened (with Task start path)
  → Spans appended
  → TraceRoot closed (with Task terminal)
```

历史只追加，不改写。

### 5.3 永远不能违反的约束（Invariant）

| ID | Invariant |
|----|-----------|
| R1 | 每个正式运行的 Task 至多一个权威 `traceId` 根（与 Task 绑定后不可偷换）。 |
| R2 | 所有 Span 必须归属该 `traceId`，且可追溯到 `taskId`。 |
| R3 | Span 树不允许环；`parentSpanId` 必须指向同 Trace 内已存在 Span（根除外）。 |
| R4 | 已结束的 Span 禁止改写其结束状态与关键属性（更正用新 event，不改写历史结论）。 |
| R5 | Task 终态后 TraceRoot 必须关闭；关闭后禁止再开新的正式执行 Span。 |
| R6 | Trace 不得替代 Task 状态机：轨迹是投影，不是第二套生命周期权威。 |

### 5.4 必须禁止的行为

- 无 Task 的「正式」Trace 作为平台审计依据。  
- 改写已关闭 Span 的成功/失败结论。  
- 用 Trace 事件回放去偷偷修改 Task/Artifact 状态（观测反向写权威状态）。  

### 5.5 必须保证的行为

- Task 关键执行路径有可关联的 TraceRoot。  
- 关键状态变迁与产物提交在轨迹上可关联（至少能关联到 taskId/artifactId）。  
- 终态后轨迹只读可查。  

### 5.6 违反的后果

- 无主/可改写轨迹 → **审计无效**，事故不可复盘。  
- Trace 反向写状态 → **双权威**，与 T2 冲突，系统不可推理。  
- 终态后仍写执行 Span → **假装仍在运行**。  

---

## 6. DecisionRecord（若 Kernel 保留）

> 不新增对象类型超出已讨论的薄模型。若 Runtime **不保留** DecisionRecord，则本节不适用；其约束不得用 Rule 内容或 Prompt 暗中替代。

### 6.1 为什么存在？

DecisionRecord 是 append-only 的 **运行时决策事实**：记录允许/拒绝/策略例外等结论，使无人值守路径可复盘。

它不是规则引擎，不承载规则正文。

### 6.2 生命周期是什么？

```text
Appended → Immutable forever
```

只追加，不修改，不删除（归档除外且需保留审计可达性）。

### 6.3 永远不能违反的约束（Invariant）

| ID | Invariant |
|----|-----------|
| D1 | 每条记录必须绑定 `taskId`（及可选 spanId）。 |
| D2 | 记录一旦追加不可改写结论字段。 |
| D3 | 影响控制流的拒绝/例外，必须有对应 DecisionRecord（或等价强制审计事件）；禁止「静默拒绝」。 |
| D4 | 权限放宽类结论必须留下 DecisionRecord；禁止无记录放宽。 |
| D5 | DecisionRecord 不得充当第二套 Task 状态权威（与 R6 同理）。 |

### 6.4 必须禁止的行为

- 改写历史决策。  
- 无记录的控制性拒绝/放宽。  
- 把决策记录当作可执行脚本或可变策略库。  

### 6.5 必须保证的行为

- 关键门禁结论可追溯。  
- 与 Trace/Task 可关联。  

### 6.6 违反的后果

- 无记录的拒绝/放宽 → **治理真空**，无人值守不可信。  
- 可改写决策 → **审计伪造**。  

---

## 7. Invariant 索引（速查）

| 对象 | ID 前缀 | 条数 |
|------|---------|------|
| Global | G | 7 |
| Task | T | 8 |
| Artifact | A | 8 |
| ExecutionContext | E | 7 |
| Checkpoint | C | 6 |
| Trace | R | 6 |
| DecisionRecord | D | 5（若保留） |

---

## 8. 违反时的统一态度

1. **检测：** 非法转移、冻结后写入、消费未提交产物等必须可被识别为缺陷。  
2. **不掩盖：** 不得把违反 Invariant 的路径标为 SUCCEEDED。  
3. **可归因：** 失败须落到稳定错误语义，并关联 Task/Trace。  

Invariant 高于便利。任何「先跑通再补约束」若破坏上表，均视为架构违规，而不是实现细节。
