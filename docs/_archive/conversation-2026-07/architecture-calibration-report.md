# Architecture Calibration Report — Sprint-6.1

> **Date:** 2026-07-28  
> **Scope:** Kernel + Checkpoint + Walking Skeleton + Review Foundation  
> **Mode:** 只读校准（本 Sprint **不修改代码**）  
> **Authority:** Frozen Invariants / Enforcement；工程实现以仓库现状为准

---

## Executive Verdict

| 问题 | 结论 |
|------|------|
| 过度设计？ | **局部有、整体可控** — 运行路径已瘦身；文档与状态枚举仍偏「全平台」 |
| Kernel 纯净？ | **基本纯净** — 无 Engine/Workers 反向依赖；少量预留字段/状态未使用 |
| 反向依赖？ | **未发现**（ArchUnit + 模块边界） |
| Walking Skeleton？ | **真正跑通**（Demo + 测试；含 Checkpoint / Trace） |
| 进入 Sprint-7？ | **建议进入，但收窄范围**（见 §9） |

一句话：当前 Runtime **已具备最小可运行闭环**，主要风险不是「缺层」，而是 **文档/枚举超前于代码** 与 **三条技术债**（见 §8）。

---

## 1. 是否存在过度设计？

### 1.1 不过度（应保留）

| 项 | 理由 |
|----|------|
| Maven 多模块（common / worker-api / kernel / engine / workers / demo） | 依赖方向清晰，ArchUnit 可守 |
| Lifecycle Services + Runtime Orchestrator | 避免 God Runtime；符合冻结 Enforcement |
| Checkpoint Domain + SPI + Memory 实现 | Walking Skeleton 已消费；非空转 |
| Worker SPI（与实现分离） | 未来 ClaudeWorker 无需改 Kernel |
| ReviewPackage 工具模块 | 工程能力，零 Runtime 依赖 |

### 1.2 偏过度 / 超前（校准意见）

| 项 | 表现 | 校准 |
|----|------|------|
| Task 状态机「全量枚举」 | `WAITING_DEPENDENCY` / `RETRY_WAIT` / `CHECKPOINTING` / `BLOCKED_POLICY` / `CANCELLED` 在代码中几乎未走通 | 枚举可保留（Frozen），**一个月内不要实现驱动逻辑** |
| Frozen 文档「五大对象」含 Scheduler | Kernel README 仍以 Scheduler 为中枢；实现是 Runtime 直推状态机 | 文档叙事与实现不一致 → **认知债**（非立刻改 Frozen，用 ADR/校准报告对齐） |
| `docs/_archive/` ~54 文件 | Blueprint / Plugin / Capability / Workflow 大量归档 | 保留归档即可，**勿再当实现蓝图** |
| Worker 实现三件套 | `NoopWorker` + `MockWorker` + `CommandWorker` 能力重叠 | 一个月内以 Noop 为主；Mock/Command 作回归即可 |
| Trace → `System.out` | 可观测但粗糙 | 可接受的骨架；勿急上 Observability 栈 |
| Artifact 上 `capabilityId` 等 Optional | ProducedBy 预留 Capability | 字段可留，**勿建 Capability 模块** |

### 1.3 总评

**不是「架构层堆太多」**（没有 Plugin/Scheduler/Workflow 模块）。  
过度主要来自：**Frozen 全景状态机 + 归档蓝图** 相对 **单 Worker 直推 Runtime** 的实现落差。

---

## 2. 未来一个月不会真正使用的模块 / 面

| 类别 | 具体项 | 一个月内预期 |
|------|--------|--------------|
| 未实现子系统 | Scheduler / Workflow / Capability / Plugin / Rule / Knowledge / Provider | **不使用** |
| Kernel 状态 | `WAITING_DEPENDENCY`、`RETRY_WAIT`、`CHECKPOINTING`、`BLOCKED_POLICY`、取消路径 | **不驱动** |
| Kernel 概念 | DecisionRecord（仅 invariants 提及，无代码） | **不使用** |
| API 面 | `WorkerRegistry` | **基本空转**（无注册中心编排） |
| Worker | `CommandWorker`（本地白名单 CLI） | 演示可用，**非主路径** |
| 持久化 | DB / FS Checkpoint·Artifact Store | **继续内存** |
| 恢复 | Resume / Recovery / Retry | **明确不做**（除非 Sprint-7 裁剪纳入） |
| Review 工具 | `ai4se-review-tools` | 低频工程使用，不进 Runtime 热路径 |
| 归档文档 | `docs/_archive/**` | 只读参考，不驱动编码 |

**会用到的：** `kernel`（Task/Artifact/Context/Checkpoint/Trace）、`runtime-engine`、`worker-api`、`NoopWorker`（+ 偶发 Mock）、`demo`、测试与 ArchUnit。

---

## 3. 哪些抽象可以延后？

| 抽象 | 建议延后至 |
|------|------------|
| Scheduler / WorkItem 调度器 | 多 Worker 并行或依赖图真实出现之后 |
| Resume / Recovery 编排 | Checkpoint 有第二写点或崩溃演练需求之后 |
| Capability / Permission 门禁引擎 | 真实危险副作用 Worker 之前 |
| Plugin / Adapter 体系 | 第二个外部系统接入之前 |
| Workflow / Skill / Rule DSL | 无人值守多步编排之前 |
| DecisionRecord | 策略例外（BLOCKED_POLICY）之前 |
| 可插拔 Trace Exporter / OTel | stdout 不够用时 |
| DB/FS Store | 进程外恢复需求出现时 |
| WorkerRegistry 动态发现 | 多 Worker 装配复杂化时 |
| Artifact `supersede` / 完整保留策略 | 多版本产物冲突时 |

**现在就该稳住的抽象：** Task 状态机合法性、Context 三态、Artifact PROPOSED→COMMITTED|ABANDONED、Checkpoint 不可变 + sequence、Worker SPI、Lifecycle Service 门禁。

---

## 4. Kernel 是否仍保持纯净？

### 4.1 依赖纯净 — **是**

```text
common ← worker-api
common ← kernel
worker-api ← workers
common + worker-api + kernel ← runtime-engine
workers / demo 组装注入；review-tools 独立
```

- Kernel **不依赖** engine / workers  
- Workers **不依赖** engine  
- ArchUnit 守护：依赖方向 + Domain mutator 仅经 `engine.service`

### 4.2 概念纯净 — **大体是，有摩擦**

| 检查 | 结果 |
|------|------|
| 无 Scheduler/Plugin 模块污染 Kernel | 通过 |
| Checkpoint / Trace 在 Kernel | 符合对象模型 |
| Worker 实现在 Kernel 外 | 通过 |
| Task 携带未执行的中间态枚举 | 摩擦：文档全量 vs 代码子集路径 |
| `ProducedBy.capabilityId` | 预留字段，未引入 Capability 类型系统 |
| Context 允许 MATERIALIZING→FROZEN（early fail） | 代码 TODO 与 Invariants「须经 Active」叙事有张力 |

**结论：** Kernel **实现边界纯净**；与 Frozen「五大含 Scheduler / 全状态机」的**叙事**不完全同构——这是校准重点，不是立刻重构理由。

---

## 5. Dependency 是否存在反向依赖？

| 规则 | 状态 |
|------|------|
| Kernel ↛ Engine | ✅ ArchUnit |
| Kernel ↛ Workers | ✅ ArchUnit |
| Engine ↛ Workers 实现 | ✅ ArchUnit（仅 worker-api） |
| Workers ↛ Engine | ✅ ArchUnit |
| Worker-api ↛ Kernel/Engine/Workers | ✅ ArchUnit |
| Package slices 无环 | ✅ ArchUnit |
| review-tools ↛ Runtime 域模块 | ✅ Maven enforcer |

**未发现反向依赖。**  
注意：测试与 demo **允许**依赖 workers（正确组装层）。

---

## 6. Walking Skeleton 是否真正跑通？

**是。** 证据链：

| 证据 | 内容 |
|------|------|
| 代码路径 | `Runtime.submit`：Validate→Context→Trace→Worker→Artifact→Checkpoint→SUCCEEDED→freeze/close |
| Demo | `WalkingSkeletonMain` + `NoopWorker` → 控制台 `[OK]` 五项断言 |
| 测试 | `WalkingSkeletonTest`、`CheckpointIntegrationTest`、`RuntimeIntegrationTest`、`RuntimeWalkingSkeletonTest` |
| 产物 | COMMITTED `hello.txt`；MemoryCheckpoint sequence≥1；Trace spans `submit/worker/artifact/finish` + `closed` |

**已知非缺陷边界（Design Note 已记）：**

- 不进入 `CHECKPOINTING` 状态（Foundation 选择）  
- Checkpoint 在 SUCCEEDED 之前写入（因 `markCheckpoint` 禁终态）  
- `RuntimeResult` 不携带 checkpointId（测/Demo 查 Store）

这些是**有意识的范围裁剪**，不是「没跑通」。

---

## 7. Invariant：代码保证 vs 仍停在文档

### 7.1 已由代码（+测试/ArchUnit）实质保证

| ID | 如何保证 |
|----|----------|
| G1 | 唯一入口 `Runtime.submit` 创建 Task |
| G2 | 对外以 ArtifactId 列表返回；Worker 不直写下游契约 |
| G3 | 一次 materialize + bind；禁止换绑 |
| G4 | 终态路径 `contexts.freeze` |
| G5 | 仅 COMMITTED 可 `indexCommitted`；失败走 ABANDONED |
| G6 | Checkpoint/Trace 构造强制 taskId |
| T1–T4 | TaskId / TaskTransitions / bind once / 终态禁 transition |
| T6（部分） | goal/workspace 等 builder 身份字段；运行中不提供偷换 API |
| T8（部分） | status / terminal / context / lastError 可查询 |
| A3–A5 | propose→commit/abandon 状态机 |
| E1–E4（主路径） | MATERIALIZING→ACTIVE→FROZEN；索引仅 ACTIVE+COMMITTED |
| C1–C3（主路径） | taskId、单调 sequence、save 不可覆盖 |
| C5（结构） | 无 integrity 不可构造有效 Checkpoint |
| R1–R6（主路径） | Trace 绑定 Task；append/close；不改 Task 状态 |
| 依赖方向 | ArchUnit |

### 7.2 仍主要停在文档 / 弱保证

| ID | 缺口 |
|----|------|
| G7 | ErrorTaxonomy 存在，但未强制覆盖所有失败分类语义 |
| T5 | budgetRemaining 上下限运行时未系统校验 |
| T7 | permissions「只收紧」无执行器 |
| I4（Kernel 对象模型） | 「Scheduler 唯一推进状态机」— **实现是 Runtime Engine 推进**，与文档冲突 |
| C4 | `latestCheckpointId` 有更新；与「sequence 最大有效点」缺跨失败/多点系统测试 |
| C6 | Resume 未实现，无法在恢复路径验证 |
| E* 分区视图 | Profile/Graph/Knowledge/Rule View 未物化 |
| Artifact 可见性/敏感度 | 枚举存在，无强制门禁 |
| CANCELLED / 策略阻塞 / 重试 | 状态合法但无产品路径 |
| DecisionRecord | 无代码 |
| Idempotency / I6 | 无幂等存储 |

### 7.3 校准含义

**主路径 Invariants 已「可执行」；平台级 Invariants 仍「可叙述」。**  
进入 Sprint-7 时应优先补 **与执行相关的弱项**（错误 taxonomy、预算、I4 叙事 ADR），而不是一次性实现全部文档状态。

---

## 8. 当前最大的三个技术债

### TD-1 — Frozen 叙事 vs 实现：谁推进 Task？

- **现象：** Invariants/Kernel README 以 Scheduler 为状态机权威；代码由 `Runtime` + Lifecycle Services 直推。  
- **风险：** 新人按文档实现第二套调度器 → 双推进。  
- **建议：** Sprint-7 前用 **ADR** 声明「v0 Walking Skeleton：Engine 暂代 Scheduler；Scheduler 引入前不得并行推进」。

### TD-2 — Task 全量状态机未使用子集未标注

- **现象：** 中间态/取消/CHECKPOINTING 合法但无路径；Checkpoint 又不走 CHECKPOINTING。  
- **风险：** 误以为「缺实现即缺陷」或错误补全导致复杂度爆炸。  
- **建议：** 在 Design Note / Calibration 中固定 **Supported subset**；其余状态 **Explicitly deferred**。

### TD-3 — 可观测与结果面偏薄

- **现象：** Trace 打 stdout；`RuntimeResult` 无 checkpointId；失败 Checkpoint 策略未定；Context early-freeze 与文档张力。  
- **风险：** Sprint-7 做引擎时难以断言「执行边界」，调试靠日志字符串。  
- **建议：** 小步补齐 API/观测（非新 Domain）：Result 暴露 checkpoint 指针、Trace sink 可替换、统一失败 Checkpoint 政策（ADR）。

> 次级债（不进 Top3）：Mock/Noop 重复、CommandWorker 与主路径偏离、`docs/_archive` 认知噪音、budget/permission 未执行。

---

## 9. 是否建议进入 Sprint-7（Execution Engine）？

### 建议：**可以进入，但必须收窄定义**

**赞成进入的理由**

1. Walking Skeleton **已闭环**（含 Checkpoint），Kernel 未阻塞。  
2. 依赖方向与 Lifecycle 门禁 **已可守**。  
3. 再堆工程脚手架收益递减；需要「真执行」才能暴露下一波 Invariant 缺口。

**反对「大而全 Execution Engine」的理由**

1. Scheduler / Workflow / Capability 仍属延后抽象。  
2. Top3 技术债偏 **校准与契约**，不是缺一个新引擎模块。  
3. 若 Sprint-7 = 引入 Scheduler+多 Worker+Resume，会再次过度设计。

### 建议的 Sprint-7 范围（校准后）

| 做 | 不做 |
|----|------|
| 明确 Execution Engine = **编排增强**（多步 WorkRequest、失败策略、结果面），仍经现有 Lifecycle | 不新建 Scheduler 模块（除非 ADR） |
| 可选：第二个真实一点的 Worker（仍 SPI） | 不接 Claude/Cursor 除非单独 ADR |
| 补齐 TD-1 ADR + Result/观测小步 | 不上 Plugin/Capability/Workflow |
| Checkpoint 政策（失败是否写）ADR | 不做完整 Resume/Recovery |

### Go / No-Go

| 选项 | 决议 |
|------|------|
| **Go（收窄的 Sprint-7）** | ✅ **推荐** |
| Go（完整 Scheduler+Resume+多引擎） | ❌ 不推荐 |
| No-Go（继续只做工程基建） | 仅当团队优先还 TD-1/TD-2 文档债一周 |

---

## Appendix A — 当前模块地图（实现）

```text
ai4se-common          IDs / ErrorTaxonomy / ExecutionContextView
ai4se-worker-api      Worker SPI
ai4se-kernel          Task Artifact Context Checkpoint Trace
ai4se-runtime-engine  Runtime + Lifecycle Services + Memory stores
ai4se-workers         NoopWorker MockWorker CommandWorker
ai4se-demo            WalkingSkeletonMain DemoMain
ai4se-review-tools    ReviewPackage generator（工程）
review-package/       Schema + templates
```

## Appendix B — Supported Task path（v0）

```text
CREATED → VALIDATING → QUEUED → SCHEDULED → STARTING → RUNNING → SUCCEEDED
                                                      ↘ FAILED
```

Deferred：`WAITING_DEPENDENCY` · `RETRY_WAIT` · `CHECKPOINTING` · `BLOCKED_POLICY` · `CANCELLED` 产品路径。

---

## Document Control

| 字段 | 值 |
|------|----|
| Sprint | 6.1 Architecture Calibration |
| Code changes | **None** |
| Follow-up | ADR for Engine-as-Scheduler-v0；Sprint-7 范围冻结会 |
