# Learning Contract

> 能力域 **06 · Knowledge Lifecycle**。宿主：**客户仓**。Learning = **交付经验**，不是 AI Memory，不是学习客户代码。

## 学什么

| 学什么 | 例 | 结果 |
|--------|-----|------|
| Delivery Pattern | 某客户 Promotion 常漏 Refund | 升格/更新客户 Rule 或 Development P1 |
| 重复 UI 失误 | margin 次次错 | 生成/加强 UI Rule，Development 自动加载 |
| 验红热点 | 某命令常红 | 写入 Learning + Test Strategy 提示 |

## 不是什么

- Runtime 越来越聪明的隐式记忆  
- 把客户代码背进模型  
- 本仓中枢数据库

## 是什么

客户仓 **可审文件** → 经 Context Builder **显式**装入 Package（通常 P2，热点可升 P1/Rule）。

## Lifecycle

| 事件 | 动作 |
|------|------|
| Story 验红/验收后 | 人可写 Pattern；或工具建议草稿等人审 |
| Pattern 稳定复现 | **升格 Rule**（硬门禁）或写入 Development/Verification P1 |
| 失效 | 标 deprecated；Builder 不再装入 |

## 与 Rule / Skill

| | Learning | Rule | Skill |
|--|----------|------|-------|
| 强度 | 经验提示 | 必须遵守 | 推荐做法 |
| 升格 | → Rule | — | — |

详见：[../20-context-engineering/materials/rule-contract.md](../20-context-engineering/materials/rule-contract.md) · [../20-context-engineering/materials/skill-contract.md](../20-context-engineering/materials/skill-contract.md) · 能力域 README：[README.md](./README.md)
