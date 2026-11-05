# Context Builder Contract

> 挂在能力域 **02 · Context Engineering**。Context Builder = **非 AI** 的 Context Assembler。  
> 执行器 **永远不知道** Knowledge/Rule/Learning 物理路径。

## 职责

1. 读当前阶段的 **Context Contract**（见 [context-engineering-spec.md](./context-engineering-spec.md) 与 04–10 能力域）  
2. 从客户仓 / `.story` / 平台模板 **检索并裁剪**  
3. 按 Priority 装入；执行禁止项过滤与体积预算  
4. 产出 **Context Package**（结构化：清单 + 正文切片 + 元数据）  
5. 阶段结束按压缩策略写入 `.story` Artifact，供下一阶段  

## Context Package（最小语义）

| 块 | 说明 |
|----|------|
| `role` | Analysis / Planning / Development / Verification / … |
| `priority1` / `priority2` | 已装内容清单 + 切片引用 |
| `forbidden_filtered` | 被剔除项（审计） |
| `ids` | Knowledge / Rule / Skill / Learning ID 列表 |
| `budget` | token/字节预算与裁剪记录 |
| `story_refs` | 关联 `.story/<id>/` 产物 |

字段名可演进；**语义不可用 Prompt 替代**。

## 预算与失败

| 条件 | 行为 |
|------|------|
| P1 缺失 | **FAIL**，不得开 CLI |
| 预算不够 | 先丢 P2，再缩短切片；仍缺 P1 → FAIL |
| 出现禁止项 | 剔除；若无法剔除且污染包 → FAIL |
| 禁止项被执行器绕过 | 属 Tool Adapter / 门禁 FAIL |

## 压缩

触发：阶段边界、token 预算、角色切换前（由 Orchestration 决定时机）。  
保留：结论、Unknown、Acceptance、Allowed Files、Defect Package、关键 ID 列表。  
**不保留**聊天流水账。

## 禁止

- 让 CLI 自行全库检索当主路径  
- 为某模型写私藏超大 Prompt 绕过 Builder  
- 把客户全文打进本仓日志  

产品原点：[blueprint](../00-product/capability-map.md) · 知识检索：[../20-context-engineering/materials/](materials/README.md)
