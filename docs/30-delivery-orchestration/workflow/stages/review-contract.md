# Review Contract

> 能力域 **03 · Workflow · Review**。结构化评审结论与残留风险；**不**替代 Verification。  
> 通例见 [context-engineering-spec.md](../../../20-context-engineering/context-engineering-spec.md)。  
> 输出：**Review Result**

## Input

| 优先级 | 内容 |
|--------|------|
| **P1** | Requirement；Acceptance；Plan；Verification 结论；Diff 摘要；适用 Rule |
| **P2** | Learning；Platform Skill（Review 模板） |
| **禁止** | 用 Review 会话重跑全量测试冒充 Testing；无关 Story 全文 |

## Output 必须

- Review 结论（通过 / 附条件 / 驳回）  
- 残留风险（结构化）  

## Output 禁止

- 替代 Verification 做验绿  
- 无 Verification 结论却宣称可交付  

## Stop / Resume / FAIL

- **Stop：** 结论写入 `.story`，交 Delivery 或驳回回环  
- **FAIL：** P1 缺失；绕过 Verification  
