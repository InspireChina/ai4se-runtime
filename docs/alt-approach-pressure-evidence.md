# Evidence Delta — Alternate-approach pressure (B, not serial DoD)

playbook: docs/build-pathway-playbook.md · pressure mode
question: Can S0–S9 hold when we **do not** reuse SerialDelivery happy path?
workspace: `stress-workspaces/04-cli-batch` (CLI/CSV ≠ config/REST/cross-module)
honest: **样例仓 B** — not external production repo

## A. Blind analysis (match shape, no Execution)

- gap: CLEAR
- mayPlan: true
- ImportCli in candidates: true
- patches applied: **false** (by design)
- status: **PASS**

## B. Cross-domain honesty (促销需求砸到 CLI 仓)

- gap: BLOCKED
- mayPlan: false
- clarify questions: 7
- status: **PASS** (mayPlan=false)

## C. Engine adversarial + DISCOVERY (same Runtime, no Demo serial)

- PLAN without hit-set rejected, worker=0: true
- DISCOVERY committed hit-set: true
- hit-set recalls ImportCli: true
- DISCOVERY skipped Worker: true
- DISCOVERY status: SUCCEEDED
- VERIFY command=skip refused: true
- VERIFY green cites C1 + Trace: true
- status: **PASS**

## Evidence Delta (new only)

| New evidence | Result |
|--------------|--------|
| CLI-batch shape (≠ prior three) | used |
| Blind analysis + cross-domain mayPlan=false | see A/B |
| Engine DISCOVERY/gate/skip — no SerialDelivery | see C |

Not claimed: external business repo, S8b/c, S10+, Claude.
Not re-scored: FirstProduction / BoundaryStress serial paths.

## Verdict

**PASS** — alternate approach pressure holds on sample B.

## Reviewer close（2026-07-30 · 用户授权抽检关闭）

| 抽检项 | 结果 |
|--------|------|
| `AltApproachPressureMain` 复跑 | **PASS** exit=0 |
| A 盲摸底 mayPlan + ImportCli | **OK** |
| B 跨域促销→CLI 仓 BLOCKED / mayPlan=false | **OK**（诚实） |
| C Engine 对抗（无 SerialDelivery） | **OK** |
| Not claimed：外部仓 / S8b/c / Claude | **OK** |

**REVIEWER_CLOSE: CLOSED**  
注：本文件正文可由 Main 再生；关闭印章以本段 + 手册状态表为准。
