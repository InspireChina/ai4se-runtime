# Clarification

required: true

## Questions

1. 是否已有 Promotion 表（或等价持久化）？表结构是否已知？
2. 是否已有折扣/优惠领域模型（固定金额、百分比）？
3. 现有 Promotion 是否已支持多个活动并存？
4. Order 是否已经存在金额计算接口（或 OrderService 计价入口）？路径是什么？
5. 新满减/满折是否必须兼容并不得破坏已有优惠类型？兼容约束是什么？
6. 「每个订单只能命中一个最高优惠」的比较规则（按优惠金额？按优先级？）是否已有定义？
7. 百分比折扣的「最高优惠金额」配置存放在哪里（活动级/全局）？

## Answers

- **promotion_table**: UNKNOWN — Facts 未证明 Promotion 表；待确认
- **discount_model**: UNKNOWN — Facts 未证明折扣模型；待确认
- **multi_promotion**: UNKNOWN — Facts 未证明多活动并存行为；待确认
- **order_calc**: UNKNOWN — Facts 未证明 Order 计价接口；待确认
- **compat**: UNKNOWN — 兼容约束待确认；Spec 要求不得影响已有优惠
- **best_offer_rule**: UNKNOWN — 「最高优惠」比较规则待确认
- **percent_cap**: UNKNOWN — 百分比封顶配置位置待确认
