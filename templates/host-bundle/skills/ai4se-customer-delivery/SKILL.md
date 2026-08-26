---
name: ai4se-customer-delivery
description: 在 AI4SE 受控流程中完成客户仓摸底与 Story 交付。用户要求建立项目知识库、分析需求或交付需求卡时使用；普通代码编辑不使用。
metadata:
  short-description: 客户仓受控交付
---

# AI4SE 客户仓接入与交付入口

AI4SE 是交付控制面；你是被选择的模型执行器，不是流程所有者。用户使用业务语言即可，不能要求他们记住
CLI 命令、Package 路径、Story 目录名或提示词模板。

## 唯一用户入口

用户在 **AI4SE 项目根目录** 调用本 Skill：

```text
$ai4se-customer-delivery 接入客户项目
$ai4se-customer-delivery 新需求：<需求文字，可附本地原型/文档>
$ai4se-customer-delivery 继续：<业务回答>
$ai4se-customer-delivery 验收：通过
```

不得要求用户执行 `install`、`onboard`、`intake`、`run` 或 `resume` 等内部命令；这些命令只由你在后台执行。

首次“接入客户项目”只询问：客户仓绝对路径，以及客户批准的 Adapter（`cursor`、`codex` 或 `claude`）。在
AI4SE 项目的未跟踪目录 `.ai4se/customer-targets/` 记录路径、Bundle、Adapter、当前 Story 与状态；绝不提交客户
路径、凭据或客户内容。以后“新需求 / 继续 / 验收”读取活动目标，只有没有目标或有多个目标时才询问用户选择。

## 接入客户项目：摸底与知识建库

拿到客户路径后，验证 Git 工作区与工作树，构建/安装 Bundle，执行确定性 `onboard`，并核对
`.ai4se/repository/entries.yaml` 是否真的执行构建/测试。测试被跳过、环境缺失或入口不诚实时，展示事实并只询问
必要决定；不得伪称基线已验证。

随后准备 Bridge Discovery Package，只读允许材料，生成带 `Evidence → Working Boundary → Unknowns` 的知识候选。
展示候选摘要与路径后，只问：**“是否批准初始知识库？”**。只有明确同意才执行 `approve-knowledge` 与 checkpoint，
且只创建客户仓本地提交、不 push；之后目标状态为 `KNOWLEDGE_READY`。

## 新需求：需求到交付

收到“新需求”后，从活动目标读取客户仓和 Adapter。没有 `KNOWLEDGE_READY` 时先完成接入，不能跳过。
创建稳定 Story id，用 `intake` 冻结用户文字与已附本地附件，再准备并提交 Specification。只有 Specification
发现带源码依据的具体歧义时才提问。候选规格就绪后，展示一次紧凑审批卡：

```text
目标 / 范围外 / 业务决策 / AC / 建议的最大 write scope / 附件使用情况
```

询问 **“是否冻结规格及该最大 write scope？”**。用户批准业务边界后，再冻结规格并调用 Production Runtime；
write scope 必须是代码证据支持的最小上限，不能传仓库根目录或模糊通配范围。

Analysis 出现具体问题时，展示原始编号问题与选项，记录用户答案并 `resume`。Plan 就绪后，将 Plan、Change Map、
Constraint、Test Strategy 和冻结 Probe 作为一次紧凑审阅展示，询问 **“是否批准 Plan 并开始无人值守交付？”**。
只有此时才允许 `Development → Verification → 有界缺陷修复 → Review → local commit` 无人值守运行。

终态成功后，展示本地 commit、每条 AC Probe verdict 与 Review decision，再询问最终验收。用户说“验收：通过”
才执行 `accept`。不得替用户回答授权问题、不得 push、不得修改状态文件、不得在 policy/verification/Adapter 停止后
静默 retry，也不得未经用户选择切换 Adapter。

## 终端回退方式

若用户直接在客户仓打开模型，需要客户工具先注册 Bundle 内同名 Skill；不同厂商项目 Skill 位置不同，不能假装有
通用自动安装。若宿主工具不能可靠执行单个 AI4SE 命令，使用 `ai4se-flow full` 作为终端回退；后续 Story 添加
`--existing-knowledge`。

不得利用本 Skill 绕过客户对 Browser 材料、附件、凭据或部署的访问政策。
