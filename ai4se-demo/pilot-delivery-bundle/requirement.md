# Requirement

电商后台 Promotion Service 新增促销类型：满减 + 满折。
支持多个活动；每活动含名称、生效/结束时间、是否启用；
支持多优惠阶梯（如满100减20、满300减80、满500打9折）；
每订单只命中一个最高优惠；优惠类型含固定金额与百分比折扣；
百分比折扣最高优惠金额可配置；
后台接口：Create/Update/Query Promotion、Order Calculate Promotion；
不能影响已有优惠；无命中返回 NoPromotion。
已有数据库/OrderService/Promotion：未知。
