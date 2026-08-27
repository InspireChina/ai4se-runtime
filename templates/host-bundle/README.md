# AI4SE 客户仓 Host Bundle

本 Bundle 是本地交付控制面，不是模型服务商，也不是 IDE 插件。
它不会自行安装模型 CLI，也不会自行修改客户业务源码。

先阅读 `docs/customer-host-bridge-day-one-guide.md`；完整命令契约与停止规则见
`docs/customer-host-bridge-runbook-v1.md`。

正常情况下，在支持项目 Skill 的模型工具中调用 `skills/ai4se-customer-delivery/`，直接用中文表达“摸底”或
“交付需求”。模型会在后台调用受控命令，并只在真实的人类决策点询问。`bin/ai4se-flow full ...` 是不支持 Skill 时的
终端回退；两者遵循同一契约。正常模式只自动处理证据化知识、清晰规格和低风险 Plan；业务歧义、高风险操作、验证/Review 失败及最终验收始终保留人为决定。
