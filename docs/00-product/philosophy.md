# Philosophy · 产品哲学

> **长期不能偏的原则。**  
> 宪法：[capability-map.md](./capability-map.md)

## 1. 产品 = 八个能力域

| 是 | 不是 |
|----|------|
| Repository Intelligence · Context · Orchestration · Execution · Verification · Lifecycle · Runtime · Infrastructure | 按 Maven 模块讲的产品 |
| Contract · Context Package · 交付标准 | 以 Worker / Prompt / 某家模型为中心 |
| 定义 AI 如何交付软件 | 研究「模型本身怎样更聪明」 |

**一年内不新增第九个一级域。** Runtime / Infrastructure 是底座，不是销售话术中心。

## 2. 知识在客户仓

客户业务知识、交付经验、业务 Rule/Skill **只住客户仓**。  
本仓提供：八域 Contract / SOP / 平台模板 / Adapter / 薄 Runtime。

## 3. 执行器只吃 Package

执行器只看见 Context Builder 给出的 Context Package。  
约束靠 **Rule + Contract 校验**，不靠 Prompt 念经。  
**AI Execution 不拥有业务知识，不拥有流程控制。**

## 4. Orchestration = Workflow + Control

| | 负责 |
|--|------|
| **Workflow** | 阶段顺序（Analysis→…→Deliver） |
| **Control** | 何时继续/停/批/回环/Resume |
| **Context Engineering** | 做的时候看什么、吐什么 |

## 5. Runtime 越 Frozen 越小

Task / Artifact / Worker / Trace / Checkpoint / Decision —— 骨头要稳。  
见 [frozen-boundary.md](../70-runtime-foundation/frozen-boundary.md)。  
**Worker 属于 Runtime，不属于 AI Execution。**

## 6. Verification 独立；Defect 必须结构化

调用**客户已有**验证能力；无 Acceptance 对照的「编译过」不算通过。  
FAIL → Defect Package → Development。Development **禁止**自评验绿。

## 7. Commit 不 Push；零回流

本地 Commit → 人验收；**不 Push**。禁止客户源码回流本仓。

## 8. 合同先于实现；域先于功能

换模型只换 Adapter。加功能先问落在哪个域、是否提高该域成功标准。  
**禁止为架构而架构**（Graph / Ranking / Policy Engine 等，主链未逼出前不做）。

## 9. Repository Intelligence 不思考

数字化表示（Facts / Map / 入口），不做 AI 分析、需求理解、改码建议。

## 10. Knowledge Lifecycle 不生产知识

只管理增删改汰 / 索引 / 晋升；不做 Repo Scan，不替代 Context 拼包。
