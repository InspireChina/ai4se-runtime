# RFC-0002 · Plugin SPI

- Status: **Draft**
- Related: ADR-0002, Architecture 04

## Motivation

统一 Plugin 发现、生命周期与贡献注册。

## SPI 形状（概念）

```
PluginDescriptor { id, version, runtimeApiVersion, dependencies[], contributions }

Plugin {
  id(): PluginId
  start(PluginContext): void
  stop(): void
}

PluginContext {
  register(Contribution)
  getService(Class<T>)   // 仅暴露白名单服务，不含 Kernel 内部
}

Contribution =
  | WorkflowContribution
  | SkillContribution
  | RuleContribution
  | CapabilityContribution
  | AdapterContribution
  | ModelProviderContribution
  | GraphEnricherContribution
```

## Discovery

1. Host 配置路径
2. `plugins/` 目录扫描 descriptor
3. ServiceLoader / 清单文件

## Conflict Policy

同 `contributionType + id`：

1. 显式 `priority` 高者胜
2. 版本更新者胜（同 priority）
3. 否则启动失败

## Compatibility

`runtimeApiVersion` 主版本必须匹配；次版本向后兼容。
