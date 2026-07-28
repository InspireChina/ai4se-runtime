# RFC-0006 · Rule DSL

- Status: **Draft**
- Related: Architecture 07, ADR-0003

## Motivation

以声明式策略治理 Step / Capability，避免在 Kernel 写死 if-else。

## Document Schema（概念）

```
RuleDefinition:
  id: string
  version: semver
  scope: GLOBAL | WORKFLOW | STEP | CAPABILITY
  phase: PRE | POST | BOTH
  priority: int
  enabled: bool
  when: FactMatcher
  then: Action[]
```

## FactMatcher

支持：相等、存在、集合包含、数值比较、与/或组合。  
事实路径白名单由 Engine 文档维护（如 `capabilityId`、`args.*`、`budget.remainingSteps`、`graph.impact.*`）。

## Actions

`ALLOW` | `DENY{reasonCode}` | `ADD_CONSTRAINT{...}` | `REQUIRE_HUMAN{reasonCode}` | `EMIT_EVENT` | `SET_FLAG{key,value}`

## Evaluation

适用规则按 priority 升序；`DENY` 短路为拒绝；约束合并取更严。

## Non-Goals

Rule 内调用 Capability、任意脚本、完整 RETE。
