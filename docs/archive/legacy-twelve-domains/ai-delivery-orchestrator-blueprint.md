# AI Delivery Orchestrator · 产品蓝图

> **设计原点。** 产品 = **十二个一级能力域**；以后不能随便加第十三件。  
> **不是**开发工具 · **不是** Runtime · **不是** Claude Wrapper。  
> **是：** 定义 AI 如何交付软件，并把最佳实践固化成可无人值守执行的交付系统。  
> 北极星：[vision.md](./vision.md) · 哲学：[philosophy.md](./philosophy.md) · 名词：[glossary.md](./glossary.md)

---

## 要解决什么（一句话）

> **定义 AI 软件交付的每一个环节应该怎么做，并把这些最佳实践固化成可无人值守执行的交付系统。**

---

## 整个系统只有十二件事情

这十二件事情，就是整个产品。  
后面所有 Contract / Context / Knowledge / Rule / Skill，都挂在这十二个一级能力下面，**不围绕 Runtime 组织**。


| #   | 能力域                       | 一句话                                               | 目录                                                                  |
| --- | ------------------------- | ------------------------------------------------- | ------------------------------------------------------------------- |
| 1   | **Repository Onboarding** | 第一次接触客户项目，建好 AI 可理解的仓库                            | `[01-repository-onboarding/](./01-repository-onboarding/README.md)` |
| 2   | **Knowledge Management**  | 维护 AI 理解项目所需的全部知识（建/更/汰/检）                        | `[02-knowledge-management/](./02-knowledge-management/README.md)`   |
| 3   | **Context Engineering**   | AI 每一步该看到什么 → Context Package                     | `[03-context-engineering/](./03-context-engineering/README.md)`     |
| 4   | **Requirement Analysis**  | 按 Contract 理解需求、范围、风险、未知、BLOCK                    | `[04-requirement-analysis/](./04-requirement-analysis/README.md)`   |
| 5   | **Planning**              | 需求 → 开发/测试/风险/迁移/回滚等方案                            | `[05-planning/](./05-planning/README.md)`                           |
| 6   | **Development**           | 按 Plan 改代码；不越权；守 Rule/Skill                       | `[06-development/](./06-development/README.md)`                     |
| 7   | **Verification**          | 调用客户已有测试能力证明需求；不自评                                | `[07-verification/](./07-verification/README.md)`                   |
| 8   | **Defect Loop**           | 失败 → 结构化 Defect Package → 再 Development           | `[08-defect-loop/](./08-defect-loop/README.md)`                     |
| 9   | **Review**                | 最终检查需求/Rule/Skill/架构/质量                           | `[09-review/](./09-review/README.md)`                               |
| 10  | **Delivery**              | Commit / Report / Artifact / Story 状态；等人验收；不 Push | `[10-delivery/](./10-delivery/README.md)`                           |
| 11  | **Learning**              | 沉淀交付经验，回写 Knowledge/Rule/Skill                    | `[11-learning/](./11-learning/README.md)`                           |
| 12  | **Orchestration**         | 调度：启停、Resume、压缩、并行、熔断、预算、审批、回环                    | `[12-orchestration/](./12-orchestration/README.md)`                 |


**支撑（非第十三能力）：** 薄执行底座 `[90-engine/](./90-engine/README.md)` · 水位/状态 `[99-status/](./99-status/README.md)`

---

## 十二件事情之间的关系

```text
Repository Onboarding
            │
            ▼
Knowledge Management
            │
            ▼
Context Engineering
            │
            ▼
Requirement Analysis
            │
            ▼
Planning
            │
            ▼
Development
            │
            ▼
Verification
      │           │
      │ PASS      │ FAIL
      ▼           │
   Review         │
      │           │
      ▼           │
   Delivery ◄─────┘   （FAIL 经 Defect Loop → Development → Verification）
      │
      ▼
Learning
      │
      ▼
Knowledge Management
```

更精确的 FAIL 路径：

```text
Verification FAIL → Defect Loop → Development → Verification …
Verification PASS → Review → Delivery → Learning → Knowledge Management
```

**Orchestration** 横贯全程：决定何时启动/停止/Resume/重开 Session/压缩/并行/串行/熔断/预算/审批/回环，直到 Story 完成。

---

## 1. Repository Onboarding

系统第一次接触客户项目。

负责：建立 Repository Context · Knowledge · Learning · Rules · Skills · Story 工作区 · 测试入口 · 索引。  
输出：**一个 AI 可以理解的客户项目。**

→ `[../01-repository-onboarding/](./01-repository-onboarding/README.md)`

---

## 2. Knowledge Management

不是存 Markdown，而是维护 AI 理解项目所需要的全部知识：

Repository Context · Business Knowledge · Architecture · Module · Risk · Dependency · Learning（资产面）

负责：建立 · 更新 · 淘汰 · 检索。  
输出：Context Builder 可以快速找到需要的信息。

→ `[../02-knowledge-management/](./02-knowledge-management/README.md)`

---

## 3. Context Engineering（最重要）

负责回答：AI 每一步到底应该看到什么。

输入哪些知识 · 多少 · 权重 · Token 预算 · 压缩 · Resume · 切 Session。  
输出：**Context Package**

→ `[../03-context-engineering/](./03-context-engineering/README.md)`

---

## 4. Requirement Analysis

不是让模型自由想，而是按 Context Contract 完成：理解需求 · 影响范围 · 风险 · 未知 · 提问 · 判断 BLOCK。  
输出：**Analysis Bundle**

→ `[../04-requirement-analysis/](./04-requirement-analysis/README.md)`

---

## 5. Planning

把需求变成：开发方案 · 测试方案 · 风险方案 · 迁移方案 · 回滚方案等。允许多个 Agent。  
输出：**Plan Bundle**

→ `[../05-planning/](./05-planning/README.md)`

---

## 6. Development

按 Plan 修改代码。必须：完成需求 · 不越权 · 不改无关模块 · 遵守 Rule/Skill。  
输出：**Workspace Diff**

→ `[../06-development/](./06-development/README.md)`

---

## 7. Verification

调用客户所有测试能力（Unit / Integration / E2E / UI / Regression / Coverage / Build / Lint / Performance …）。  
不是自己判断，而是调用客户已有能力。  
输出：**Verification Report**

→ `[../07-verification/](./07-verification/README.md)`

---

## 8. Defect Loop

失败时把失败转换成开发最容易理解的上下文——不是日志/Console，而是结构化 Defect：

为什么失败 · 影响 AC · 影响模块 · 建议修改范围 · 禁止修改范围。  
输出：**Defect Package** → 再进入 Development。

→ `[../08-defect-loop/](./08-defect-loop/README.md)`

---

## 9. Review

最终检查是否满足：需求 · Rule · Skill · 架构 · 质量 · Review 标准。  
输出：**Review Result**

→ `[../09-review/](./09-review/README.md)`

---

## 10. Delivery

Commit · Report · Artifact · Story 状态 · 等待人工验收。**不是 Push。**  
输出：**Delivery Package**

→ `[../10-delivery/](./10-delivery/README.md)`

---

## 11. Learning

不是学习客户代码，而是学习交付经验：易改坏点 · Rule 加强 · Skill 新增 · Pattern 沉淀。  
最后更新：Knowledge · Rule · Skill · Learning。

→ `[../11-learning/](./11-learning/README.md)`

---

## 12. Orchestration

整个系统的大脑。负责何时：启动/停止 Agent · Resume · 重开 Session · 压缩 · 并行/串行 · 熔断 · 预算 · 审批 · 回环 · 恢复 —— 直到 Story 完成。

→ `[../12-orchestration/](./12-orchestration/README.md)`

---

## 资产宿主（不变）

```text
客户仓：Context · Knowledge · Learning · 客户 Rule/Skill · .story/ · 源码 · Tests · Git
本仓：  十二能力域的 Contract / SOP / 平台模板 · Tool Adapter · 薄 Engine
```

---

## 要做 / 不做

### 要做

按十二能力域逐项定义 Contract + 最佳实践，并验证可无人值守执行。优先 Context Engineering + Orchestration + Knowledge Retrieval/Lifecycle。

### 不做

- 第十三件「随缘模块」  
- 本仓业务 Knowledge/Graph 中枢  
- 模型自助全库检索当主路径  
- 自由 Agent 主循环替代 Orchestration  
- Development/Verification 混会话自拍通过  
- 客户代码回流本仓  
- 把 Runtime 当产品中心

---

## 验收：读完能否回答

1. 产品是什么？ → **十二个能力域**，不是模块清单
2. 最重要的一件？ → **Context Engineering**
3. 谁调度全程？ → **Orchestration**
4. Verification 失败走哪？ → **Defect Loop → Development**
5. 成功长什么样？ → 新客户 + 新 Story → … → Commit → 人验收；稳定、可验证、与模型无关
6. Engine 是第几件？ → **不是**；只是支撑（`90-engine`）

