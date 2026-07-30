# Gap Report

gap_status: BLOCKED
blocking_gap_count: 7
assumable_gap_count: 0

## Known

- confidence=medium
- candidate-files=1
- candidate:README.md

## Unknown

- No Promotion / discount implementation surface found in repository Facts.
- No Order calculation / OrderService surface found in repository Facts.
- Existing database schema for promotions is unknown from Facts.

## Assumption

- Q: 是否已有 Promotion 表（或等价持久化）？表结构是否已知？ → A: UNKNOWN — Facts 未证明 Promotion 表；待确认
- Q: 是否已有折扣/优惠领域模型（固定金额、百分比）？ → A: UNKNOWN — Facts 未证明折扣模型；待确认
- Q: 现有 Promotion 是否已支持多个活动并存？ → A: UNKNOWN — Facts 未证明多活动并存行为；待确认
- Q: Order 是否已经存在金额计算接口（或 OrderService 计价入口）？路径是什么？ → A: UNKNOWN — Facts 未证明 Order 计价接口；待确认
- Q: 新满减/满折是否必须兼容并不得破坏已有优惠类型？兼容约束是什么？ → A: UNKNOWN — 兼容约束待确认；Spec 要求不得影响已有优惠
- Q: 「每个订单只能命中一个最高优惠」的比较规则（按优惠金额？按优先级？）是否已有定义？ → A: UNKNOWN — 「最高优惠」比较规则待确认
- Q: 百分比折扣的「最高优惠金额」配置存放在哪里（活动级/全局）？ → A: UNKNOWN — 百分比封顶配置位置待确认

## Risk

- Human confirmed UNKNOWN — must not invent: 是否已有 Promotion 表（或等价持久化）？表结构是否已知？
- Honest mode: stop at Clarification until concrete answer for: 是否已有 Promotion 表（或等价持久化）？表结构是否已知？
- Human confirmed UNKNOWN — must not invent: 是否已有折扣/优惠领域模型（固定金额、百分比）？
- Honest mode: stop at Clarification until concrete answer for: 是否已有折扣/优惠领域模型（固定金额、百分比）？
- Human confirmed UNKNOWN — must not invent: 现有 Promotion 是否已支持多个活动并存？
- Honest mode: stop at Clarification until concrete answer for: 现有 Promotion 是否已支持多个活动并存？
- Human confirmed UNKNOWN — must not invent: Order 是否已经存在金额计算接口（或 OrderService 计价入口）？路径是什么？
- Honest mode: stop at Clarification until concrete answer for: Order 是否已经存在金额计算接口（或 OrderService 计价入口）？路径是什么？
- Human confirmed UNKNOWN — must not invent: 新满减/满折是否必须兼容并不得破坏已有优惠类型？兼容约束是什么？
- Honest mode: stop at Clarification until concrete answer for: 新满减/满折是否必须兼容并不得破坏已有优惠类型？兼容约束是什么？
- Human confirmed UNKNOWN — must not invent: 「每个订单只能命中一个最高优惠」的比较规则（按优惠金额？按优先级？）是否已有定义？
- Honest mode: stop at Clarification until concrete answer for: 「每个订单只能命中一个最高优惠」的比较规则（按优惠金额？按优先级？）是否已有定义？
- Human confirmed UNKNOWN — must not invent: 百分比折扣的「最高优惠金额」配置存放在哪里（活动级/全局）？
- Honest mode: stop at Clarification until concrete answer for: 百分比折扣的「最高优惠金额」配置存放在哪里（活动级/全局）？

## Decision Needed

- 是否已有 Promotion 表（或等价持久化）？表结构是否已知？ [ANSWER=UNKNOWN — still blocking for Planning]
- 是否已有折扣/优惠领域模型（固定金额、百分比）？ [ANSWER=UNKNOWN — still blocking for Planning]
- 现有 Promotion 是否已支持多个活动并存？ [ANSWER=UNKNOWN — still blocking for Planning]
- Order 是否已经存在金额计算接口（或 OrderService 计价入口）？路径是什么？ [ANSWER=UNKNOWN — still blocking for Planning]
- 新满减/满折是否必须兼容并不得破坏已有优惠类型？兼容约束是什么？ [ANSWER=UNKNOWN — still blocking for Planning]
- 「每个订单只能命中一个最高优惠」的比较规则（按优惠金额？按优先级？）是否已有定义？ [ANSWER=UNKNOWN — still blocking for Planning]
- 百分比折扣的「最高优惠金额」配置存放在哪里（活动级/全局）？ [ANSWER=UNKNOWN — still blocking for Planning]

