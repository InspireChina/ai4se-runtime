# Requirement Analysis Pipeline Contract

> **Status:** Contract（规范）· 非实现  
> **位置：** Delivery 前半段 —— **Requirement → Planning 就绪**  
> **不是：** Runtime 如何执行；不修改 Runtime / Worker / Kernel Object  
> **Non Goal：** Scheduler · Workflow · StageRunner · Knowledge · Learning · AI Worker · Resume  
> 能力域 **03 · Workflow · Analysis**。输出：**Analysis Bundle**。  
> **同伴：** [`capability-map.md`](../../../../00-product/capability-map.md) · [`context-engineering-spec.md`](../../../20-context-engineering/context-engineering-spec.md) · Engine：[`70-runtime-foundation`](../../../70-runtime-foundation/README.md)  

本文只回答：

> **一句自然语言需求，怎样变成可以安全交给 Delivery / Runtime 的规格？**

---

## 0. 管道位置

```text
一句自然语言
      │
      ▼
【 Requirement Analysis Pipeline 】  ← 本文
  Analysis → Discovery? → Gap → Clarification? → Planning → Human Gate
      │
      ▼
Delivery Pipeline（Execution → Verification → Review → Delivery）
      │
      ▼
Runtime.submit（单步）→ Worker → Artifact → …
```

**硬边界：** 本管道 **禁止 Coding / Execution / 改业务源码**。  
产出止于 **Planning 产物 + 人工闸通过**；之后才进入 Delivery 的 Execution。

**仓库侧两层产物（详见 [`../../../10-repository-intelligence/repository-facts-contract.md`](../../../10-repository-intelligence/repository-facts-contract.md)）：**

- **Repository Facts** — Analyzer 只出事实  
- **Repository Context** — Pipeline 从 Facts+Spec 构建；Planning / Clarification 主消费 Context  

---

## 1. Requirement Analysis

### 1.1 定义

| 项 | 内容 |
|----|------|
| **输入** | 一句（或一小段）自然语言需求 |
| **输出** | **Requirement Spec**（结构化、可验收，仍无实现方案） |
| **职责** | **只理解需求**：对象、意图、范围、验收口径、约束 |
| **禁止** | Coding、写补丁、选框架细节、改仓库、调用 Execution Worker |

### 1.2 Requirement Spec 最低字段

| 字段 | 必须 | 说明 |
|------|------|------|
| `raw` | 是 | 原始自然语言 |
| `goal` | 是 | 一句话目标（无实现词堆砌） |
| `in_scope` | 是 | 包含什么 |
| `out_of_scope` | 是 | 明确不含什么 |
| `acceptance` | 是 | 可检验的通过条件（可测或可人工勾选，须写明类型） |
| `constraints` | 否 | 环境、兼容、禁止事项 |
| `actors` | 否 | 谁触发/谁受益 |
| `open_questions` | 否 | Analysis 阶段已察觉的疑点（供 Gap） |

### 1.3 PASS / FAIL

| | 条件 |
|--|------|
| **PASS** | 上表必填齐全；`acceptance` 非空且非「看着办」；未包含实现步骤冒充需求 |
| **FAIL** | 空目标、无验收、范围自相矛盾、夹带「请直接改某某类」却无 Spec 字段 |

Analysis **PASS 不等于**可以 Planning：还必须过 Gap（§3）与（若触发）Clarification（§4）。

---

## 2. Discovery

### 2.1 只回答什么

> Discovery **只能**回答：**「项目现在是什么样。」**  
> **禁止**回答：**「应该怎么实现。」**

允许：模块地图、相关路径命中、依赖/配置现状、现有测试位置、与需求可能相关的符号列表。  
禁止：推荐设计、任务拆解、选定 API 形状、直接给补丁。

### 2.2 何时必须 / 可跳过

| 必须 Discovery | 允许跳过（须书面记录 Skip Rationale） |
|----------------|----------------------------------------|
| 目标仓库对作者不熟 | 作者对影响面钉死且能指出精确路径 |
| Spec 未点名模块/文件 | 样本工程 + Spec 已写死路径（如 Production sample） |
| 验收依赖「现状行为」 | 纯文档/纯新增空模块且无存量耦合 |
| Gap 预检已标 `needs_discovery` | 合同声明「零摸底交付」且 Human Gate 批准跳过 |

### 2.3 输出 Artifact（Delivery 层，非新 Kernel 类型）

| Artifact | 内容 |
|----------|------|
| `discovery.report` | 事实摘要（路径、模块、依赖、现状行为摘录） |
| `discovery.hit-set`（可选） | 相关路径 / 符号 TopK |
| `discovery.skip`（若跳过） | 跳过理由 + 批准人/时间 |

Runtime 若执行摸底命令：仅结算该步 `WorkResult`→Artifact；**语义上仍是 Delivery 的 Discovery 产物**。

### 2.4 PASS / FAIL

| | 条件 |
|--|------|
| **PASS** | 有 report 或合法 skip；内容无「实现建议」段落 |
| **FAIL** | 应摸底却空；或报告写成设计方案 |

---

## 3. Gap Analysis（独立阶段）

### 3.1 目标

判断：**仅凭 Requirement Spec +（可选）Discovery，是否足够进入 Planning。**

### 3.2 输出：Gap Report

| 分区 | 含义 |
|------|------|
| **Known** | 已证实的事实（来自 Spec/Discovery） |
| **Unknown** | 仍不知、且影响方案或验收的问题 |
| **Assumption** | 准备采用的假设（须标置信度：低/中/高） |
| **Risk** | 若假设错误的后果 |
| **Decision Needed** | 必须人做的选择（产品/范围/兼容） |

另需机器可检字段：

| 字段 | 类型 | 说明 |
|------|------|------|
| `blocking_gap_count` | int | **阻塞性** Unknown + 未批准的 Decision Needed 条数 |
| `assumable_gap_count` | int | 允许 AI 低风险假设的条数（见 §4.3） |
| `gap_status` | enum | `CLEAR` / `ASSUMABLE` / `BLOCKED` |

### 3.3 Gap 是否允许继续（硬规则）

定义：

- **Blocking Gap**：缺了会导致错误模块、错误验收、破坏兼容/安全/数据，或产品二选一未定。  
- **Assumable Gap**：有合理默认、失败成本低、且写入 Assumption 可逆（见 §4.3 白名单）。

| `gap_status` | 条件 | 下一步 |
|--------------|------|--------|
| **CLEAR** | `blocking_gap_count == 0` 且无未批准 Decision Needed | → Planning（可经 Human Gate 视风险） |
| **ASSUMABLE** | `blocking_gap_count == 0` 且仅剩 Assumable；假设已写入 Gap Report | → Planning（假设必须带进 Plan；**`AssumablePolicy.REQUIRE_ACK` 时须 `assumable.ack.md`**；高风险域仍要 Human Gate） |
| **BLOCKED** | `blocking_gap_count > 0` | → **禁止 Planning** → Clarification（§4） |

**「Gap 为多少必须停止」：**

- **停止阈值 = `blocking_gap_count > 0`**（不是按「未知条数好看」，而是按是否阻塞）。  
- 即使 Unknown 很多，若全部被正式降级为 Assumable 且无 Decision Needed，可为 `ASSUMABLE`。  
- **禁止**把 Blocking 改名为 Assumable 以强行 CLEAR。

### 3.4 PASS / FAIL

| | 条件 |
|--|------|
| **PASS** | Gap Report 五区齐全；`gap_status` 与计数一致；无「实现方案」冒充 Known |
| **FAIL** | 无 Gap Report 却声称进 Planning；或 status 与内容矛盾 |

---

## 4. Clarification（条件触发，非固定阶段）

### 4.1 触发规则（必须遵守）

```text
Gap Analysis
    │
    ├─ gap_status == CLEAR 或 ASSUMABLE
    │       └─►（不强制 Clarification）→ Planning 路径
    │
    └─ gap_status == BLOCKED
            └─► Generate Questions
                    └─► BLOCKED（交付态：不得 Planning / Execution）
                            └─► Human Answer（Artifact/记录）
                                    └─► Gap Re-check
                                            ├─ 仍 BLOCKED → 继续问 / 升级 Approval
                                            └─ CLEAR | ASSUMABLE → Planning 路径
```

**Clarification 不是流水线上永远存在的一格**；只在 `BLOCKED` 时出现。

### 4.2 产物

| 产物 | 说明 |
|------|------|
| `clarification.questions` | 问题列表（每问对应一条 Blocking Gap） |
| `clarification.answers` | 人工答案 |
| `gap.report`（更新） | Re-check 后的新 status |

### 4.3 哪些可假设 / 必须问人 / 必须 Approval

| 类别 | 规则 | 例子 |
|------|------|------|
| **AI 可假设（Assumable）** | 有社区/项目默认；错了易改；不改对外契约；写入 Assumption | 日志级别默认 INFO；内部方法命名风格；测试框架沿用仓库现有 |
| **必须问人（Blocking → Clarify）** | 影响行为对错、范围、数据、安全、多解产品意图 | 「超时是 3s 还是 30s？」「是否兼容旧 API？」「改 A 模块还是 B？」 |
| **必须 Approval** | 不可逆、对外、合规、跨模块契约、删数据/放权 | 公共 REST 变更、鉴权放宽、DB schema、跨服务协议、生产配置默认值 |

**禁止：** AI 对 Approval 类问题自行「拍板」后进入 Planning。

---

## 5. Planning

### 5.1 输入（必须齐全）

必须来自：

1. **Requirement Spec**（Analysis PASS）  
2. **Discovery** 产物或合法 `discovery.skip`  
3. **Gap Report** 且 `gap_status ∈ {CLEAR, ASSUMABLE}`  
4. **Clarification** 产物（**仅当**曾 BLOCKED；若从未触发则可空）

缺任一 → **禁止**产出有效 Plan。

### 5.2 输出

| 产物 | 内容 |
|------|------|
| **Design** | 怎么改（模块/接口/配置），仍不是补丁正文亦可，但须可指导补丁 |
| **Task Breakdown** | 有序步骤（对应后续 Execution 切片） |
| **Test Plan** | 如何 Verification（命令/用例/验收 ID） |
| **Risk** | 残留假设与回滚点 |

### 5.3 禁止

- **再回头做 Discovery**（发现不够应 **FAIL Plan 或退回 Gap/Clarify**，不是在 Plan 里偷摸底）。  
- Coding / 写业务源码 / 调用 Execution。  
- 在 `gap_status == BLOCKED` 时产出「正式 Plan」。

### 5.4 PASS / FAIL

| | 条件 |
|--|------|
| **PASS** | 四类输出齐全；每步能映射 Spec.acceptance；Assumptions 显式引用 Gap；未含未批准 Approval 项 |
| **FAIL** | 输入不齐；暗含 Blocking 未决；或夹带已写死的业务补丁冒充「仅计划」却跳过 Human Gate |

---

## 6. Human Gate

### 6.1 AI 永远不能自己决定

- Blocking Gap 的最终答案  
- Approval 类变更是否做  
- Delivery / Requirement Analysis 整段是否 **SUCCESS**（可协助起草，终裁在人）  
- 是否免 Discovery / 免 Clarification 的高风险声明  
- 把 Verification 失败解释成成功  

### 6.2 必须人工批准才能离开本管道

| 闸 | 何时 |
|----|------|
| **Plan Approval** | 进入 Delivery Execution **之前**（默认强制） |
| **Skip Discovery Approval** | 使用跳过时 |
| **Assumption 放行** | `ASSUMABLE` 且假设触及配置默认/兼容边缘时（合同可分级） |
| **Approval 类 Decision** | §4.3 第三行任一 |

未过 Plan Approval：**Delivery Pipeline 不得 Execution**（与产品蓝图 Delivery/Approval 对齐）。

---

## 7. Architecture Boundary

```text
Requirement（自然语言）
      │
      ▼
Requirement Analysis Pipeline     ← 本文（理解 · 摸底 · 缺口 · 条件澄清 · 计划 · 人闸）
      │  产出：Spec · Discovery · Gap · Answers? · Plan · Approvals
      ▼
Delivery Pipeline                 ← Execution · Verification · Review · Delivery
      │  多次 Runtime.submit（今日 Demo/CLI，非 StageRunner）
      ▼
Runtime                           ← 单步 Task/Context/Trace/Artifact/Checkpoint
      │
      ▼
Worker                            ← 副作用
      │
      ▼
Artifact（Runtime 结算）+ Business files
      │
      ▼
Review → Delivery Report
```

| 层 | 允许 | 禁止 |
|----|------|------|
| Analysis Pipeline | 文档/报告/问答；只读摸底 | 改业务代码；扩 Kernel |
| Delivery | 执行补丁与验证 | 在未过 Analysis+人闸时开跑 Execution |
| Runtime | 单步结算 | 拥有 Clarification 状态机（除非未来另开契约且 ADR） |
| Worker | 执行明确动作 | 替人关闭 Blocking Gap |

---

## 8. Non Goal

绝不因本文而实现或宣称：

Scheduler · Workflow · StageRunner · Knowledge · Learning · Memory · AI Worker · Resume · Platform · Plugin · Capability  

本管道今日可由：**人 + Cursor/文档 +（可选）只读命令** 履行；接模型时只许接在 **Worker 只读摸底** 或 **文案起草**，且必须服从 Gap/人闸。

---

## 9. 与现况对齐（诚实）

| 合同要求 | 今日实现 |
|----------|----------|
| Gap / 条件 Clarification | **规范已有；自动化门禁未做** |
| Plan Approval | 合同层有；Runtime 无 Human-Wait |
| 禁 BLOCKED 进 Planning | **靠人遵守**；无强制引擎 |

因此：合同目标成立；**强制力**依赖流程纪律 + 未来可选的 Pipeline 校验器（仍不进 Kernel）。

---

## 文档信息

| 项 | 值 |
|----|-----|
| 文件 | `docs/30-delivery-orchestration/workflow/stages/analysis-contract.md` |
| 上游 | `docs/00-product/capability-map.md` |
| 变更 | 改规则须评审；不授权改 Runtime |
