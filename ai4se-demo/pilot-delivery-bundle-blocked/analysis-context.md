# Repository Context

> 由 Analysis Pipeline 基于 Facts + Requirement 构建。  
> **禁止** Patch / Implementation / Plan / Task / Review。

## Refs

- requirementRef: 满300减50（本 Bundle requirement.md）
- factsRef: facts.md（本 Bundle）

## Candidate Modules

- (none — Facts 未给出可编码模块)

## Relevant Files / Candidate Files

- `README.md`（仅说明未知，非实现面）

## TopK

1. `README.md` | legacy surface unknown | 与「Promotion」字样弱相关

## Confidence

**low**

## Unknown

1. Promotion 持久化/表是否存在及结构
2. 是否已有满减/优惠领域模型
3. 订单计价入口（OrderService 或等价）是否存在及路径
4. 店铺券模型与叠加引擎是否存在
5. 秒杀活动模型与互斥引擎是否存在
6. 「满300」计数字段（原价/实付/优惠后）定义
7. 退款时优惠回滚/分摊规则与现有退款流入口
8. 多商品订单优惠分摊规则（按行/按整单）
9. 本工作区是否即真实 Promotion Service 仓（或仅占位）

## Need Clarification

1. 真实代码仓库路径是什么？当前根是否仅为占位？
2. 是否已有 Promotion 表/服务？路径与负责人？
3. 是否已有订单金额计算接口？如何接入优惠？
4. 店铺券与秒杀在系统中的对象名/服务名是什么？叠加/互斥如何判定？
5. 「满300」按哪种金额口径？
6. 退款是否需冲销本满减？部分退款如何处理？
7. 多商品订单优惠落在整单还是行项目？

## Assumptions

| 假设 | 来源 | 是否允许推进业务 |
|------|------|------------------|
| 当前工作区 Facts 不能代表已实现 Promotion 系统 | Facts（仅 README） | **否** — 仅用于证明未知 |
| Spec 字面要求满减 300/50、叠店铺券、斥秒杀、退款、多商品 | Requirement 原文 | **否** — 不能替代仓库事实 |

**无业务默认假设**（不假设表已存在、不假设可直接 Coding）。
