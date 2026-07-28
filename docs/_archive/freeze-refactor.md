# Architecture Freeze Refactor

> **Sprint-2.5 / Task-001**  
> **性质：** 瘦身决策（Freeze），不是重新设计、不是加功能、不是扩文档集  
> **硬冻结（本任务禁止改动）：** Kernel 代码 · `runtime-invariants.md` · `runtime-enforcement.md`  
> **本任务交付物：** 仅本文；**不写 Java**；物理删文件/改 `pom` 留给后续执行本清单的任务  

**目标：** 以最小维护面进入 Walking Skeleton 开发（`Runtime.submit` → Task → Context → MockWorker → Artifact → SUCCEEDED）。

**裁决口径（未来 ~3 个月）：**

| 裁决 | 含义 |
|------|------|
| **保留** | Walking Skeleton 日常依赖；可继续改实现/测例 |
| **合并** | 多份重复叙述只留一份权威；其余从「活动树」移除 |
| **删除** | 对当前骨架无运行价值；从活动树移除（文档或空模块），降低噪音 |
| **延后** | 方向仍有效，但 **不实现、不维护、不扩展**；不参与 WS 迭代 |

---

## 1. 重复文档盘点

### 1.1 Kernel / 对象模型（高重复）

| 簇 | 文件 | 问题 | 裁决 |
|----|------|------|------|
| A | `docs/architecture/kernel/*` | Sprint-0.5 五大对象契约；含 Worker/Scheduler 章节 | **合并后保留内核对象契约子集**（见 §4）；Worker/Scheduler 章 **延后** |
| A′ | `docs/kernel-design.md` | 与 A 同题（边界/职责） | **删除**（并入 A 权威） |
| A′ | `docs/kernel-object.md` | 与 A/`01`–`05` 字段与生命周期重复 | **删除** |
| A′ | `docs/kernel-uml.md` | 与 A 图 + `walking-skeleton` 时序重复 | **删除** |
| B | `docs/architecture/00-overview.md` + `01-runtime-architecture.md` | 总览 + 对象模型，与 A / invariants 重叠 | **延后**（Blueprint 归档，不日常维护） |
| B | `docs/architecture/15-task-lifecycle.md` | 与 kernel/01 + invariants T\* 重复 | **删除**（生命周期以 invariants + Kernel 代码为准） |

### 1.2 索引 / README 过期

| 文件 | 问题 | 裁决 |
|------|------|------|
| 根 `README.md` | 仍写 Sprint-1；模块表含全量 SPI；缺 `runtime-engine` | **保留并瘦身**（仅指向 Freeze + WS；**不在本任务改**，执行清单时改） |
| `docs/architecture/README.md` | 仍指向 Sprint-0.5 / kernel-design 三件套 | **删除或改为一行「见 freeze-refactor」**（执行时处理） |
| `docs/adr/README.md` / `docs/rfc/README.md` | 索引整包 Blueprint | **延后**（归档索引，不扩写） |

### 1.3 Blueprint Engine / 扩展文档 vs Walking Skeleton

下列与当前可运行链路 **无直接代码牵引**，彼此又与 ADR/RFC 三重描述：

| 主题 | Architecture | ADR | RFC | 裁决 |
|------|--------------|-----|-----|------|
| Capability / SDK | `03`, `20` | `0003`, `0014` | `0003` | **延后**（文档归档）；**模块删除** |
| Plugin | `04`, `14` | `0002` | `0002` | 同上 |
| Adapter/Provider | `05` | `0004` | `0007` | 同上（Provider/Model） |
| Repository Graph | `06` | `0005` | — | **延后** |
| Rule / Decision | `07` | — | `0006` | **延后**；无独立 Decision Engine 模块可删 |
| Skill | `08` | — | `0005` | **延后** |
| Workflow | `09` | `0006` | `0004` | **延后** |
| Knowledge | `18` | `0012` | `0011` | **延后** |
| Profile | `19` | `0013` | `0012` | **延后**（WS 仅用 Task 上字符串 profile 字段） |
| Checkpoint / Trace | `16`, `17` | `0010`, `0011` | `0009`, `0010` | **延后实现**；invariants 已冻结语义，**不删 invariants** |
| C4 / 目录 / 扩展指南 | `10`–`14`, `99` | — | — | **延后**（与真实模块树已偏离） |
| Unattended / Tech stack | `21`, `22` | `0008` | — | **延后**（技术栈决策可留 ADR-0008 只读） |

### 1.4 已与代码对齐、应作日常权威的文档

| 文件 | 角色 |
|------|------|
| `docs/runtime-invariants.md` | **硬冻结** · 约束权威 |
| `docs/runtime-enforcement.md` | **硬冻结** · 责任分层权威 |
| `docs/walking-skeleton.md` | **保留** · 当前可运行链路说明 |

---

## 2. 提前设计但当前无代码价值的模块

| 模块 | 现状 | Walking Skeleton 是否用到 | 裁决 |
|------|------|---------------------------|------|
| `ai4se-capability-api` | 仅 SPI 空壳；**无任何 Java import**；`kernel` pom 依赖但未使用类型 | 否（`ProducedBy.capabilityId` 为 `Optional<String>`） | **删除**（后续从 reactor 移除；kernel 去依赖 — **属执行任务，非本任务改 Kernel**） |
| `ai4se-plugin-api` | 空 SPI；仅 `host` 依赖 | 否 | **删除** |
| `ai4se-rule-api` | 空 SPI；仅 `host` 依赖 | 否 | **删除** |
| `ai4se-skill-api` | 空 SPI；仅 `host` 依赖 | 否 | **删除** |
| `ai4se-model-api` | 空 SPI；仅 `host` 依赖 | 否 | **删除** |
| `ai4se-scheduler` | WorkItem/Scheduler 骨架；**engine 未依赖**；WS 由 Engine 内联状态推进 | 否（3 个月内不接真调度） | **删除**（概念 **延后**） |
| `ai4se-host` | 空占位，聚合全部 SPI | 否 | **删除** |
| Decision Engine | **无模块**；仅文档/invariants 中 DecisionRecord | 否 | **延后实现**（不建模块） |
| Knowledge / Profile Engine | **无模块** | 否 | **延后实现** |
| Provider（Model） | `model-api` | 否 | **删除模块 + 延后概念** |

**Walking Skeleton 实际依赖链（已验证）：**

```text
ai4se-runtime-engine
  → ai4se-kernel → ai4se-worker-api → ai4se-common
                 → ai4se-common
                 → (pom 上多余的 capability-api — 待执行清单清除)
  → ai4se-worker-api
  → ai4se-common
```

---

## 3. 判断汇总：保留 / 合并 / 删除 / 延后

### 3.1 保留（活动工作集）

**代码**

- `ai4se-common`
- `ai4se-worker-api`（MockWorker / `WorkRequest` / `ExecutionContextView`）
- `ai4se-kernel`（**本任务不修改**；内容冻结维护）
- `ai4se-runtime-engine`

**文档**

- `docs/runtime-invariants.md`（硬冻结）
- `docs/runtime-enforcement.md`（硬冻结）
- `docs/walking-skeleton.md`
- `docs/freeze-refactor.md`（本文）
- 根 `README.md`（执行瘦身时改为「Freeze + 如何跑测」入口）

**可选只读 ADR（不扩写、不实现）：**

- `adr/0007-runtime-not-agent.md`
- `adr/0009-task-first-class.md`
- `adr/0015-five-kernel-objects.md`（历史决策；与「Worker/Scheduler 非 Kernel」的后续认知冲突时 **以 invariants + 代码为准**，不在本阶段改 ADR）

### 3.2 合并

| 从 | 到 | 说明 |
|----|----|------|
| `kernel-design.md` + `kernel-object.md` + `kernel-uml.md` | `docs/architecture/kernel/` 中与 **Task / Artifact / ExecutionContext** 对应的章节 | 三件套删除；对象字段以 Kernel 代码 + invariants 为准 |
| Blueprint 多份 Task 生命周期叙述 | `runtime-invariants.md` §1 | 删 `15-task-lifecycle.md` |
| 多份「怎么跑起来」叙述 | `walking-skeleton.md` | 时序/验收以 WS 文档为准 |
| 根 README + architecture README 双索引 | 单一根 README | architecture README 删除或极薄指针 |

**合并后 Kernel 文档活动子集（建议执行时保留文件，其余 kernel 章移出活动树）：**

- `architecture/kernel/00-object-model.md`（可标：Worker/Scheduler 为边界协作方，非本阶段实现）
- `architecture/kernel/01-task.md`
- `architecture/kernel/02-artifact.md`
- `architecture/kernel/05-execution-context.md`

`03-worker.md` / `04-scheduler.md` / `06-reconciliation.md` / `99-kernel-acceptance.md` → **延后**（移出活动阅读路径）。

### 3.3 删除（活动树移除；执行任务落地）

**Maven 模块（从 parent `modules` + dependencyManagement + host 引用移除）：**

- `ai4se-capability-api`
- `ai4se-plugin-api`
- `ai4se-rule-api`
- `ai4se-skill-api`
- `ai4se-model-api`
- `ai4se-scheduler`
- `ai4se-host`

**重复 / 过期文档（建议删文件，勿再维护）：**

- `docs/kernel-design.md`
- `docs/kernel-object.md`
- `docs/kernel-uml.md`
- `docs/architecture/15-task-lifecycle.md`

**可选强删（若希望仓库立刻安静；否则并入「延后归档」亦可）：**

- 整目录 `docs/architecture/02-engines.md` … `09-*.md`、`14`、`18`–`20`、`10`–`13`、`99`（与 WS 无耦合）
- 对应「仅扩 SPI」的 RFC：`0002`–`0007`、`0011`、`0012`
- 对应 ADR：`0002`–`0006`、`0012`–`0014`

> **推荐默认：** 模块 **删除**；超前 Blueprint/RFC/ADR **延后归档**（见下），避免误伤历史决策检索。二选一在执行任务中一次做完即可。

### 3.4 延后实现（不写代码、不扩文档、不进 WS 范围）

| 项 | 理由 |
|----|------|
| 真实 Scheduler / WorkItem 租约 | Engine 已内联合法 Task 转移 |
| Capability Gate / Capability SDK | WS 无副作用门禁需求 |
| Plugin 冷加载 | 无扩展加载路径 |
| Rule Engine / Decision Engine | 无策略 DSL；DecisionRecord 仅 invariants 占位 |
| Skill / Workflow Engine | 明确禁入 WS |
| Model Provider / CLI Adapter | 任务禁止接模型与 Claude CLI |
| Knowledge / Repository Graph | Context 未物化此类视图 |
| Checkpoint / Trace 服务实现 | 模型语义在 invariants；WS 不持久化恢复 |
| Project Profile Engine | 仅字符串字段足够 |
| Spring Host / Vue | ADR-0008 只读，不启动 |
| `project-roadmap.md` Phase 1+ 插件/规则等 | 路线图冻结为参考，不驱动本阶段排期 |

---

## 4. 最终目录结构（Freeze 目标树）

> 表示 **执行删除/归档后** 的最小活动树。`_archive/` 为建议落点（执行任务创建）；本任务不落盘。

```text
ai4se-runtime/
├── README.md                          # 瘦身后：如何构建 + 指向 freeze / WS / invariants
├── pom.xml                            # 仅 4 个 jar 模块
├── docs/
│   ├── freeze-refactor.md             # 本文（Freeze 契约）
│   ├── runtime-invariants.md          # 硬冻结
│   ├── runtime-enforcement.md         # 硬冻结
│   ├── walking-skeleton.md            # 当前可运行链路
│   ├── architecture/
│   │   └── kernel/                    # 活动子集（仅对象契约）
│   │       ├── 00-object-model.md
│   │       ├── 01-task.md
│   │       ├── 02-artifact.md
│   │       └── 05-execution-context.md
│   └── _archive/                      # 延后：不维护
│       ├── architecture/              # 原 00–22、plugin/rule/… 等
│       ├── architecture/kernel/       # 03-worker, 04-scheduler, 06, 99…
│       ├── adr/                       # 全量 ADR 只读归档（或只归档非 0007/0009/0015）
│       ├── rfc/                       # 全量 RFC
│       └── project-roadmap.md
├── ai4se-common/
├── ai4se-worker-api/
├── ai4se-kernel/                      # 不在本 Freeze 任务中修改
└── ai4se-runtime-engine/
```

**目标 Maven reactor：**

```text
ai4se-common
ai4se-worker-api
ai4se-kernel
ai4se-runtime-engine
```

---

## 5. 删除清单（执行时一次性完成）

### 5.1 模块

- [ ] 删除目录：`ai4se-capability-api/`
- [ ] 删除目录：`ai4se-plugin-api/`
- [ ] 删除目录：`ai4se-rule-api/`
- [ ] 删除目录：`ai4se-skill-api/`
- [ ] 删除目录：`ai4se-model-api/`
- [ ] 删除目录：`ai4se-scheduler/`
- [ ] 删除目录：`ai4se-host/`
- [ ] 更新根 `pom.xml`：`modules` / `dependencyManagement` 与上表一致
- [ ] 清除 `ai4se-kernel/pom.xml` 对 `ai4se-capability-api` 的未使用依赖（**单独小 PR，仍算「执行 Freeze」，不改 Kernel 业务源码**）

### 5.2 文档（活动树）

- [ ] 删除：`docs/kernel-design.md`
- [ ] 删除：`docs/kernel-object.md`
- [ ] 删除：`docs/kernel-uml.md`
- [ ] 删除：`docs/architecture/15-task-lifecycle.md`
- [ ] 删除或替换为指针：`docs/architecture/README.md`
- [ ]（推荐）将其余 Blueprint / 多余 kernel 章 / RFC / 非关键 ADR / `project-roadmap.md` **移入** `docs/_archive/`（等价于从活动树删除）

### 5.3 明确不删

- [ ] **不删、不改：** `docs/runtime-invariants.md`
- [ ] **不删、不改：** `docs/runtime-enforcement.md`
- [ ] **不改：** `ai4se-kernel/src/**` 业务代码（本 Freeze 任务范围）

---

## 6. 保留清单

| 路径 | 用途 |
|------|------|
| `ai4se-common` | ID / ErrorTaxonomy / Java 8 工具 |
| `ai4se-worker-api` | Worker SPI（MockWorker 契约） |
| `ai4se-kernel` | Task / Artifact / ExecutionContext |
| `ai4se-runtime-engine` | `Runtime.submit` Walking Skeleton |
| `docs/runtime-invariants.md` | 约束权威 |
| `docs/runtime-enforcement.md` | 执法分层权威 |
| `docs/walking-skeleton.md` | 可运行链路与时序 |
| `docs/freeze-refactor.md` | 本 Freeze 契约 |
| `docs/architecture/kernel/{00,01,02,05}.md` | 对象契约只读参考（合并后权威子集） |
| `docs/adr/0007`, `0009`, `0015` | 定位类决策只读 |

---

## 7. 延期实现清单

| 能力 | 触发再开条件（示例） |
|------|----------------------|
| Scheduler / WorkItem 租约与重试 | 需要并发、多 Worker、真排队 |
| Capability Gate + SDK | 需要受控副作用 / 工具权限 |
| Plugin 系统 | 需要热/冷加载第三方扩展 |
| Rule / Decision Engine | 需要可版本化策略 DSL 或放宽权限审计落地 |
| Skill / Workflow | 需要多阶段编排 DSL |
| Model Provider / CLI Worker | 允许接真实模型或 Claude/Codex CLI |
| Knowledge / Repo Graph | Context 需要可查询知识或图谱视图 |
| Checkpoint / Trace 服务 | 需要恢复演练或正式审计导出 |
| Project Profile Engine | 需要多项目差异化策略快照 |
| Spring Host / 对外 API / UI | 需要进程托管或控制台 |

---

## 8. 执行原则（给后续任务）

1. **先删模块与重复文档，再改 README** —— 避免文档继续指向已删模块。  
2. **禁止** 借 Freeze 之名修改 invariants / enforcement / Kernel 状态机语义。  
3. **禁止** 借 Freeze 之名实现 Scheduler/Capability/Rule「顺手补全」。  
4. 任何「延后」项重新进入活动树，必须新开 Sprint，并更新本文「保留/延期」表。  
5. Walking Skeleton 迭代的唯一编排入口保持：`com.ai4se.runtime.engine.Runtime#submit`。

---

## 9. 一句话 Freeze

> **活动面 = 4 个 Maven 模块 + invariants + enforcement + walking-skeleton + 精简 Kernel 对象文档；其余 Blueprint/SPI/Host/Scheduler 一律删除或归档延后，三个月内不维护、不实现。**
