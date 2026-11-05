# Pathway Evidence templates

V3/V4 通路签收证据包形状（放在客户仓 `.story/<id>/pathway-evidence/`）。  
真源说明与签收八问见仓库根 [PATHWAY-VERIFICATION-HANDBOOK.md](../../PATHWAY-VERIFICATION-HANDBOOK.md) §6。

## 最低集

```text
pathway-evidence/
  meta.yaml           # A|B、Wave、Adapter、剧本（V3|V4）、spine_mode、adapter_invoked、signoff_claim
  story/              # 需求与 acceptance
  packages/           # 各阶段清单+hash（回环两轮必可 diff；Verify P1 须嵌入 AC+Diff）
  gap-plan/           # Discovery|skip、Gap、Allowed、批准
  verification/       # Report、命令、出口码、verdict_basis
  defects/            # 若有
  review/             # 结论
  delivery/           # sha 或 awaiting
  audit-host.md       # 无回流、无 Push；披露 spine_mode
```

`spine_mode: fixture_control` = Runner 代写 Discovery/Plan/Approval，**不算** Adapter 挂机签收。  
`spine_mode: adapter_driven` + `adapter_invoked: true` 才可作挂机签收候选。

W9 起可有：`sessions/`（每跳 resume|new）、`compress/`（保留 AC/Allowed/Defect，不灌聊天）。  
W10 起可有：`acceptance/`（人验收）、`lifecycle/`（applied 或 noop）。

字段名可演进；**语义**以手册为准。本目录不放客户业务正文样例。
