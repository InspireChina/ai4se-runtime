# 建造通路手册（瘦身后）

> **施工与验证真源（仓库根）：** [`../../PATHWAY-VERIFICATION-HANDBOOK.md`](../../PATHWAY-VERIFICATION-HANDBOOK.md)  
> **产品真源（八能力域）：** [`../00-product/capability-map.md`](../00-product/capability-map.md)  
> **Context Engineering：** [`../20-context-engineering/`](../20-context-engineering/README.md)  
> **验证速查：** [`pathway-verification-guide.md`](./pathway-verification-guide.md)  

水位摘要服从根目录通路验证手册；Gate / 揪偏 / 防劣化以手册为准。

**编号勿混：** 手册 **S0–S6 = 交付车站**；本文 **E0–E9 = Runtime/Engine 底座台阶**。不是同一套编号。

---

## 0. 定位

**定义 AI 如何交付软件。** 产品 = 八能力域；Engine 只是支撑。

| 层 | 职责 |
|----|------|
| L0 | 薄 Engine（`docs/70-runtime-foundation/`） |
| L1 | 八能力域 Contract / SOP |
| L2/L3 | 本文：水位 / Now / 禁止什么 |

---

## 1. 底座水位（E0–E9 最小）

| 台阶 | 状态 |
|------|------|
| E0 ADR-0016 | ✅ |
| E1–E2 | ✅ |
| E3 | ✅ 部分（Result 字段） |
| E4–E9 | ✅ **最小** |
| E8b/c · Resume 产品 · 本仓 Graph · Scheduler 产品 | ❌ 锁定 |

证据在集成测试（engine 模块）。**不再维护** `*-evidence.md` 海。

交付施工顺序见手册 **Wave W0–W11**（与 E* 无关）。

---

## 2. 交付验证

编排试验 ≠ 客户现场营业。

**已通：** W1–W10 **控制面** + 现场 yudao `hybrid_adapter_dev`（Cursor Analysis/Plan/Dev 可挂机 + 真测；B 脱敏仍可 Functional 预置 Dev，见 [`b-suite-signoff-pointer.md`](./b-suite-signoff-pointer.md)）。  
**未通 / 禁止称通路通：** 自然 V4（非 seeded）；Gap/Clarification 诚实熔断签收；全站 `adapter_driven`；多 Story 队列。  
**附录 A 已落地（隔离，不顶替主链）：** Claude Adapter；Rule 触顶不丢；闪断 Resume。

```bash
mvn -pl ai4se-context,ai4se-execution,ai4se-orchestration -am test
```

---

## 3. Now

> **做：** 主链缺口 — 自然 V4、Gap/Clarification 诚实停、S5 口径、水位与证据一致。  
> **已硬：** 现场三角色挂机；Verify 嵌入 P1；诚实 verdict_basis / adapter_kind / low_risk_auto。  
> **不做：** 第九能力域、Graph/Ranking、把 hybrid/Functional/seeded-V4 绿写成通路通。  
> **客户桌面：** `external-readonly-pressure-prompt.md`

---

## 4. 文档

见 [`../README.md`](../README.md) 与 [capability-map](../00-product/capability-map.md)。  
合同挂在 `10`–`60`；Runtime 在 `70-runtime-foundation`；本区在 `90-status`。

---

## 5. 门禁自拍禁令

AI 自填 Yes ≠ 通过。Reviewer 抽检产物或复跑命令。
