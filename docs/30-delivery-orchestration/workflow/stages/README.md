# Workflow · Stages

挂在 **03 Delivery Orchestration · Workflow**。不是一级能力域。

| 阶段 | 合同 |
|------|------|
| Analysis | [analysis-contract.md](./analysis-contract.md) |
| Planning | [planning-contract.md](./planning-contract.md) |
| Development | [development-contract.md](./development-contract.md) |
| Verification | 调用 [50-verification](../../../50-verification/README.md)（本目录不重复造验证产品） |
| Review | [review-contract.md](./review-contract.md) |
| Delivery | [delivery-contract.md](./delivery-contract.md) |

Verify FAIL → [defect-package](../../../50-verification/defect-package-contract.md) → Development（由 Control 调度）。

Analysis 段含 Discovery|skip（见 [analysis-contract](./analysis-contract.md)）。Delivery 之后是人验收，再触发 06（见 [通路验证手册](../../../../PATHWAY-VERIFICATION-HANDBOOK.md)）。
