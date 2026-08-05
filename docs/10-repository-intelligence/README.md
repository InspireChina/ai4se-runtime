# 01 · Repository Intelligence

> **负责把代码仓数字化，不负责思考。**

## 成功标准

客户仓内有可引用的基线材料 + Build/Test 入口清单；输出中**无**需求理解、改码建议、AI 分析结论。

## 负责

Scan · Facts · Repository Map · Dependency 摘要 · Architecture 要点 · Risk Surface · Build/Test Entry

## 不负责

AI 分析 · 需求理解 · 推荐修改 · 拼 Context Package · 流程跳转

## 文档

| 文档 | 说明 |
|------|------|
| [onboarding-runbook.md](./onboarding-runbook.md) | 冷启动建槽（脚本/命令） |
| [repository-facts-contract.md](./repository-facts-contract.md) | Facts 与可复查基线（历史「Repo Context」中 Facts 侧） |

## 宿主

产出写在**客户仓** `.ai4se/`（见 [asset-hosting](../00-product/asset-hosting.md)）。

## 现网最小

建槽脚本 + 探测测试/构建命令写出清单。不做完整依赖图引擎、自动 DDD。
