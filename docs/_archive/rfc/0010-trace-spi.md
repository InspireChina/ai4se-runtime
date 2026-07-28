# RFC-0010 · Trace Store SPI

- Status: **Accepted-Draft**
- Related: ADR-0011, Architecture 17

## SPI

```
TraceSpan {
  traceId, spanId, parentSpanId?, taskId,
  name, kind, startTime, endTime?, status,
  attributes, events, resourceUsage, links, error?
}

TraceStore {
  createRoot(taskId): traceId
  startSpan(span): void
  endSpan(spanId, status, error?): void
  appendEvent(spanId, event): void
  getTree(traceId): SpanTree
  queryByTask(taskId): SpanTree
}
```

## Required attribute keys（稳定）

`project.id` `profile.id` `workflow.id` `workflow.node.id` `iteration.index`  
`plugin.id` `capability.id` `skill.id` `rule.id` `model.provider`  

## Redaction

Attribute 名称匹配 `*.secret` `*.token` `*.password` 必须拒绝或红处理。
