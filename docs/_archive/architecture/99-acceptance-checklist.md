# 99 · Unified Acceptance Checklist

> 用于检查本蓝图是否达到「团队不读其他资料即可开始实现 Runtime」。  
> 同时对照 **初版 TASK-001 文档** 与 **本次升级** 的统一性。

## A. 产品定位

| # | 检查项 | 权威文档 | 初版 | 现状 |
|---|--------|----------|------|------|
| A1 | 明确是 AI SE Runtime，不是 Coding Agent | `00`, ADR-0007 | 弱 | ✅ |
| A2 | 无人值守为默认 | `21`, ADR-0007 | 弱 | ✅ |
| A3 | 控制流属 Workflow+Rule，非自由 ReAct | `00`, `09` | 部分 | ✅ |
| A4 | Vue 控制台非 Chat 产品 | `22` | 无 | ✅ |

## B. 一等子系统完整性

| # | 子系统 | 专章 | ADR/RFC | 初版 | 现状 |
|---|--------|------|---------|------|------|
| B1 | 分层架构 | `01`,`11` | 0001 | ✅ | ✅ 升级为八层 |
| B2 | Workflow Engine | `09` | 0006 / 0004 | ✅ | ✅ |
| B3 | Skill Engine | `08` | / 0005 | ✅ | ✅ |
| B4 | Rule Engine | `07` | / 0006 | ✅ | ✅ |
| B5 | Capability + **SDK** | `03`,`20` | 0003,0014 / 0003 | SPI only | ✅ |
| B6 | Plugin System | `04` | 0002 / 0002 | ✅ | ✅ |
| B7 | Adapter System | `05` | 0004 / 0007 | ✅ | ✅ |
| B8 | Repository Graph | `06` | 0005 | ✅ | ✅ |
| B9 | **Task 生命周期** | `15` | 0009 / 0008 | Loop only | ✅ |
| B10 | **Checkpoint** | `16` | 0010 / 0009 | 缺 | ✅ |
| B11 | **Trace** | `17` | 0011 / 0010 | 一句 | ✅ |
| B12 | **Knowledge** | `18` | 0012 / 0011 | 缺 | ✅ |
| B13 | **Project Profile** | `19` | 0013 / 0012 | 缺 | ✅ |
| B14 | Java/Spring/Vue | `22` | 0008 | 缺 | ✅ |

## C. 图与结构

| # | 检查项 | 位置 | 现状 |
|---|--------|------|------|
| C1 | Mermaid 总图/时序/状态机 | `00`,`01`,`15`… | ✅ |
| C2 | C4 | `12`（含 Task/Console/DB） | ✅ |
| C3 | 模块关系图 | `13`（含 sdk/profile/knowledge） | ✅ |
| C4 | 目录结构 | `10` | ✅ |
| C5 | Roadmap | `project-roadmap.md` | ✅ |

## D. 可开工性（实现者问题）

| # | 问题 | 应能回答的文档 |
|---|------|----------------|
| D1 | Runtime 怎么跑完一个无人值守 Task？ | `01`,`15`,`21` |
| D2 | Capability 放哪？怎么用 SDK 开发？ | `03`,`10`,`20`,`14` |
| D3 | 新增 Plugin？ | `04`,`14` |
| D4 | 新增 Workflow / Skill / Rule？ | `09`,`08`,`07`,`14` |
| D5 | 新增 Model Adapter？ | `05`,`14`, RFC-0007 |
| D6 | Checkpoint 何时写、如何恢复？ | `16`, RFC-0009 |
| D7 | Trace 打哪些点、Console 看什么？ | `17`,`22` |
| D8 | 新项目如何接入？ | `19` Profile |
| D9 | 知识如何跨项目复用？ | `18`,`19` |
| D10 | 模块依赖能否用 ArchUnit 表达？ | `10`,`11` |

## E. 术语统一（初版 → 现行）

| 初版术语 | 现行术语 | 处理 |
|----------|----------|------|
| Loop API | Task API | RFC-0001 标注 superseded；RFC-0008 为准 |
| LoopInstance | Task + WorkflowInstance | ADR-0009 |
| Loop phases | TaskIteration phases | `15` |
| LoopContext | TaskContext | `15` |
| NEEDS_HUMAN | NEEDS_POLICY_EXCEPTION / BLOCKED_POLICY | `01`,`21` |
| Observation 仅日志 | Trace-centric | `17`, ADR-0011 |
| AI Loop Runtime 名称 | AI Software Engineering Runtime | README/`00` |

## F. 阶段约束

| # | 约束 | 现状 |
|---|------|------|
| F1 | 无业务实现代码 | 必须保持 |
| F2 | 无 Prompt 正文 | 必须保持 |
| F3 | 文档自洽可验收 | 本清单 |

## 审查签名区（供检查人使用）

| 角色 | 结论 | 签字 | 日期 |
|------|------|------|------|
| 架构师 | Pass / Fail | | |
| 实现负责人 | Pass / Fail | | |
| 平台负责人 | Pass / Fail | | |

**Fail 条件示例**：任一 B 项缺失专章；A1 不成立；D 类问题无法在 docs 内定位答案；存在 Java 业务实现代码。
