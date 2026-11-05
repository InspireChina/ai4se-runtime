# Delivery Contract

> 能力域 **03 · Workflow · Delivery**。本地交付就绪（Commit 不 Push）+ 报告；等人验收。  
> 通例见 [context-engineering-spec.md](../../../20-context-engineering/context-engineering-spec.md)。  
> 输出：**Delivery Package**

## Input

| 优先级 | 内容 |
|--------|------|
| **P1** | Requirement / Acceptance 满足证明（Verification 产出）；Code Diff 摘要；Review 草稿要素；客户 Git 规范（Commit 不 Push） |
| **P2** | Learning（交付 checklist） |
| **禁止** | 推远程；把客户源码打进本仓 |

## Output 必须

- 本地 Commit（若政策允许）或明确「仅工作区就绪待人 commit」  
- Delivery Report；等人验收状态  

## Output 禁止

- Push  
- 提交进 ai4se-runtime  
- 无 Verification 放行却交付  

## Stop / Resume / FAIL

- **Stop：** 等人验收；验收后 Orchestration 触发 Learning → Knowledge Management  
- **FAIL：** Push；回流本仓；无 Verification 放行  
