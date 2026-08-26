# Litemall 两张复杂需求卡串行验证复盘

> 验证日期：2026-08-26。客户仓：`/Users/peng.lv/IdeaProjects/litemall`。Runtime 分支：
> `codex/customer-production-runtime`。本报告记录真实结果、失败路径和边界，不把流程实验夸大为生产上线。

## 结论

两张相互串行的 Java 8 领域策略卡均完成 **Specification → Analysis → Planning → 冻结 Probe →
Development → Verification → Review → 本地 Commit → 实验性验收**，且每条冻结 AC Probe 为 `PROVEN`。

| 卡 | 交付提交 | 结果 | 无人值守阶段 |
|---|---|---|---|
| `promotion-tiered-pricing-r001-r3` | `c212e0ec68190ad064a5b64d05b588aae172866c` | 6/6 AC PROVEN，Review PASS | Development 第 1 轮即通过、local commit |
| `goods-variant-publication-policy-r002` | `6e3eaa7f36f208631529115343901efb5ba05b92` | 5/5 AC PROVEN，Review PASS | Development 第 1 轮即通过、local commit |

两张卡的最终 Runtime 状态均为 `AWAITING_HUMAN_ACCEPTANCE`，workflow 为 `DELIVERY / COMPLETED`；
实验使用 `ai4se-experiment-simulated-customer-owner` 完成验收记录，**不能视为真实产品、Plan 或验收授权**。

## 1. 起点与首次摸底

业务仓最初重置到 Litemall 上游 `a1ef964a718b7277925b19ea26afe78ea3a1d325`；随后建立受控 Host
基线 `4ec1056b`，批准初始知识的本地 checkpoint 为 `9a4250c4`。基线构建与真实测试入口已执行；
其中测试入口明确避免了根 POM 的 `maven.test.skip=true` 造成“命令成功但没有测试”的假象。

### 1.1 确定性产物（不调用模型推断）

| 位置 | 内容 | 用途 |
|---|---|---|
| `.ai4se/repository/entries.yaml` | 已验证 build/test 命令 | Verification 的正式入口 |
| `.ai4se/repository/baseline.md` | 基线提交、环境、限制 | 复现和变更基准 |
| `.ai4se/repository/module-map.md` | 模块与边界地图 | 选择目标模块 |
| `.ai4se/index/knowledge.yaml` | 知识 ID、标签、来源路径、SHA、状态 | 可检索的知识索引 |

### 1.2 模型化知识候选及批准结果

当前模型在 Discovery Package 限定的源码范围内生成候选；人审阅来源后才批准。已验证知识正文为：

| 文件 | 记录的事实 / 边界 | 未确认项 |
|---|---|---|
| `.ai4se/knowledge/wx-checkout-promotion-boundary.md` | `WxCartController` 的团购/优惠券/运费金额顺序，`CouponVerifyService` 是资格校验权威 | 新促销的配置来源、持久化、生产支付/库存链路 |
| `.ai4se/knowledge/admin-promotion-configuration-boundary.md` | `AdminCouponController`、`AdminGrouponController` 是不同配置路径，不存在可复用统一策略 DTO | 活动范围、原型、可复用表 |
| `.ai4se/knowledge/admin-goods-write-boundary.md` | `AdminGoodsService` 的多表事务、最低价重算、购物车缓存同步、订单历史不回写 | 批量审计、幂等、最大批量、并发、后台 UI |

每篇均采用 **Evidence → Working Boundary → Unknowns** 格式，并由 `knowledge.yaml` 保存 source commit、
source path 和 SHA。它们是可审阅的仓内记忆，不是模型聊天摘要。

## 2. 卡 1：促销报价策略

### 2.1 需求与澄清

目标是实现纯 Java 8 的报价策略：满折和满减互斥、取优惠更大者、相同优惠优先满减；“10 元任选 10 件”
可叠加并优先结算；不合格商品和余数不参与该固定价组。

原始表述中“固定十元”与“固定组价非正值要拒绝”的验收描述矛盾。Analysis 没有猜测，而是停止并提出
澄清；本次实验替用户给出决定：**10 元是不可配置的常量，因此不输入组价；仍校验数量/价格、折扣和满减阈值。**
答案进入 `analysis/clarification.resolved.md` 后，Analysis 重评为 CLEAR。

### 2.2 模型实际读取的上下文

成功 r3 的 Analysis P1 包含：冻结 `requirement.md`、6 条 AC、`entries.yaml`、repository facts、
`module-map.md`、`baseline.md`、operator write-scope 和上述澄清结论。

**重要观察：此卡没有命中知识检索条目。** Analysis package 中没有 `knowledge-hits.md`。因此它没有自动读取
促销相关的三篇知识；这不是“知识已被完整利用”，而是当前 tag/检索与纯 `litemall-core/promotion` 目标
不匹配的缺口。后续应为报价策略增加 `core,promotion,pricing` 标签或由 Planning 依据影响面显式补充知识。

Planning 形成并冻结：`plan.md`、`change-map.md`、`effective-constraints.md`、`test-strategy.md`，以及 6
个每条 AC 对应的 shell Probe。Development P1 包继承冻结规格、计划、允许范围、约束和 Probe 内容；模型不能
用“自行改测试”替代已有探针。

### 2.3 代码与验证

Commit `c212e0ec` 新增 5 个核心实现类和 `PromotionQuoteServiceTest`，共 6 个文件、349 行新增；改动限定在：

```text
litemall-core/src/main/java/org/linlinjava/litemall/core/promotion/
litemall-core/src/test/java/org/linlinjava/litemall/core/promotion/
```

`verification/report-round-1.md` 记录正式 Maven 入口成功，且 AC1–AC6 的冻结脚本逐条 exit 0 / `PROVEN`。
Review 侧文件 `review/review-result.properties` 为 `decision=PASS`、`review_source=adapter`。

## 3. 卡 2：商品 SKU 发布策略

卡 2 在卡 1 的实验性 `accept` 后才进入串行队列。它实现纯 Java 8 商品 SKU 发布前策略：SKU 非空且唯一、
库存与价格合法、已启用 SKU 参与最低价、只有启用且有库存的 SKU 计入可售库存、发布必须有可售 SKU、草稿可
没有可售 SKU、金额按两位半向上舍入。

### 3.1 分析包读取情况

该卡的 Analysis P1 明确有 `slices/knowledge-hits.md`，命中了并注入
`.ai4se/knowledge/admin-goods-write-boundary.md`。模型读到了现有商品写入的事务、最低价、购物车缓存和订单历史
边界；由于本卡 write-scope 被人为限定为新的 `litemall-core/.../goods/policy/` 纯策略目录，Plan 没有越界改
`AdminGoodsService`、Controller、数据库或 UI。

这里还有一个同样重要的“未读”：结算/促销知识与本卡无关，所以未送入上下文。这种按相关性检索比把三篇
知识和所有历史 Story 塞给模型更可控。

### 3.2 代码与验证

Commit `6e3eaa7f` 新增 4 个实现类和 `GoodsVariantPublicationPolicyServiceTest`，共 5 个文件、254 行新增；
范围同样仅限 `litemall-core/.../goods/policy/` 及其测试目录。5 个冻结 Probe（SKU、字段值、聚合、发布状态、
有效发布）均 exit 0 / `PROVEN`，Review 为 PASS。队列最终记录为：

```properties
mode=SERIAL
story.1=promotion-tiered-pricing-r001-r3 dependency=NONE
story.2=goods-variant-publication-policy-r002 dependency=REQUIRES_ACCEPTED_PARENT parent=promotion-tiered-pricing-r001-r3
```

## 4. 验证中暴露的问题、修复和留下的证据

这不是一次“从未失败”的演示。失败证据保留在客户仓 `.story/`：

| 运行 | 真实问题 | 系统处理 | 后续修复 |
|---|---|---|---|
| `promotion-tiered-pricing-r001` | 模型生成的测试名与冻结 Probe selector 不同 | 3 轮预算后停止，无交付 commit | Development P1 强制带入冻结 Probe；Verification 把首个失败命令与输出放入 Defect Package |
| `promotion-tiered-pricing-r001-r2` | 精确 Maven `-Dtest` Probe 错抄正式入口的 `-am`，在无测试的 reactor 依赖模块失败 | 重复 diff 后 `FAILED_NO_PROGRESS`，无交付 commit | 禁止 `-am` + exact test candidate；要求 exact selector 由目标模块运行并开启 `surefire.failIfNoSpecifiedTests=true` |
| r3 / 卡 2 | 没有业务修复回合 | 首轮全部探针通过后交付 | 证明修复后的 P1/Probe 契约能实际跑通，不是凭模型自评放行 |

Runtime 为这些经验新增或强化了：ASSUMABLE 澄清后受控 resume、冻结 Probe 在 Development/Repair 中可见、
首个失败命令/输出结构化传递、失败 Story 仅作为串行历史而不携带业务脏 diff、Maven reactor 探针校验、
精确允许 `.ai4se/queue/serial-queue.properties` 作为运行时控制状态。所有这些 Runtime 改动均在
`codex/customer-production-runtime`，本次最终 `mvn clean test` 通过 11 个模块，编排模块 269 项测试无失败。

## 5. 这次真正证明了什么，未证明什么

### 已证明

- 客户仓内可建立可追溯的确定性事实、模型化知识候选、人工批准和 checkpoint；
- 需求歧义会形成问题、停止、注入人类决定并继续，而不是模型私自选方案；
- 规格、Plan、约束、写入范围和每条 AC Probe 可冻结并按角色交接；
- 已批准 Plan 后，可用受控 `codex-cli` 运行开发、验证、Review 与 local commit；
- 每张成功卡都有独立 AC 命令、exit、Probe SHA、Review sidecar、范围检查和本地提交；
- 两张卡可依据前卡验收串行推进。

### 未证明 / 不能据此宣称

- 两张卡是 **纯 in-memory `litemall-core` 策略模块**，没有接到 Admin/Wx HTTP、数据库、前端、支付、库存、
  UAT、部署或回滚；
- 本次关键批准和验收由实验身份模拟，真实客户运行必须由真实角色操作；
- 目前没有多卡并行写同一仓、自动供应商切换或自动知识正文回写；
- 促销卡的知识检索漏命中，需在真实接入前补充标签或显式影响面检索规则；
- “Probe 全绿”表示已证明该冻结规格的测试行为，不等于所有客户环境、全链路集成和运营体验都已验证。

## 6. 证据导航

| 目标 | 客户仓路径 |
|---|---|
| 机器事实与知识索引 | `.ai4se/repository/`、`.ai4se/index/knowledge.yaml` |
| 已批准知识 | `.ai4se/knowledge/*.md` |
| 卡 1 成功全链路 | `.story/promotion-tiered-pricing-r001-r3/` |
| 卡 1 失败/修复历史 | `.story/promotion-tiered-pricing-r001/`、`.story/promotion-tiered-pricing-r001-r2/` |
| 卡 2 成功全链路 | `.story/goods-variant-publication-policy-r002/` |
| 冻结 Probe | `.ai4se/acceptance-probes/<story-id>/` |
| 串行状态 | `.ai4se/queue/serial-queue.properties` |
| 交付源码 | Git `c212e0ec`、`6e3eaa7f` |

客户实际第一天的可执行步骤见 [客户仓第一天使用指南](../00-product/customer-host-bridge-day-one-guide.md)；
完整 Bridge 命令与停止规则见 [Host Bridge Runbook](./customer-host-bridge-runbook-v1.md)。
