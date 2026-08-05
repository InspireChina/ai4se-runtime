# 验证通路指南（瘦身版）

> **完整门禁 / 揪偏 / 组合验证 / 终局口令：** [`../../PATHWAY-VERIFICATION-HANDBOOK.md`](../../PATHWAY-VERIFICATION-HANDBOOK.md)  
> 产品真源：[`../00-product/capability-map.md`](../00-product/capability-map.md)  
> Context Engineering：[`../20-context-engineering/`](../20-context-engineering/README.md)  
> 水位：[`build-pathway-playbook.md`](./build-pathway-playbook.md)

## 两套验证勿混

| 套别 | 含义 | 成功 |
|------|------|------|
| **A 编排试验** | 本仓夹具 | `mvn test` + 少数 Demo Main |
| **B 真仓或脱敏仓** | 八能力域按标准跑；知识不回流本仓 | 脱敏见 `b-suite-signoff-pointer.md`；**现场真仓另列** |

A 绿 ≠ B 通。B 脱敏绿 ≠ 现场通。Stop 收场 ≠ 通路通。hybrid 脊骨绿 ≠ 通路通。

## Reviewer 速查（完整八问见手册 §6）

1. 是否违反资产宿主（回流 / Push）？  
2. 是否编 Facts / BLOCKED 进 Planning / 无 Discovery|skip 进 Plan？  
3. 是否用预置 patches 冒充 Dev？（B Functional `writeFixed` = 脊骨证明，**不得**勾非预置）  
4. 是否偏离 Context Contract（缺 P1 仍跑 / 乱喂全仓）？  
5. 回测是否空包或 Dev 续聊？  
6. Defect 是否结构化进下一轮 Dev？  
7. 是否跳 Review？仅编译当 PASS？  
8. 是否把未实现能力或 Stop / hybrid 绿勾成通路通？

## 已知缺口（相对手册通路通）

自然 V4（非 seeded）· Gap/Clarification 诚实熔断签收 · 全站 `adapter_driven` · 多 Story 队列 / Stop 自动续跑产品化

**已通（现场 hybrid，≠ 通路通）：** Cursor Analysis/Plan/Dev 挂机；低风险自动批；V4 控制回环（seeded FAIL 须披露 `v4_fail_mode`）；Clarification Stop+Resume 控制面；部分真人 S5。

已落地附录 A（隔离）：Claude Adapter；Rule 触顶；闪断 Resume。
