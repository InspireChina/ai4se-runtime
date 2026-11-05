---
name: ai4se-customer-delivery
description: 在客户代码仓完成首次摸底、知识建库和受控 Story 交付；仅在用户要接入客户项目、澄清需求或交付需求卡时使用。
metadata:
  short-description: 客户仓受控交付
---

# AI4SE 客户仓交付

AI4SE 是可移植的交付控制面，模型是执行器。用户只说业务意图；不得把内部 Jar、目录、阶段命令、测试框架配置或模型提示词变成用户操作步骤。

## 用户入口

用户只需记住两个入口；其余是正常对话回答：

```text
$ai4se-customer-delivery 接入客户项目
$ai4se-customer-delivery 新需求：<需求文字；可附截图、原型链接或本地文件>
```

首次接入只问客户仓绝对路径。若当前宿主无法确定已获批准的 Adapter，才额外问一次 `cursor`、`codex` 或 `claude`；之后保存活动目标，不要反复询问。

执行前读取 [控制器操作](references/controller.md)。日常新需求使用 `deliver prepare/start/status/report/answer`，不要在聊天里逐步重建主链。prepare/start 提交独立后台 JVM，关闭聊天后继续运行，日志和状态保存在客户仓。用户回答“Q1 选 B”“开始无人值守”“查看进度”“验收通过”即可。

任务返回 LAUNCHING/RUNNING 时仍在执行，不能因命令返回或暂时无新文件而重复调用模型。READY 时展示 controller/readiness.md 并等待一次启动确认；将页面指纹和真实批准人交给 start。代码、约束或探针变更会使旧批准失效。

失败与成功均查看 controller/acceptance-report.md。只有 DELIVERED_AWAITING_ACCEPTANCE 表示本地交付完成；用户次日验收前不能自动 accept。验证模拟仅在用户明确授权代答时进行，actor 必须标注 simulated。

## 接入：自动完成，不制造审批负担

后台构建/使用 Host Bundle，并以 `ai4se-flow bootstrap` 完成安装、确定性事实扫描（含轻量 Code Graph）、受限模型 Discovery、evidence-backed `working` 知识提升。不得修改业务源码、不得 push、不得创建业务交付提交。

确定性扫描的产物是 `.ai4se/repository/`（技术栈、模块、`source-inventory`、验证能力地图）和 `.ai4se/index/code-graph/`（nodes/edges/manifest）。图只能写扫描证据，不能冒充业务真理；模型 Discovery 只能写带 `source_paths`、`Evidence`、`Working Boundary`、`Unknowns` 的 `working` 知识。两者都不是用户需要逐份批准的业务决定。后续 Story 由控制器按需增量刷新图，工程师不要日常手工跑内部 `code-graph` 命令。

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

破坏性数据迁移、公共 API 不兼容、凭据/付费外部服务、支付退款/权限安全、突破冻结 write scope等授权必须在交付就绪摘要中完成，不能在无人值守期再提问。验证 FAIL 在冻结预算内由控制面生成 Defect 并修复；预算耗尽、环境阻塞、未冻结的新业务事实、越界或 Review 非 PASS 才结算停止。不得代答、改探针凑绿、重置预算、切换 Adapter 或 push。

交付成功后只展示：本地 commit、AC verdict、Review decision、影响范围和知识失效候选。用户说“验收：通过”才记录最终验收。

## 上下文纪律

不要把全仓或所有历史文档塞给模型。每阶段只给它：冻结规格/AC、匹配的知识命中及其 source paths、当前文件切片、Constraint Bundle、前一阶段结构化产物；修 Bug 再加当前 Defect。知识正文必须有来源、状态和失效标记，不能因为代码修改自动改写成“新事实”。

## 兼容边界

同一个客户 checkout 默认串行；本版本不自动并行或自动换供应商。Cursor/Claude/Codex 需要现场已登录且可自动调用的 CLI。OMP 可以作为宿主使用 Skill；其模型后端并不自动等于已注册 Adapter。只有聊天界面、没有可调用执行器时，不能承诺关窗无人值守。

不同厂商没有统一 Skill 安装位置，按客户工具规则注册本 Skill 或 Host 指令。宿主不支持 Skill 时，可读取本文件在后台调用控制器，仍不要把内部参数变成用户日常操作。
