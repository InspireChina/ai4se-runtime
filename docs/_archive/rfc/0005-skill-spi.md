# RFC-0005 · Skill SPI / Program

- Status: **Draft**
- Related: Architecture 08

## Motivation

提供介于 Workflow 与 Capability 之间的可复用程序单元。

## Descriptor

```
SkillDescriptor {
  id, version, inputSchema, outputSchema,
  requiredCapabilities[], requiredPermissions[],
  programKind: DECLARATIVE | COMPILED,
  tags[]
}
```

## Declarative Program Ops（v1）

| Op | 说明 |
|----|------|
| `sequence` | 顺序 |
| `conditional` | 基于绑定/事实 |
| `forEach` | 有限集合，必须 `limit` |
| `invokeCapability` | 调 Capability |
| `invokeSkill` | 调子 Skill（禁止递归环，启动期检测） |
| `setContext` | 写受限 memory 键 |
| `fail` / `succeed` | 终止 |

## Compiled Skill

实现 `SkillProgram` 接口；仍须声明静态 `requiredCapabilities`，Engine 可做启动期闭合检查。

## Execution Guarantees

- 输入输出 schema 校验
- 逐步审计
- 取消信号可在 op 边界生效
