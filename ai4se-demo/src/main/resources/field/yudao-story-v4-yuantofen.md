# Story 种子 · MoneyUtils.yuanToFen（V4 缺陷回环）

## raw

需要为 MoneyUtils 增加元转分 API，并补单测。实现必须与分转元互逆（1 元 = 100 分）。

## goal

提供 `MoneyUtils.yuanToFen(BigDecimal)`，null 返回 null；已知金额换算正确。

## in_scope

- `MoneyUtils.java` 增加 `yuanToFen`
- `MoneyUtilsTest` 增加对应用例
- 仅限上述 Allowed 文件

## out_of_scope

- 启动 server / DB
- 改动其它工具类

## acceptance

- `mvn -pl yudao-framework/yudao-common -am -Dtest=MoneyUtilsTest -DfailIfNoTests=false test` 退出码 0
- `yuanToFen(1.00) == 100`；`yuanToFen(0.01) == 1`；`yuanToFen(null) == null`
- Diff 不超出 Allowed Files
