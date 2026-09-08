---
name: ai4se-customer-delivery
description: 在客户代码仓完成首次摸底、知识建库和受控 Story 交付；仅在用户要接入客户项目、澄清需求或交付需求卡时使用。
metadata:
  short-description: 客户仓受控交付
---

# AI4SE 客户仓交付

AI4SE 是可移植的交付控制面，模型是执行器。用户只说业务意图；不得把内部 Jar、目录、阶段命令、测试框架配置或模型提示词变成用户操作步骤。

## 用户入口

用户只使用下面四种中文入口：

```text
$ai4se-customer-delivery 接入客户项目
$ai4se-customer-delivery 新需求：<需求文字；可附截图、原型链接或本地文件>
$ai4se-customer-delivery 继续：<对编号业务问题的回答>
$ai4se-customer-delivery 验收：通过
```

首次接入只问客户仓绝对路径。若当前宿主无法确定已获批准的 Adapter，才额外问一次 `cursor`、`codex` 或 `claude`；之后保存活动目标，不要反复询问。

## 接入：自动完成，不制造审批负担

后台构建/使用 Host Bundle，并以 `ai4se-flow bootstrap` 完成安装、确定性事实扫描、受限模型 Discovery、evidence-backed `working` 知识提升。不得修改业务源码、不得 push、不得创建业务交付提交。

确定性扫描的产物是 `.ai4se/repository/`：技术栈、模块、源码/测试资产数量和**验证能力地图**。它只能写 `observed` 或 `inventory_only` 事实。模型 Discovery 只能写带 `source_paths`、`Evidence`、`Working Boundary`、`Unknowns` 的 `working` 知识。两者都不是用户需要逐份批准的业务决定。

以下情况自动记录进能力地图，继续摸底；不要问用户、不要跑全仓历史测试、也不要把它当作交付阻塞：

- 没有 `npm test` 或仓库没有统一测试命令；
- 根构建默认跳过测试；
- 不相干的历史集成测试需要对象存储、消息队列或云凭据；
- 发现未知环境变量、外部服务或不属于当前 Story 的模块。

只有客户仓无法读取、当前模型 Adapter 不可运行、或工作树已有业务改动而用户没有明确授权处理时，才停止并报告可执行事实。

完成后展示一页摘要：事实文档、working 知识、显式 Unknowns 和验证能力地图；然后可以直接接收第一张需求卡。

## 新需求：只在真实业务歧义或高风险操作打断

1. 在 `.story/<id>/` 冻结用户原话及附件指纹；针对卡片检索相关 working/verified 知识，并补读最小必要的当前源码。
2. 调用 Specification。若目标、范围、状态变化、计算规则、异常语义或 AC 已由材料和代码充分确定，自动冻结规格；不要为“冻结”本身提问。
3. 仅对**影响实现且不能从材料或源码证明**的业务问题提问。每题必须有编号、为什么影响、代码/附件依据和 2–3 个可选项。用户回答后作为下一轮模型输入，不是手工文件注入。
4. 从冻结规格生成 Plan、Change Map、Constraint Bundle、Test Strategy 和每条 AC 的候选 Probe；推导最小 write scope。然后展示一次**交付就绪摘要**：全部业务回答、影响/风险、Allowed Files、回归场景和 Probe。用户确认“开始无人值守”后，冻结 Probe 并记录这一个启动决定。
5. 只有交付就绪决定后，才无人值守执行 Development → Verification → 有界缺陷修复 → Review → 本地业务 commit。每条 AC 必须由冻结 Probe `PROVEN`；不能以“构建成功”冒充功能验收。没有仓库级测试入口时，Story Probe 可以是交付 oracle。

破坏性数据迁移、公共 API 不兼容、凭据/付费外部服务、支付退款/权限安全、突破冻结 write scope等授权必须在交付就绪摘要中完成，不能在无人值守期再提问。执行期若发现未冻结的新业务事实、越界或 Review/验证非 PASS，只能安全停止并留下证据；不得代答、静默 retry、切换 Adapter 或 push。

交付成功后只展示：本地 commit、AC verdict、Review decision、影响范围和知识失效候选。用户说“验收：通过”才记录最终验收。

## 上下文纪律

不要把全仓或所有历史文档塞给模型。每阶段只给它：冻结规格/AC、匹配的知识命中及其 source paths、当前文件切片、Constraint Bundle、前一阶段结构化产物；修 Bug 再加当前 Defect。知识正文必须有来源、状态和失效标记，不能因为代码修改自动改写成“新事实”。

## 兼容边界

若用户直接在客户仓使用 Cursor、Claude、Codex 或 OMP，需按该工具允许的方式注册本 Skill 或 Bundle 的 Host 指令；不同厂商没有可安全假设的统一安装位置。宿主不支持 Skill 时，模型在后台使用 `ai4se-flow`，仍不得把该命令暴露成用户日常流程。
