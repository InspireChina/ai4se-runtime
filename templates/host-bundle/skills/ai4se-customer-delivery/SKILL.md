---
name: ai4se-customer-delivery
description: 在 AI4SE 受控流程中完成客户仓摸底与 Story 交付。用户要求建立项目知识库、分析需求或交付需求卡时使用；普通代码编辑不使用。
metadata:
  short-description: 客户仓受控交付
---

# AI4SE 客户仓受控交付

AI4SE 是交付控制面；你是被选择的模型执行器，不是流程所有者。用户使用业务语言即可，不能要求他们记住
CLI 命令、Package 路径、Story 目录名或提示词模板。

## 用户如何调用

在已接入 AI4SE 的客户仓中，以下任一表达都是对此 Skill 的调用：`$ai4se-customer-delivery`、`/ai4se`，
或普通自然语言。

- `摸底这个项目` / `建立项目知识库`
- `交付这个需求：<text or attachment>`
- `继续 <answer>`
- `验收 <story>：通过` / `拒绝 <story>：<reason>`

不要询问用户要执行哪个 Runtime 命令。应从当前工作目录确定 workspace、从
`.ai4se/host/installation.properties` 确定 Runtime Jar，并在后台使用已安装的 `ai4se` 命令。
如果没有 Host Profile，只询问一次经批准的 AI4SE Bundle 位置；不得猜测路径或自行安装软件。

## 首次项目：摸底与知识建库

收到“摸底这个项目”后，执行确定性 `onboard` 与 Bridge Discovery。只阅读准备好的 Package，生成带源码依据的
知识候选。用紧凑审阅形式展示 `Evidence`、`Working Boundary`、`Unknowns`，然后只问一个问题：
**“是否批准初始知识库？”**。只有得到明确同意，才能执行知识批准和 checkpoint；绝不修改业务源码。

## Story：需求到交付

收到“交付这个需求”后，创建稳定的 Story id，用 `intake` 冻结用户文字与本地附件，再准备并提交
Specification。只有 Specification 发现带源码依据的具体歧义时才提问。候选规格就绪后，展示：

```text
目标 / 范围外 / 业务决策 / AC / 建议的最大写入范围
```

询问 **“是否冻结规格及该最大写入范围？”**。用户批准业务边界后，再冻结规格并调用 Production Runtime；
不得选择比展示结果更宽的 write scope。

Analysis 出现具体问题时，展示原始编号问题与选项，记录用户答案并 `resume`。Plan 就绪后，将 Plan、Change Map、
Constraint、Test Strategy 和冻结 Probe 作为一次紧凑审阅展示，询问 **“是否批准 Plan 并开始无人值守交付？”**。
只有此时才允许 `Development → Verification → 有界缺陷修复 → Review → local commit` 无人值守运行。

终态成功后，展示本地 commit、每条 AC Probe verdict 与 Review decision，再询问最终 `accept/reject`。不得替用户
回答授权问题、不得 push、不得修改状态文件、不得在 policy/verification/Adapter 停止后静默 retry，也不得未经用户选择
切换 Adapter。

## 终端回退方式

如果宿主模型工具无法可靠执行单个 AI4SE 命令，使用已安装的 `ai4se-flow full`。它在终端暴露同样的人类授权节点。
已有批准知识库的后续 Story 添加 `--existing-knowledge`。

不得利用本 Skill 绕过客户对 Browser 材料、附件、凭据或部署的访问政策。
