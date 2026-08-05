# Defect Loop Contract

> 能力域 **05 · Verification**。Verification FAIL 之后的结构化回灌。

## 目标

把失败变成 **Defect Package**，使下一轮 Development 的 Context Package 能精准修复——不是甩原始日志。

## Input（P1）

- Verification Report（失败项）  
- Requirement / Acceptance（受影响项）  
- Approved Plan / Allowed Files  
- 当前 Diff 摘要  
- 适用 Rule  

## Output 必须（Defect Package）

| 字段 | 含义 |
|------|------|
| 为什么失败 | 相对 Acceptance / 命令结果的结构化原因 |
| 影响 AC | 哪些验收项未满足 |
| 影响模块 | 相关模块 / 文件指针 |
| 建议修改范围 | 建议 Allowed 扩展或保持 |
| 禁止修改范围 | Forbidden；不得借修缺陷扩大面 |
| 复现指针 | 命令 / 产物路径（指针，非洪水日志） |

## Output 禁止

- 只贴 Console 全文当缺陷  
- 在 Defect 阶段直接改业务代码  
- 无 Acceptance 对照的「感觉坏了」  

## Stop / Resume / FAIL

- **Stop：** Defect Package 写入 `.story/`，Orchestration 调度 Development  
- **Resume：** 同缺陷轮次工具闪断  
- **FAIL：** 无法映射到任何 AC；连续同缺陷超预算（熔断上交 Orchestration）  

装配：Defect Package 进入下一轮 Development 的 Context **P1**（见 [development-contract](../30-delivery-orchestration/workflow/stages/development-contract.md)）。
