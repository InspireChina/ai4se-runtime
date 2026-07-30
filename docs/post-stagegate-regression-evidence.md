# Evidence Delta — Post–StageGate multi-dimension regression

playbook: docs/build-pathway-playbook.md §2.1.1 / §1.5  
question: After DemoGateBundle fix, are closed gates still green, and is water-level honest?  
role: AGENT_DECLARE only — Reviewer must close.  
date: 2026-07-30

## Dimension matrix

| # | Dimension | Command / check | Result |
|---|-----------|-----------------|--------|
| 1 | Full suite green | `mvn clean test` | **PASS** (reactor SUCCESS; demo ~21s) |
| 2a | First Production Main | `exec:java …FirstProductionDeliveryMain` | **PASS** exit=0; 6 stages SUCCEEDED |
| 2b | Real-repo continuous | `exec:java …RealRepoContinuousDoDMain` | **PASS** exit=0; DISCOVERY…DELIVERY true; A1–A3 provenance |
| 2c | Retrospective harness | `exec:java …PathwayRetrospectiveMain` | **PASS** exit=0; 7 prior Mains exit 0; water honesty PASS |
| 3 | FAIL negative spot-check | `LoopReExecutionTest` → `…/loop-reexecution-test-fail/DELIVERY_REPORT.md` | **PASS** — contains `Delivery Result: FAIL` + `Recommended return: Execution` |
| 4 | Water honesty | playbook + `current-support-status` | **PASS** — S0/S4–S8a ✅ 最小; S8b/c ❌; no Scheduler/Resume claim |
| 5 | Dual-track boundary | code + status | **PASS** — see § Dual-track |

## 1. Full suite

```text
mvn clean test  →  BUILD SUCCESS  (2026-07-30T18:19:37+08:00)
```

## 2. Closed-gate Mains (independent re-run)

| Main | exit | Notes |
|------|------|-------|
| FirstProductionDeliveryMain | 0 | DISCOVERY…DELIVERY all success=true |
| RealRepoContinuousDoDMain | 0 | Matrix + provenance A1,A2,A3; Delivery PASS |
| PathwayRetrospectiveMain | 0 | Subprocess: PathwayVerification + Sprint A/B/C + Acceptance + RealRepo + SecondShape all 0 |

Prerequisite: `mvn -pl ai4se-demo -am install -DskipTests` before `exec:java` (avoids stale classpath / missing StageGate on exec).

## 3. Negative artifact (anti false-green)

Path: `ai4se-demo/target/loop-reexecution-test-fail/DELIVERY_REPORT.md`

- `Delivery Result: FAIL`
- `Recommended return: Execution`

Confirms §2.2 local-return text still written on shell/matrix fail path.

## 4. Water honesty (facts)

| Claim | Source | OK? |
|-------|--------|-----|
| S0 ADR ✅ | playbook water table | yes |
| S4–S8a ✅ 最小 | playbook + HumanWait/StageGate/Plan*/Discovery tests | yes |
| S8b/c ❌ | playbook | yes |
| No Scheduler / Resume product | `current-support-status` 「没有」 | yes |
| Ban Graph / Claude / Workflow DSL | playbook Next / Now | yes |
| Not claiming S9 multi-repo done | S9 ✅ 最小（双 Profile）；支持状态页禁止外部多仓宣称 | yes |

## 5. Dual-track boundary

| Track | Owner | What it proves | What it does **not** prove |
|-------|-------|----------------|----------------------------|
| Engine S5–S8a | `StageGate` / `MapSearchDiscovery` / integration tests | Missing kinds reject Worker; Engine DISCOVERY can BLOCKED | Full delivery orchestration |
| Demo serial | `SerialDeliveryRunner` + **`DemoGateBundle`** | Fixture delivery still runs under StageGate | Real MapSearch hit-set; real human approve; real Acceptance coverage (`DEMO-VERIFY` citing is transport-only) |
| Demo probe goal | `GIT_STATUS` | Shell probe without triggering Engine `isDiscoveryGoal` | Engine S8a discovery |

**ALIGN:** Demo seeds ≠ StageRunner; Engine remains single-submit Orchestrator (ADR-0016).

## Not claimed

- Reviewer close of this gate  
- S9 external multi-repo  
- S8b/c / Scheduler / Resume / Claude / StageRunner  
- That `DEMO-VERIFY` equals product Acceptance Provenance  

## Verdict

**AGENT_DECLARE: PASS** — multi-dimension regression after DemoGateBundle is green; water honest; dual-track boundary explicit.  

## Reviewer close（2026-07-30 · 用户授权抽检关闭）

| 抽检项 | 结果 |
|--------|------|
| 证据 md Verdict / Not claimed | **OK** |
| 负例 FAIL 产物关键句 | **PASS**（与压测同一路径复验） |
| Water：S0–S9 最小 / S8b/c 锁定 / 无 Scheduler·Resume 产品宣称 | **OK**（已对齐 `current-support-status` + 大白话现状） |
| Dual-track：DemoGateBundle ≠ StageRunner | **OK** |

**REVIEWER_CLOSE: CLOSED**  
**Suggested next：** **不开新关**；等待外部业务仓路径。勿因本 PASS 开 Graph / Claude / DSL。
