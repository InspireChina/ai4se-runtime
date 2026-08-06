# 当前支持状态

> 八能力域：[capability-map](../00-product/capability-map.md) · 水位：[build-pathway-playbook.md](./build-pathway-playbook.md) · 施工：[通路验证手册](../../PATHWAY-VERIFICATION-HANDBOOK.md)

## 一句话

**控制面 W1–W10 + 现场 yudao hybrid（Cursor Analysis/Plan/Dev 挂机 + 真测 + 本地 Commit）已通。禁止称「通路通」。**  
签收口径：`signoff_claim: adapter_spine_wiring`（≠ `adapter_driven`，≠ 通路通）。

## 有（实现水位）

- **W1–W10 控制面：** 建槽 / Story+Package / 状态机 / Gap·Allowed 门禁 / Dev 限面；Cursor/Claude Adapter  
- **现场 B（yudao）：** V3 多 Story；Analysis/Plan/Dev 可 `--*-adapter cursor`；低风险 Plan 自动批（Allowed⊆hint）；部分真人 S5  
- **V4 控制回环：** FAIL→Defect→再 Dev→PASS **已接线**  
  - Field seeded：`--preset v4-yuantofen`（须披露）  
  - Field natural：`--v4-fail-mode natural`；纯 Cursor 首轮可能 PASS→Control **诚实拒**；`--v4-round1 incomplete` 可稳定走完回环（`v4_round1_source: incomplete_hook`，≠ seeded ×10）  
- **W7：** Verify 先装包；P1 嵌 AC+Diff；`verdict_basis: customer_entries_all_exit_codes`（多 entry 合取）  
- **执行就绪（问题类加固）：** `ShellExecutable` 共享解析；`PathwayPreflight`；Claude 路径探测；Allowed **schema 拒绝**（非 peel 补丁）；Dev Package P1 含 `acceptance.md`；onboard 根+一层子目录累加探测  
- **Review 真环：** `ReviewPackageBuilder` + `reviewAdapter`；`review_source` 必披露；驳回不进 Delivery  
- **知识写读：** `KnowledgeIndexReader` → Analysis Package hits；`APPLY_LEARNING` 拒绝静默 FIXTURE 验收  
- **Gap/附件：** `AssumablePolicy.REQUIRE_ACK`；`requirement-attachments/` 槽位；声明附件缺失拒跑  
- **按角色模型：** `.ai4se/runtime/role-models.yaml` + `AI4SE_MODEL_*` + Field `--model-*`；Claude/Cursor 透传 `--model`（分析/编码/验收可分模型）  
- **附录 A（部分隔离）：** Claude Adapter；Rule 触顶；闪断 Resume  

## 没有 / 未签（相对手册通路通）

- 对外「通路通」（须自然 V4、Gap/Clarification 诚实停可复查、八问无假绿等）  
- Gap 常由 runner CLEAR（假分析风险）；Clarification **真熔断站** 正在补，非开跑预填即等于站  
- 全站 `adapter_driven`；多 Story 队列 / Stop 后自动 Resume 产品  
- 开跑 `--allowed` / `--verify-command` 仍人给（人闸输入，须披露）  
- Push；S6 写 Facts（现场默认 noop）；ASSUMABLE 默认仍为 ALLOW（压测/Field 应显式 `REQUIRE_ACK`）  

## 资产宿主

客户仓：`.ai4se/` · Knowledge · Learning · `.story/` · `pathway-evidence/`  
本仓：八域 Contract、平台模板、薄 Runtime、`ai4se-context`
