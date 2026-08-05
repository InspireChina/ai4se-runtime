# Context Engineering Specification

> **能力域 02 · 产品最值钱的规范。**  
> 中心句：**让 AI 在有限 Token 下获得最大有效信息。**  
> 换任意模型：Contract 不变；变的只是 Tool Adapter。  
> 产品原点：[capability-map](../00-product/capability-map.md)

## 通例

每个阶段必须定义：

| 块 | 含义 |
|----|------|
| **Input · Priority 1（必须）** | 缺失 ⇒ Builder FAIL，不得开 CLI |
| **Input · Priority 2（可选）** | 可裁剪；预算不够先丢 P2 |
| **Input · 禁止** | 出现则剔除或整包 FAIL |
| **Output · 必须** | 缺一不可；校验失败 ⇒ 阶段 FAIL |
| **Output · 禁止** | 如 Development 输出「测试已通过」自评 |
| **Stop** | 正常结束条件 |
| **Resume** | 同角色同阶段可续（**是否** Resume 由 03 Control 决定；**如何**重建包在此） |
| **FAIL** | 硬失败 / 上交 03 Control |

**权重：** P1 = 一定精准、一定不能缺失；P2 = 可降质；禁止 = 一定不能进包。

## 阶段合同索引（挂在 03 Workflow）

| 阶段 | 合同 |
|------|------|
| Analysis | [../30-delivery-orchestration/workflow/stages/analysis-contract.md](../30-delivery-orchestration/workflow/stages/analysis-contract.md) |
| Planning | [../30-delivery-orchestration/workflow/stages/planning-contract.md](../30-delivery-orchestration/workflow/stages/planning-contract.md) |
| Development | [../30-delivery-orchestration/workflow/stages/development-contract.md](../30-delivery-orchestration/workflow/stages/development-contract.md) |
| Verification | [../50-verification/verification-contract.md](../50-verification/verification-contract.md) |
| Defect | [../50-verification/defect-package-contract.md](../50-verification/defect-package-contract.md) |
| Review | [../30-delivery-orchestration/workflow/stages/review-contract.md](../30-delivery-orchestration/workflow/stages/review-contract.md) |
| Delivery | [../30-delivery-orchestration/workflow/stages/delivery-contract.md](../30-delivery-orchestration/workflow/stages/delivery-contract.md) |

装配器：[context-builder-contract.md](./context-builder-contract.md) · 调度：[../30-delivery-orchestration/](../30-delivery-orchestration/README.md)

## 跨模型原则

同一 Contract → 不同 Tool Adapter。  
**禁止**为某个模型写一套「私藏超大 Prompt」绕过 Builder。

## 现网不做

Knowledge Ranking · 向量 Recall · Graph Engine —— 主链未逼出前不做。
