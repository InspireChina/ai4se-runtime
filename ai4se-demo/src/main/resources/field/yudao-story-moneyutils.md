# Story 种子 · MoneyUtils 单测基线

## raw

芋道 yudao-common 的 MoneyUtils 被商城/支付相关逻辑使用（分转元、折扣、百分比金额），
但缺少单元测试。需要补齐核心用例；若测红暴露边界错误则仅在 Allowed 文件内修正，
不得借机重构无关工具类或引入新依赖。

## goal

为 MoneyUtils 建立可回归的单测基线，并保证分/百分比相关语义在测例中成立。

## in_scope

- 新增 `MoneyUtilsTest`（包路径与 MoneyUtils 对应）
- 必要时小改 `MoneyUtils.java` 边界（仅当现有行为与 javadoc 明显矛盾且测例证明）
- 仅限 yudao-common 上述文件

## out_of_scope

- 启动 yudao-server / 连接 MySQL·Redis
- 修改 pay/mall/system 等业务模块
- 更换金额库、大范围重构 CollectionUtils/HttpUtils 等

## acceptance

- 命令 `mvn -pl yudao-framework/yudao-common -am -Dtest=MoneyUtilsTest -DfailIfNoTests=false test` 退出码 0
- 测例至少覆盖：`fenToYuan` / `fenToYuanStr` 一组已知分值；`calculateRatePrice`（HALF_UP）一组已知值；`calculateRatePriceFloor` 与 HALF_UP 在同一组输入上可区分；`calculator` 在 `percent == null` 时返回 `price * count`；`priceMultiply` / `priceMultiplyPercent` 在任一参数为 null 时返回 null
- Diff 不超出 Allowed Files
