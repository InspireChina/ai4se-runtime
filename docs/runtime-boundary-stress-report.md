# Sprint-9 Runtime Boundary Stress Report

> Evidence: 3 different real deliveries via existing Runtime.submit + Workers.
> No new Runtime / Domain / StageRunner / Scheduler / Workflow / Planning engine.

Overall: PASS · deliveries=3

## Deliveries executed

### 01-config — 修改配置 · PASS

- Requirement: Make FeatureFlags.isFeatureEnabled() read app.feature.enabled from application.properties (false in fixture). Update README config contract.
- Discovery command: `git status`
- Workspace: `/Users/peng.lv/IdeaProjects/ai4se-runtime/ai4se-demo/target/stress-01-config`
- Stages:
  - DISCOVERY: OK worker=worker_shell goal=GIT_STATUS artifactNamesViaLabels=see RuntimeResult cp=cp_9_5199dc19 ms=22
  - PLAN: OK worker=worker_file_edit goal=PLAN artifactNamesViaLabels=see RuntimeResult cp=cp_22_958768b4 ms=1
  - EXECUTION: OK worker=worker_file_edit goal=EXECUTION artifactNamesViaLabels=see RuntimeResult cp=cp_33_c7ce3322 ms=0
  - VERIFICATION: OK worker=worker_shell goal=VERIFICATION artifactNamesViaLabels=see RuntimeResult cp=cp_44_8a3aa9b3 ms=1414
  - REVIEW: OK worker=worker_file_edit goal=REVIEW artifactNamesViaLabels=see RuntimeResult cp=cp_54_7afbed1d ms=1
  - DELIVERY: OK worker=worker_file_edit goal=DELIVERY artifactNamesViaLabels=see RuntimeResult cp=cp_64_7b99f4ce ms=0

### 02-rest-api — 新增 REST API · PASS

- Requirement: Add UserApi handling GET /api/users/{id}: 200 JSON for known ids, 404 JSON error for unknown. Document endpoint in README.
- Discovery command: `pwd`
- Workspace: `/Users/peng.lv/IdeaProjects/ai4se-runtime/ai4se-demo/target/stress-02-rest-api`
- Stages:
  - DISCOVERY: OK worker=worker_shell goal=GIT_STATUS artifactNamesViaLabels=see RuntimeResult cp=cp_74_412e1129 ms=8
  - PLAN: OK worker=worker_file_edit goal=PLAN artifactNamesViaLabels=see RuntimeResult cp=cp_87_86fd7fc6 ms=0
  - EXECUTION: OK worker=worker_file_edit goal=EXECUTION artifactNamesViaLabels=see RuntimeResult cp=cp_98_94e715b4 ms=0
  - VERIFICATION: OK worker=worker_shell goal=VERIFICATION artifactNamesViaLabels=see RuntimeResult cp=cp_109_030d2d3a ms=1389
  - REVIEW: OK worker=worker_file_edit goal=REVIEW artifactNamesViaLabels=see RuntimeResult cp=cp_119_8e1b9096 ms=0
  - DELIVERY: OK worker=worker_file_edit goal=DELIVERY artifactNamesViaLabels=see RuntimeResult cp=cp_129_2dcff5a9 ms=0

### 03-cross-module — 跨模块修改 · PASS

- Requirement: Fix PriceCalculator (core) to apply 10% tax and update OrderFacade.taxPolicy() (api) to report tax=10%. Both modules must pass OrderFacadeTest.
- Discovery command: `git status`
- Workspace: `/Users/peng.lv/IdeaProjects/ai4se-runtime/ai4se-demo/target/stress-03-cross-module`
- Stages:
  - DISCOVERY: OK worker=worker_shell goal=GIT_STATUS artifactNamesViaLabels=see RuntimeResult cp=cp_139_8bc28005 ms=18
  - PLAN: OK worker=worker_file_edit goal=PLAN artifactNamesViaLabels=see RuntimeResult cp=cp_152_79a3d490 ms=0
  - EXECUTION: OK worker=worker_file_edit goal=EXECUTION artifactNamesViaLabels=see RuntimeResult cp=cp_163_5dd577f9 ms=0
  - VERIFICATION: OK worker=worker_shell goal=VERIFICATION artifactNamesViaLabels=see RuntimeResult cp=cp_174_e185af29 ms=1372
  - REVIEW: OK worker=worker_file_edit goal=REVIEW artifactNamesViaLabels=see RuntimeResult cp=cp_184_edcd9bb5 ms=0
  - DELIVERY: OK worker=worker_file_edit goal=DELIVERY artifactNamesViaLabels=see RuntimeResult cp=cp_194_e0b752df ms=7

---

## ① Runtime 职责 — 三次都没有变化

三次交付中，Runtime 每步仍且只做：

1. 创建 Task + 推进 STARTING/RUNNING/SUCCEEDED|FAILED
2. 打开/冻结 ExecutionContext
3. Trace spans: submit → worker → artifact → finish
4. **一次** `Worker.execute`
5. WorkResult → Artifact COMMITTED/ABANDONED
6. Checkpoint 只写
7. 返回 RuntimeResult（checkpointId / durationMs / workerId / goalType）

举证：每种需求各 6 次 submit，lifecycle 形状一致；需求类型（配置 / REST / 跨模块）未改变 Runtime 代码路径。

## ② Worker — 变化最大

| 变化面 | 证据 |
|--------|------|
| Discovery 命令 | `git status`（01/03）vs `pwd`（02）— 同 ShellWorker，不同 params |
| Execution 文件集 | FeatureFlags vs UserApi 新建 vs core+api 双文件 — FileEditWorker params 不同 |
| Verification 耗时 | mvn test 主导 duration；业务越重 Worker 侧越慢，Runtime 结算不变 |
| Worker 种类本身 | 仍只有 ShellWorker + FileEditWorker；**未新增 Worker 类** |

结论：边界压力主要落在 **Worker 输入（command/files）**，不是 Runtime 类型系统。

## ③ Artifact — 已经稳定

| Artifact name | 生产者 | 稳定性 |
|---------------|--------|--------|
| `shell-stdout.txt` | ShellWorker | 三次 Discovery/Verification 均此名 |
| `file-edit-manifest.txt` | FileEditWorker | Plan/Execution/Review/Delivery 均此名 |
| Kernel ArtifactKind | — | **未新增**；仅 name 区分 |
| workspace 报告文件 | FileEdit 写入 | PLAN/REVIEW/DELIVERY.md 是文件不是 Kernel Object |

## ④ 哪些阶段其实属于 Demo

| 阶段 | Runtime? | Demo? | 证据 |
|------|----------|-------|------|
| Requirement 文本 | No | Yes | 各 Scenario.requirement() |
| 阶段顺序与失败停机 | No | Yes | SerialDeliveryRunner |
| Discovery 是否用 git 或 pwd | No | Yes | scenario.discoveryCommand |
| Plan 正文 | No | Yes | scenario.planFiles |
| Execution 补丁内容 | No | Yes | scenario.executionFiles |
| Review/Delivery 叙述 | No | Yes | Formatters |
| 单步结算 | Yes | No | Runtime.submit |
| git/mvn/写文件 | Worker | 发起在 Demo | — |

## ⑤ 想抽象但证据仍不足

| 冲动 | 为何不足 |
|------|----------|
| StageRunner 进 Runtime | 三次阶段名相同，但是 **人为复用同一 Demo runner**；Discovery 命令已分叉（git vs pwd）；未证明所有真实项目都要这六段 |
| Planning 引擎 | Plan 仍是 Demo 预写 markdown，无搜索/推理 |
| 统一 DeliveryReport schema 进 Kernel | 报告仅为 Review 文档；三次内容结构相同因 formatter 相同，非业务必然 |
| 按需求类型分流的 Capability | 仅 3 个样本，且全是 FileEdit+Shell |

## ⑥ 可进 Runtime vs 留在 Demo

### 真正可以留在 / 保持在 Runtime（已稳定）

- 单步 Task/Context/Trace/Artifact/Checkpoint/`RuntimeResult` 结算
- Budget 超时下传
- Worker SPI 无关业务类型

### 继续留在 Demo

- Scenario 选择与补丁库
- 串行六阶段编排（SerialDeliveryRunner）
- Review/Delivery markdown
- Discovery 命令策略

### 仍禁止进入 Runtime（本 Sprint）

- StageRunner / Scheduler / Workflow / Planning 引擎 / 新 Domain

---

# ChatGPT Review Package

## 1 Runtime Stable Boundary

- Single-submit lifecycle unchanged across 3 requirement types
- Artifact+Checkpoint+Trace+RuntimeResult shape unchanged
- No Runtime code added for stress

## 2 Runtime Unstable Boundary

- None inside Runtime path
- Instability sits in Demo scenario inputs (commands, file maps, discovery choice)
- Naming debt: workflowId still present but unused as Workflow

## 3 Worker Evolution

- No new Worker class
- ShellWorker params: git status | pwd | mvn test
- FileEditWorker params: config patch | new API class | cross-module dual patch
- Largest variance = Worker inputs, not SPI

## 4 Artifact Stability

- Stable names: shell-stdout.txt, file-edit-manifest.txt
- No new Kernel Artifact type
- Report files remain workspace artifacts via FileEdit

## 5 Runtime Purity Check

- PASS: no business/config/REST/cross-module knowledge in Runtime
- Demo owns SerialDeliveryRunner + scenarios
- Workers own side effects

## 6 Architecture Drift

- No Domain / StageRunner / Scheduler / Workflow / Planning engine added
- Drift risk: Demo scenario pack growing (acceptable under stress charter)
- Frozen invariants preserved

## 7 Architecture Recommendation

- Keep Runtime boundary frozen as-is
- Do **not** promote StageRunner yet (stage list enforced by Demo reuse, not nature)
- Prefer more real deliveries OR Worker allowlist growth over Kernel growth

## 8 Next Sprint Proposal

**Proposal: Second Production Delivery on a non-sample target (or Human-wait gate if a real clarify blocker appears)** — still no StageRunner.

- Why: stress proved Runtime stable; next risk is Demo-only orchestration meeting a requirement that needs human clarify or non-mvn verify.
- Why not StageRunner: three identical stage shells were Demo-authored; Discovery already diverged; abstracting now = freeze Demo habit.
