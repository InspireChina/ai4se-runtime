# 03 · Delivery Orchestration

> 决定 **什么时候做、谁做、做到哪里、失败怎么办**。  
> 不拼 Package，不验绿，不持业务知识。

## 成功标准

Story 能从需求走到本地 Commit，或明确 Stop；Verification FAIL 有 Defect→Dev 去向。

## 概念拆分（不拆模块）

```text
Delivery Orchestration
├── Workflow   阶段顺序
└── Control    启停 / 审批 / 回环 / Resume 决定 / Retry
```

### Workflow

Analysis → Discovery|skip → Clarification? → Planning → Approval? → Development → **Verification(05)** → Review → Delivery → **人验收** → **Lifecycle 触发(06)**

阶段合同：[workflow/stages/](workflow/stages/README.md)

### Control

Approval · Loop · Stop · Resume 决定 · Retry  
（Budget / Parallel / Rollback：**后置**）

合同：[control/control-contract.md](./control/control-contract.md)

## 不负责

Context 裁剪细节（02）· 调用哪家模型的协议细节（04）· 测试命令语义（05）· Worker 语义扩展（07）

## Decision

**不独立成域。** 全部写在 Control 规则里。等真复杂到难维护再抽。
