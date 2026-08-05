# Rule Contract

> Rule = **必须遵守**；违反 ⇒ FAIL / BLOCK。不是可选建议（那是 Skill）。

## 二分宿主

| 类 | 宿主 | 例 |
|----|------|-----|
| **平台 Rule** | Orchestrator 模板包 | 不改 Forbidden 面；Commit 不 Push；不回流本仓 |
| **客户业务 Rule** | **客户仓** | 域不变量、合规、UI/Refund 等硬约束 |

Context Builder 按 **当前角色** 挑选适用 Rule 装入 Package（通常 P1）。

## 装配

- Analysis / Planning / Coding / Testing / Review：**相关 Rule 进 P1**（能识别则装）  
- 体积不够：**不得**丢弃已识别为适用的业务 Rule；应 FAIL 或扩预算（实现：`ApplicableRuleAssembler` + `PackageBudget`）  
- 未知是否适用：可进 P2，不得假装「已遵守」  
- 客户仓路径：`.ai4se/rules/*.md`（header：`id` / `roles` / `applicable`）

## 违反后果

| 场景 | 后果 |
|------|------|
| Coding 改 Forbidden / 无视 Rule | 阶段 FAIL |
| Delivery Push / 回流本仓 | FAIL |
| Verification 无 Acceptance 却报交付成功 | FAIL |

## 与 Learning

Learning Pattern 稳定后可 **升格** 为 Rule；升格后人审，Builder 按 Rule 路径加载。

详见：[skill-contract.md](./skill-contract.md) · [../60-knowledge-lifecycle/learning-contract.md](../60-knowledge-lifecycle/learning-contract.md)
