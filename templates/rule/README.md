# Rule templates

平台 Rule 模板（占位）。合同见 [`docs/20-context-engineering/materials/rule-contract.md`](../../docs/20-context-engineering/materials/rule-contract.md)。

客户业务 Rule 放在客户仓 `.ai4se/rules/*.md`：

```text
# id: refund-invariant
# roles: Analysis,Development
# applicable: true

Must not invent refund tables without Clarification.
```

附录 A：适用 Rule 进 P1；预算不够 → Builder FAIL（扩预算），**不得**静默丢弃。
