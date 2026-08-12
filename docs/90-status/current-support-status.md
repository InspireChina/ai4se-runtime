# 当前支持状态

> 八能力域：[capability-map](../00-product/capability-map.md) · 水位：[build-pathway-playbook.md](./build-pathway-playbook.md) · 施工：[通路验证手册](../../PATHWAY-VERIFICATION-HANDBOOK.md)

## 一句话

**控制面 W1–W10 + 现场 yudao hybrid（Cursor Analysis/Plan/Dev 挂机 + 真测 + 本地 Commit）已通。禁止称「通路通」。**
签收口径：`signoff_claim: adapter_spine_wiring`（≠ `adapter_driven`，≠ 通路通）。
**M1 PR1：** 正式 jar 入口 `Ai4seMain` + `ProductionPathway` strict facade（四角色 Cursor、REQUIRE_ACK、LOCAL_COMMIT、Lifecycle SKIP）；`legacy-fixture` 保留旧 Kernel input 管道。

## 有（实现水位）

- **W1–W10 控制面：** 建槽 / Story+Package / 状态机 / Gap·Allowed 门禁 / Dev 限面；Cursor/Claude Adapter
- **现场 B（yudao）：** V3 多 Story；Analysis/Plan/Dev 可 `--*-adapter cursor`；低风险 Plan 自动批（Allowed⊆hint）；部分真人 S5
- **V4 控制回环：** FAIL→Defect→再 Dev→PASS **已接线**
  - Field seeded：`--preset v4-yuantofen`（须披露）
  - Field natural：`--v4-fail-mode natural`；纯 Cursor 首轮可能 PASS→Control **诚实拒**；`--v4-round1 incomplete` 可稳定走完回环（`v4_round1_source: incomplete_hook`，≠ seeded ×10）
- **W7：** Verify 先装包；P1 嵌 AC+Diff；`verdict_basis: customer_entries_all_exit_codes`（多 entry 合取）
- **执行就绪（问题类加固）：** `ShellExecutable`（resolve / launchArgv / **resolveCommand**）；Preflight **按 Adapter 实例** `resolvedBinary()`；Onboard `requireUsable`；BSuite/ShellWorker 不裸启 `mvn`
- **Review 真环：** `ReviewPackageBuilder` + `reviewAdapter`；无 Adapter 时须显式 `allowReviewFixture` / Field `--review-fixture`；`review_source` 必披露；驳回不进 Delivery
- **知识写读：** `KnowledgeIndexReader` → Analysis Package hits；`APPLY_LEARNING` 拒绝静默 FIXTURE 验收
- **Gap/附件：** Analysis Adapter **必须**写 `gap.report.properties`（禁 runner 静默 CLEAR）；`AssumablePolicy.REQUIRE_ACK`（Field 默认）；`requirement-attachments/` 槽位；声明附件缺失拒跑
- **按角色模型：** `.ai4se/runtime/role-models.yaml` + `AI4SE_MODEL_*` + Field `--model-*`；Claude/Cursor 透传 `--model`（分析/编码/验收可分模型）
- **M1 正式 CLI（PR1）：** `java -jar ai4se-runtime.jar run --workspace … --story … --requirement … --write-scope …`；`ProductionPathway` 拒绝 Functional / fixture Review / DevMutation；operator writeScope 为 Plan Allowed 上限（目录前缀 ⊆）
- **M1 有界循环（PR2）：** `BoundedDeliveryLoop` 按 `maxDevelopmentRounds` 真实 Dev↔Verify，不预判 PASS/FAIL；支持 first-pass / fail-then-pass / budget / no-progress / ENV_FAIL；fixture `Script` V3/V4 保留
- **M1 可恢复终态（PR3）：** `.story/<id>/run/{state.properties,events.jsonl,failure-fingerprint}`；`ProductionTerminal` 退出码 0/20/21/30/31/40/41/50；CLI `status` / `resume`；stage_completed 边界恢复，不重复 local commit
- **M1 PR4 实验工具（非最终签收）：** [m1-pr4-real-story-ab-playbook.md](./m1-pr4-real-story-ab-playbook.md)；只读 `scorecard`（`openExisting` + commit/verify 独立核验）；记分表 [m1-pr4-scorecard.csv](./m1-pr4-scorecard.csv)
- **附录 A（部分隔离）：** Claude Adapter；Rule 触顶；闪断 Resume

## 没有 / 未签（相对手册通路通）

- **M1 真实 Story 签收（PR4 结果）：** 10×A/B 尚未在本仓写入脱敏结果；不得用 fixture/Functional 冒充
- 对外「通路通」（须自然 V4、Gap/Clarification 诚实停可复查、八问无假绿等）
- Clarification **真熔断站** 仍多为开跑预填，非 Analysis 自发停站
- 全站 `adapter_driven`；多 Story 队列 / Stop 后自动 Resume 产品
- 开跑 `--allowed` / `--verify-command` 仍人给（人闸输入，须披露）
- Push；S5 人验收后写 Learning（Field 默认 await→SKIP lifecycle）；S6 写 Facts 未成默认产品路径
- V4 自然首轮若 PASS → Control 诚实拒（稳定回环仍靠 incomplete_hook / seeded）

## 资产宿主

客户仓：`.ai4se/` · Knowledge · Learning · `.story/` · `pathway-evidence/`
本仓：八域 Contract、平台模板、薄 Runtime、`ai4se-context`
