# 05 · Verification

> 独立能力域：调用客户已有验证面，证明需求；失败则结构化 Defect。  
> **被 03 Workflow 调度，但不等于「pipeline 里顺手测一下」。**

## 成功标准

对照 Acceptance：Pass/Fail 可复查；Fail 带 Defect Package（非原始日志洪水）。

## 负责（可成长）

Build / Compile / Lint / Unit / Integration / E2E / Visual / Coverage / Performance / Security / …（以**客户已有命令**为准）  
Requirement / Acceptance Validation · Regression Scope · Failure → Defect Package

## 不负责

自建万能测试云替代客户栈 · Development 自评验绿 · 流程状态机（03）

## 文档

| 文档 | 说明 |
|------|------|
| [verification-contract.md](./verification-contract.md) | 验证合同 |
| [defect-package-contract.md](./defect-package-contract.md) | 结构化缺陷回灌 |

## 现网最小

跑 Onboarding 写出的测试入口清单 + Defect 模板。不做 Playwright 平台化。
