# Engineering Freeze Report

> **Sprint-2.9 / Task-001**  
> **性质：** 工程收敛（组织 / 依赖 / 文档落位），不是重新设计 Runtime，不是新增能力  

## 结论

**可以进入 Walking Skeleton 可持续开发。**

`mvn clean test`（Java 8）应在 4 模块 reactor 上通过；Kernel 仅依赖 `ai4se-common`；Frozen 架构文档已收拢至 `docs/architecture/`。

---

## 1. 调整内容

### 1.1 模块组织

| 原模块 | 裁决 | 动作 |
|--------|------|------|
| `ai4se-common` | **保留** | 无改职责；新增 `ExecutionContextView`（从 worker-api 上移） |
| `ai4se-worker-api` | **保留** | 删除本地 `ExecutionContextView`；改依赖 common 中的 View |
| `ai4se-kernel` | **保留** | 去掉对 `worker-api` / `capability-api` 的依赖；Enforcer 禁止违规依赖 |
| `ai4se-runtime-engine` | **保留** | Walking Skeleton 入口 |
| `ai4se-capability-api` | **删除空实现** | 已从仓库与 reactor 移除 |
| `ai4se-plugin-api` | **删除空实现** | 同上 |
| `ai4se-rule-api` | **删除空实现** | 同上 |
| `ai4se-skill-api` | **删除空实现** | 同上 |
| `ai4se-model-api` | **删除空实现** | 同上（Provider） |
| `ai4se-scheduler` | **删除空实现** | 概念 **Deferred**（见 §2） |
| `ai4se-host` | **删除空实现** | 概念 **Deferred** |

### 1.2 依赖纠正（Kernel / Core）

| 违规 | 修正 |
|------|------|
| `ai4se-kernel` → `ai4se-worker-api` | `ExecutionContextView` 迁至 `ai4se-common`；Kernel 只依赖 common |
| `ai4se-kernel` → `ai4se-capability-api`（未使用） | 删除依赖与模块 |
| Kernel → Plugin / Rule / Provider / Knowledge | 原本无代码依赖；模块已删，Enforcer 显式禁止 |

目标依赖：

```text
ai4se-runtime-engine
  ├── ai4se-kernel ──────────► ai4se-common
  └── ai4se-worker-api ──────► ai4se-common
```

### 1.3 文档工程（不改设计正文）

| 动作 | 路径 |
|------|------|
| 迁入 Architecture | `docs/architecture/runtime-invariants.md` |
| 迁入 Architecture | `docs/architecture/runtime-enforcement.md` |
| 已在 Architecture | `docs/architecture/kernel/{00,01,02,05,README}.md` |
| 标记 **Frozen** | 上述文档页眉；后续修改必须走 ADR |
| 删除重复 | `docs/kernel-design.md` · `kernel-object.md` · `kernel-uml.md` · `architecture/15-task-lifecycle.md` |
| 归档延期材料 | `docs/_archive/`（原 Blueprint、多余 kernel 章、RFC、多数 ADR、roadmap、freeze-refactor） |
| 瘦身 README | 根 `README.md`：介绍 / 快速开始 / 模块 / 近两 Sprint |
| 本报告 | `docs/engineering-freeze-report.md` |

**未改：** Invariants / Enforcement / Kernel 设计语义与状态机内容（仅路径迁移 + Frozen 标记）。

### 1.4 代码工程（非概念变更）

- `com.ai4se.runtime.common.context.ExecutionContextView`（原 worker-api 接口上移）
- `ExecutionContext` / `Worker` / `MockWorker` import 路径更新
- Kernel Enforcer：`ban-non-kernel-deps`

---

## 2. 延期模块（Deferred）

以下 **不实现、不维护**；概念保留在 `_archive` 文档中，重新启用需新 Sprint + ADR：

| 能力 | 原工程落点 | 状态 |
|------|------------|------|
| Scheduler / WorkItem | 已删 `ai4se-scheduler` | Deferred |
| Host / Spring 启动 | 已删 `ai4se-host` | Deferred |
| Capability / Plugin / Rule / Skill / Model(Provider) | 已删对应 `*-api` | Deferred |
| Knowledge / Profile Engine / Workflow | 无模块，仅归档文档 | Deferred |
| Checkpoint / Trace 服务 | 语义在 Frozen Invariants；无服务模块 | Deferred |

---

## 3. 最终目录结构

```text
ai4se-runtime/
├── README.md
├── pom.xml                          # 4 modules
├── ai4se-common/
├── ai4se-worker-api/
├── ai4se-kernel/
├── ai4se-runtime-engine/
└── docs/
    ├── engineering-freeze-report.md # 本文
    ├── walking-skeleton.md
    ├── architecture/                # Frozen 活动架构
    │   ├── README.md
    │   ├── runtime-invariants.md
    │   ├── runtime-enforcement.md
    │   └── kernel/
    │       ├── README.md
    │       ├── 00-object-model.md
    │       ├── 01-task.md
    │       ├── 02-artifact.md
    │       └── 05-execution-context.md
    ├── adr/                         # 活动 ADR（0007 / 0009 / 0015）
    └── _archive/                    # 延期文档与历史 Blueprint
```

---

## 4. 是否可以进入 Walking Skeleton

| 检查项 | 结果 |
|--------|------|
| Kernel / Invariants / Enforcement 设计未重写 | 是 |
| 无一个月内用不到的空 SPI 模块 | 是（已删） |
| Kernel 不依赖 Worker / Provider / Plugin / Capability / Rule / Knowledge | 是 |
| README 不重复 Architecture | 是 |
| Frozen 文档统一在 `docs/architecture/` | 是 |
| 可运行链路与测试仍在 `runtime-engine` | 是 |

**判定：可以进入 Walking Skeleton 可持续开发。**
