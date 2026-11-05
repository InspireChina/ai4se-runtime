# Skill Contract

> Skill = **推荐做法**（非硬门禁）。三层 **禁止混装**。

## 三层

| 层 | 例子 | 宿主 |
|----|------|------|
| **Platform Skill** | 怎么写 Plan / Clarification / Review / Defect 模板 | Orchestrator 模板包 |
| **Tech Skill**（如 Spring） | Controller / Service / MyBatis / Transaction | Orchestrator 技术包 → 落到客户仓或挂载 |
| **Customer Skill** | Promotion / Refund / Coupon 怎么改 | **客户仓**（随 Context 成熟） |

## 装配指引

| 角色 | 典型 |
|------|------|
| Planning | Platform Skill（写 Plan）P1；Tech/Customer P2 |
| Coding | 适用 Platform/Tech/Customer **按需** P1；无关层禁止塞满 |
| Testing | Tech Skill（测试写法）常 P2 |
| Review | Platform Skill（Review 模板） |

## 禁止

- 把 Customer Skill 写进本仓当「平台包」  
- 用 Skill 冒充 Rule（违反 Skill ≠ 自动 FAIL；违反 Rule = FAIL）  
- 一次装入全部三层全集

详见：[rule-contract.md](./rule-contract.md) · [../20-context-engineering/context-builder-contract.md](../20-context-engineering/context-builder-contract.md)
