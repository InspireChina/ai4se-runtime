# Evidence — S0–S9 压测（不开新关）

date: 2026-07-30  
playbook: `docs/build-pathway-playbook.md`  
intent: 暂时不痛 → **不新开 S8b/c / S10+**；用已关闭台阶压准真需求。  
role: AGENT_DECLARE only — Reviewer 可抽检命令/产物。

## 压测范围（人话）

| 层 | 压什么 | 不算什么 |
|----|--------|----------|
| Engine S4–S9 | 人闸 / 门禁 / 双产物 / Verify / 摸底 / 双 Profile | 外部客户真仓 |
| 交付链 | timeout 真仓连续、REST 第二形态、三场景边界应力 | 新台阶、Claude、Scheduler |
| 负例 | Loop 再执行 FAIL + 回退建议 | 「全绿就算完美产品」 |

## 结果矩阵

| # | 命令 / 检查 | Result |
|---|-------------|--------|
| 1 | `mvn -pl ai4se-runtime-engine test -Dtest=HumanWaitIntegrationTest,StageGateIntegrationTest,PlanDualProductIntegrationTest,PlanVerifyIntegrationTest,DiscoveryIntegrationTest,SecondProjectIntegrationTest` | **PASS** |
| 2 | `mvn -pl ai4se-demo test -Dtest=FirstProductionDeliveryTest,RealRepoContinuousDoDTest,SecondShapeContinuousDoDTest,BoundaryStressTest,LoopReExecutionTest` | **PASS** |
| 3a | `exec:java …FirstProductionDeliveryMain` | **PASS** exit=0；六阶段 SUCCEEDED |
| 3b | `exec:java …RealRepoContinuousDoDMain` | **PASS** exit=0；A1–A3 provenance |
| 3c | `exec:java …SecondShapeContinuousDoDMain` | **PASS** exit=0；REST ≠ timeout |
| 3d | `exec:java …BoundaryStressMain` | **PASS** exit=0；config + REST + cross-module |
| 4 | 负例 `ai4se-demo/target/loop-reexecution-test-fail/DELIVERY_REPORT.md` | **PASS** — `Delivery Result: FAIL` + `Recommended return: Execution` |
| 5 | `exec:java …PilotAnalysisMain`（timeout 摸底 Bundle） | **PASS** exit=0；gap=ASSUMABLE；有 candidates |

前置：`mvn -pl ai4se-demo -am install -DskipTests`。

## 准真需求对照（压到了什么）

| 需求形状 | 工作区 | 结论 |
|----------|--------|------|
| 配置 / timeout | `first-delivery-workspace` + RealRepo continuous | 连续 DoD 绿 |
| REST API | `stress-workspaces/02-rest-api` | 第二形态连续 DoD 绿 |
| 跨模块 | `stress-workspaces/03-cross-module`（BoundaryStress） | 六阶段绿 |
| 分析摸底 | `pilot-workspace` Order timeout | Bundle 合法写出 |

## 诚实边界（禁止偷标）

- **不是**外部生产多仓 / 客户真需求线上压测  
- **不是** S8b/c 或 S10+ 已解锁  
- Demo 串行仍靠 `DemoGateBundle` 种子门禁 kinds；≠ StageRunner  
- Engine Map+Search（S8a）与 Demo 交付 DISCOVERY（shell 探测）仍是双轨

## 决策建议（给 Reviewer）

1. 压测全绿 → **继续停在 S0–S9**，拿真实业务仓再压一轮更有价值。  
2. 仍不痛 → **不要开** S8b/c / Resume / Claude。  
3. 若某条真需求失败 → 用失败形态决定下一刀（重复摸底→S8c；跑挂→Resume；多语言→多语言），再解锁。

## Verdict

**AGENT_DECLARE: PASS** — S0–S9 在准真需求三形态 + Engine 台阶复跑下站住；**未开新关**。

## Reviewer close（2026-07-30 · 用户授权抽检关闭）

| 抽检项 | 结果 |
|--------|------|
| Engine S4–S9 集成测试复跑 | **PASS** exit=0 |
| Demo 压测测试复跑 | **PASS** exit=0 |
| `FirstProductionDeliveryMain` | **PASS** exit=0；六阶段 SUCCEEDED |
| `RealRepoContinuousDoDMain` | **PASS** exit=0；A1–A3 provenance |
| `BoundaryStressMain` | **PASS** exit=0 |
| 负例 `loop-reexecution-test-fail/DELIVERY_REPORT.md` | **PASS** — `Delivery Result: FAIL` + `Recommended return: Execution` |
| Not claimed / 诚实边界 | **OK** — 未偷标外部多仓 / S8b/c / Scheduler |

**REVIEWER_CLOSE: CLOSED**  
**下一刀：** 给外部真实业务仓路径再压；仍不痛则继续停在 S0–S9，**不开** S8b/c / Resume / Claude。
