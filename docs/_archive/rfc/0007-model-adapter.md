# RFC-0007 · Model Provider Adapter

- Status: **Draft**
- Related: ADR-0004, Architecture 05, Extension Guide E

## Motivation

将模型供应方变为可替换 Adapter，Kernel 无厂商锁定。

## SPI 形状（概念）

```
ModelProviderDescriptor {
  id, version, labels[],
  supportsStructuredOutput: bool,
  supportsTools: bool,
  maxContextTokens: int,
  costClass: LOW | MID | HIGH
}

ModelPort {
  invoke(ModelRequest): ModelResponse
}

ModelRequest {
  modelAlias?,
  messagesRef,          # 引用 Plugin 模板渲染结果的句柄/结构化消息
  responseSchema?,      # 结构化输出 schema
  tools?,
  timeout,
  metadata
}

ModelResponse {
  structured? | text?,
  usage: { inputTokens, outputTokens },
  finishReason,
  providerRawRef?       # 可选，审计外置存储指针
}
```

## Routing

Model Engine 根据：任务标签、required labels、成本、健康状态选择 Provider。  
可用 Rule / Host config 固定某 Loop 的 provider。

## Obligations

- 错误映射到标准 taxonomy
- 不在日志打印密钥与完整敏感载荷
- Structured schema 校验失败 → `VALIDATION`（或可配置重试一次，Policy 决定）

## Adding a New Model

见 `docs/architecture/14-extension-guides.md` §E。本 RFC **不包含任何 Prompt 文本**。
