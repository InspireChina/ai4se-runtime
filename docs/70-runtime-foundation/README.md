# 07 · Runtime Foundation

> **以后越少越好。Frozen。**  
> 不是产品增长面。

## 成功标准

能调度 Task、落 Artifact、Checkpoint 恢复、Trace 可审；**无新交付业务语义**。

## 负责

Task · Artifact · Checkpoint · Trace · **Worker** · Decision（门闸骨头）· State

## 不负责

Context 方法论 · Workflow/Control 产品策略 · Adapter 协议细节（04）· 客户知识

## 文档

| 文档 | 说明 |
|------|------|
| [frozen-boundary.md](./frozen-boundary.md) | 冻结边界 |
| [adr/](adr/README.md) | 架构决策 |
| [architecture/](architecture/README.md) | 对象模型与不变量 |

变更须 ADR。禁止因「新交付能力」解冻加业务。
