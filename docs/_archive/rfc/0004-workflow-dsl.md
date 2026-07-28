# RFC-0004 · Workflow DSL

- Status: **Draft**
- Related: ADR-0006, Architecture 09

## Motivation

声明式描述闭环编排，支持静态校验与插件交付。

## Document Schema（概念）

```
WorkflowDefinition:
  id: string
  version: semver
  entry: nodeId
  maxIterations: int
  maxSteps: int
  nodes: map<nodeId, Node>
  edges: list<Edge>

Node:
  type: skill | capability | model | ruleGate | human | fork | join | subworkflow | reflect | end
  ref?: string          # skill/capability/subworkflow/template id
  params?: object       # 结构化参数，禁止超大自由文本作为唯一契约
  join?: { from: nodeId[] }

Edge:
  from: nodeId
  to: nodeId
  when?: Expression     # 结构化表达式，非脚本
```

## Reflect Decision

Reflect 节点必须产出：

```
{ decision: CONTINUE | DONE | ABORT, reasonCode?: string, facts?: object }
```

## Validation Rules

1. `entry` 存在且可达至少一个 `end`
2. 无悬空边
3. 所有 `ref` 在注册表可解析（或标记 external deferred）
4. `fork` 必须有对应 `join` 或显式取消语义
5. `when` 只能使用允许的事实路径

## Non-Goals

BPMN、定时器事件、跨进程补偿事务（v1）。
