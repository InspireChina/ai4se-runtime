# AI4SE 客户仓 Host Bundle

本 Bundle 是本地交付控制面，不是模型服务商，也不是 IDE 插件。
模型通过已批准的 Adapter 在冻结范围内修改客户业务源码；控制器执行验证，生成本地交付和验收报告。

**先读根目录 [QUICKSTART.md](./QUICKSTART.md)。** 更细说明见 `docs/customer-host-bridge-day-one-guide.md`；命令契约见
`docs/customer-host-bridge-runbook-v1.md`。

## 终端三步

```bash
./scripts/bootstrap-customer.sh /绝对路径/客户仓 cursor
./scripts/new-story.sh /绝对路径/客户仓 story-001 /tmp/req.md cursor
./scripts/story-ctl.sh start /绝对路径/客户仓 story-001 --actor 你的名字
./scripts/story-ctl.sh report /绝对路径/客户仓 story-001
```

## Skill（可选）

在支持项目 Skill 的模型工具中注册 `skills/ai4se-customer-delivery/`，用中文说“接入客户项目”或
“新需求：…”。模型在后台调用同一控制器，只在真实人类决策点询问。

`bin/ai4se deliver …` 与 `bin/ai4se-flow …` 是低层接口；Skill 与 `scripts/*` 走同一契约。
prepare/start 提交独立后台任务，不要求聊天持续在线。次日查看 `.story/<id>/controller/acceptance-report.md`。
