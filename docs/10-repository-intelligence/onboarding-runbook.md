# Onboarding Runbook · Repository Intelligence

> 能力域 **01 · Repository Intelligence**。  
> **把代码仓数字化，不负责思考。** 冷启动 / 首次接入客户仓。

## 目标

在客户仓建立最小完备**槽位与入口清单**——不是堆 AI 分析、不是需求理解、不是改码建议。

## 怎么执行

```bash
./scripts/onboard-repo.sh /path/to/customer-repo
```

脚本创建 `.ai4se/` + `.story/`，并尽量探测 build/test 入口写入 `entries.yaml`。

## Input（P1）

- 客户仓根路径  
- 平台 Rule/Skill 模板包（可选落地）  
- 构建/测试发现策略  

## Output 必须

| 产物 | 说明 |
|------|------|
| Repository baseline / Facts 骨架 | 模块地图、构建/测试入口、风险面骨架（事实，非建议） |
| Knowledge 槽位 + index | 可为空正文，结构必须存在 |
| Learning / Rules / Skills 槽位 | 可空 + 客户扩展位 |
| `.story/` 约定 | Story 过程文档根 |
| 测试/构建入口清单 | 供 05 Verification 调用 |

## Output 禁止

- 把客户业务全文拷进本仓  
- 编造业务 Knowledge 当 Facts  
- AI 分析结论、需求理解、推荐修改  
- 跳过测试入口发现却宣称 Onboarding 完成  

## Stop / FAIL

- **Stop：** 槽位就绪 → 可开 Story；Lifecycle（06）后续维护知识  
- **FAIL：** 无法访问；无法建立最小基线；违反零回流  

下游：[repository-facts-contract](./repository-facts-contract.md) · [Context Engineering](../20-context-engineering/README.md)
