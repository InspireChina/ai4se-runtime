# Verification Contract

> 能力域 **05**。调用客户已有测试能力，判断 **需求/验收是否实现**——不是只看「编译过没」，不是自己判断。  
> 通例见 [context-engineering-spec.md](../20-context-engineering/context-engineering-spec.md)。

## Input

| 优先级 | 内容 |
|--------|------|
| **P1** | Requirement；Acceptance；Business Rule（相关）；Approved Plan；**Workspace Diff / 变更摘要**；Test Strategy；Repository Context（**测试相关**：如何拉起客户工具）；已有 Defect（历史本 Story） |
| **P2** | Learning（验红热点）；Tech Skill（测试写法） |
| **禁止** | 整个代码树当唯一输入；无 Requirement/Acceptance 的「裸测」；无关 Story |

## 可调用能力（客户已有）

Unit · Integration · E2E · UI · Regression · Coverage · Build · Lint · Performance · …（以客户仓测试入口为准）

## Output 必须

- **Verification Report**  
  - PASS：覆盖哪些 Acceptance；命令与结果指针  
  - FAIL：触发 [Defect Package](./defect-package-contract.md)（结构化 Defect，非日志洪水）  
  - **诚实字段：** `verdict_basis: customer_entry_exit_code`；`acceptance_item_scoring: not_performed`（客户测试入口是 AC 神谕，禁止假装逐条 LLM 评分）  
  - **先装包再跑：** Verify Package P1 必须**嵌入** Acceptance + Diff + entry（禁止 `See .story/...` 空指针包）  
- 回归范围建议  

## Output 禁止

- 直接改业务代码冒充修复（修复走 Development）  
- 无 Acceptance 对照的「我觉得可以」  
- 只报编译成功当交付成功  
- 指针-only / 空包裸测  
- 把 `acceptance_met` 写成「已逐条人工/模型核对」却只看了 exit code 
## Stop / Resume / FAIL

- **Stop PASS：** 交 Review  
- **Stop FAIL：** Defect Package → Orchestration 调度 Development  
- **FAIL：** P1 无 Requirement/Acceptance；无法调用任何客户测试入口却宣称通过  
