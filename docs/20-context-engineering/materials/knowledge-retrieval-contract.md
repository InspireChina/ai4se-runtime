# Knowledge Retrieval Contract

> 02 材料合同：检索与装入规则。正文宿主：**客户仓**。生命周期见 06。  
> Knowledge = **Storage + Retrieval + Lifecycle**（缺一不可）。

## Storage

| 层 | 形态 | 说明 |
|----|------|------|
| 正文 | Markdown 树（客户仓） | 域知识、架构说明 |
| 清单 | YAML 索引 `id → path → kind → tags → refs` | 供检索，不是业务全文 |
| 逻辑图 | 索引 `refs` = 边 | 借鉴 Graph 思想；**默认无**本仓 Graph Engine |

槽位示例：`.ai4se/knowledge/` + index（细节可演进，契约优先）。

## Retrieval（Claude 不找）

```text
Repository Context（相关模块 / 地图）
  → 解析 Knowledge IDs（index + refs / 标签 / Story 关键词）
  → Knowledge Loader（只加载 ID 列表对应正文切片）
  → Context Builder → Context Package → CLI
```

**禁止：** 把「自行检索全库」写进 Prompt 当主路径。  
**允许：** 执行器在 Allowed Files 内读文件（Coding）；那是改码所需，不是 Knowledge 检索器。

## Lifecycle

| 事件 | 动作 |
|------|------|
| 冷启动摸底 | 填充初版 Knowledge 槽位 + index |
| Story 进行中 | Knowledge 默认只读；过程进 `.story/` |
| Story 验收后 | 增量更新 Knowledge / refs；人可审 |
| 失效 | index 标 deprecated；Builder 不再装入 Priority 1 |

## Builder 装配规则

- 只装入 **已解析 ID 列表** 对应切片  
- 体积预算不够：先裁 P2，再缩短切片，**不得**静默丢弃未声明却必需的 P1 ID  
- deprecated / 无关域：**禁止**进 P1

产品总览：[blueprint](../00-product/capability-map.md) · Builder：[../20-context-engineering/context-builder-contract.md](../20-context-engineering/context-builder-contract.md)
