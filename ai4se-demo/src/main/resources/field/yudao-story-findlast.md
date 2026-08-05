# Story Seed · yudao-common CollectionUtils.findLast

## raw

在 yudao-framework/yudao-common 的 CollectionUtils 中，已有 findFirst(Collection, Predicate)。
需要对称增加 findLast：从集合末尾向前找第一个满足谓词的元素；空集合或无匹配时返回 null（与 findFirst 语义一致）。
同步补充单元测试；不得改动现有 diffList / findFirst 行为。

## goal

为 CollectionUtils 增加 findLast，并用单测证明空/有匹配/无匹配三种行为。

## in_scope

- `CollectionUtils.java` 新增 `findLast`（及如需与 findFirst 对称的 Function 重载）
- `CollectionUtilsTest.java` 新增对应断言
- 仅限 yudao-common 模块内上述文件

## out_of_scope

- 启动 yudao-server / 连接 MySQL·Redis
- 改其他模块、改 pom 依赖
- 全局仓库重构

## acceptance

- 命令 `mvn -pl yudao-framework/yudao-common -am -Dtest=CollectionUtilsTest -DfailIfNoTests=false test` 退出码 0
- 存在可调用的 `CollectionUtils.findLast(Collection, Predicate)`（或文档约定的等价签名）
- 单测覆盖：空集合 → null；有多个匹配 → 返回最后一个；无匹配 → null
- 原有 `testDiffList` 仍通过
