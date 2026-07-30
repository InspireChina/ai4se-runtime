# Repository Context

confidence: medium

## Candidate Files

- `README.md`

## Relevant Modules

- (none)

## TopK

- README.md | score=6 | # Promotion Service (legacy surface unknown)

## Unknown

- No Promotion / discount implementation surface found in repository Facts.
- No Order calculation / OrderService surface found in repository Facts.
- Existing database schema for promotions is unknown from Facts.

## Need Clarification

- 是否已有 Promotion 表（或等价持久化）？表结构是否已知？
- 是否已有折扣/优惠领域模型（固定金额、百分比）？
- 现有 Promotion 是否已支持多个活动并存？
- Order 是否已经存在金额计算接口（或 OrderService 计价入口）？路径是什么？
- 新满减/满折是否必须兼容并不得破坏已有优惠类型？兼容约束是什么？
- 「每个订单只能命中一个最高优惠」的比较规则（按优惠金额？按优先级？）是否已有定义？
- 百分比折扣的「最高优惠金额」配置存放在哪里（活动级/全局）？

## Facts Ref (summary)

- strategy: map+search
- truncated: false
