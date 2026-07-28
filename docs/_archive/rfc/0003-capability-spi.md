# RFC-0003 · Capability SPI

- Status: **Draft**
- Related: ADR-0003, Architecture 03

## Motivation

定义原子能力的描述、调用与错误契约。

## SPI 形状（概念）

```
CapabilityDescriptor {
  id, version, inputSchema, outputSchema,
  sideEffects, idempotency, requiredPermissions,
  requiredAdapters, timeoutHint, tags
}

Capability {
  descriptor(): CapabilityDescriptor
  execute(CapabilityRequest, CapabilityEnv): CapabilityResult
}

CapabilityRequest { loopId, invocationId, input, idempotencyKey? }
CapabilityEnv { ports, contextReadView, clock, ids }  // 无任意写权限
CapabilityResult { output | error }
```

## Engine Pipeline

Permission → Schema → Idempotency → Execute → Normalize → Audit

## Error Taxonomy

| Code Class | Retry |
|------------|-------|
| VALIDATION | No |
| RETRYABLE | Yes（有上限） |
| FATAL | No |
| NEEDS_HUMAN | No（暂停） |

## Builtin 最小集（规划）

`context.get` `context.patch` `artifact.put` `artifact.get` `repo.readFile` `repo.graph.query`

其余由 Plugin 提供。
