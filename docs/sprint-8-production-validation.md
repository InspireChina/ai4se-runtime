# Sprint-8 Production Validation（分析 only）

> Mode: Production Validation · Architecture First · No Platform Expansion  
> Evidence: First Production Delivery + Boundary Stress（配置 / REST / 跨模块）+ Boundary Validation  
> 禁止：Scheduler / Workflow / Plugin / Capability / Knowledge / StageRunner / Resume / DB / Bus / MQ  
> 本文件为分析交付物；**未改代码、未改 Architecture。**

---

# A. Delivery Contract

一次「成功交付」= 下列产物齐全且各自 PASS。  
Runtime **不拥有**整条链；它只保证带 ★ 的结算语义。

| 产物 | Owner | 输入 | 输出 | PASS | FAIL | 允许人工 | Runtime 必须保证 ★ |
|------|-------|------|------|------|------|----------|-------------------|
| **Requirement** | Human（定义）· Caller/Demo（装载） | 业务意图 | 不可歧义的目标文本 + acceptance 指针 | 可验证、范围清楚 | 空/矛盾/无验收口径 | **是（定义）** | 否（只作为 GoalSpec 载荷传递） |
| **Discovery Report** | Worker 执行 · Demo 发起 | workspace + 命令 | Artifact（如 `shell-stdout.txt`）或允许空 | 命令成功；或合同标明 Discovery=空 | 命令非 0 且合同要求非空 | 可跳过/可改命令 | ★ 若执行：单步结算 + Artifact/Trace |
| **Planning** | Demo/Human 提供内容 · Worker 落盘 | Requirement (+ Discovery) | `PLAN.md` 等文件 + FileEdit manifest Artifact | 步骤可执行、指向 Verification | 无步骤/与需求无关 | **是（写计划）** | ★ 仅落盘与 Artifact 结算；**不保证计划质量** |
| **Implementation** | Worker（副作用）· Human/Demo（补丁内容） | Plan + 补丁集 | 工作区文件变更 + manifest Artifact | 变更落地；路径不逃逸 workspace | Worker FAIL / 非法路径 | **是（写补丁）** | ★ WorkResult→Artifact；**不保证业务正确** |
| **Verification Report** | Worker | Implementation + verify 命令 | stdout Artifact + Task SUCCEEDED | exit 0（如 `mvn test`） | 非 0 / timeout | 可改命令；不可改「必须跑」若合同要求 | ★ 超时/成败结算进 RuntimeResult |
| **Review Report** | Demo/Human 叙述 · Worker 落盘 | 各阶段 RuntimeResult + 工作区 | `REVIEW_REPORT.md` | 含需求、步骤、验收、缺口 | 缺关键字段/与事实矛盾 | **是（写评审）** | ★ 落盘结算；**不保证评审客观** |
| **Delivery Report** | Demo 汇总 · Worker 落盘 · Runtime 供字段 | 全阶段结果 | `DELIVERY_REPORT.md`（TaskId/Timeline/Artifacts/Checkpoint/…） | 字段齐全且与 RuntimeResult 一致 | 缺 Checkpoint/Trace/结果等合同字段 | 可补充叙述 | ★ **单步** RuntimeResult 字段完整（含 checkpointId/duration/worker/goal） |

**合同结论（证据）：**  
交付成功 = **Caller 合同产物齐全** + **每步 Runtime 结算成功**。  
今日缺口不在 Kernel 对象，在 **Requirement→补丁仍由人在 Demo 里预写**。

---

# B. Production Gap Analysis

假设：今天接到真实 Java 项目需求（新增接口 / 修 Bug / 重构 / 加测试）。  
只谈已证明能力。

## Support Matrix

| 能力 | 已支持？ | 说明（证据） | 需人工？ |
|------|----------|--------------|----------|
| 单步 Task 生命周期结算 | **是** | FPD + Stress 共多次 submit | 否 |
| Trace / Artifact / Checkpoint 可观测 | **是** | RuntimeResult 字段 | 否 |
| 工作区写文件（受控） | **是** | FileEditWorker | 补丁内容需人 |
| 跑 `mvn -f pom.xml -q test` | **是** | Shell allowlist | 命令需人指定 |
| `git status` / `pwd` | **是** | ShellWorker | 否/可选 |
| 对**未知**仓库自动摸底（结构/依赖/热点） | **否** | Discovery 仅薄命令 | **是** |
| 从自然语言需求**生成**计划 | **否** | PLAN 预写在 Scenario | **是** |
| 从需求**生成**补丁/新类 | **否** | `executionFiles()` 硬编码 | **是** |
| 新增接口（人已写好补丁） | **部分** | Stress-02 同构 | 写接口+测试 |
| 修 Bug（人已写好补丁） | **部分** | FPD / Stress-01/03 | 定位+补丁 |
| 重构（人已写好补丁） | **理论同构未测** | 无独立样本；机制同 FileEdit | 设计+补丁 |
| 增加测试（人已写好测试文件） | **部分** | 可 FileEdit 写入 + mvn | 写测试 |
| 任意 shell / 任意构建工具 | **否** | allowlist 极窄 | 扩 allowlist 或人跑 |
| 跨多仓 / 发版 / PR | **否** | 未做 | **是** |
| 失败后 Resume | **否** | Checkpoint 只写 | **是（重跑）** |
| 无人值守接单 | **否** | 编排+内容在 Demo | **是** |

**一句话：**  
Runtime **能运送**已准备好的工程动作并留下审计链；**不能代替**工程师完成「理解项目 → 想改法 → 写出补丁」。

---

# C. Delivery Metrics

| 指标 | 含义 | 今天能否统计 | 备注 |
|------|------|--------------|------|
| Delivery Success Rate | 合同全阶段 PASS 的交付占比 | **能（Demo/测试层）** | Stress 3/3；FPD 1/1；非 Runtime 内置 |
| Verification Pass Rate | Verification 步 SUCCEEDED 占比 | **能** | 看该步 RuntimeResult |
| Average Delivery Time | 端到端 wall 时间 | **能** | 各步 `durationMs` 求和（Demo） |
| Average Human Intervention | 每交付人工介入次数 | **基本不能** | 无介入事件模型；今日介入=写 Scenario |
| Worker Failure Rate | Worker FATAL / 非 0 | **能** | RuntimeResult.success=false |
| Artifact Completeness | 合同要求的 Artifact/文件是否齐全 | **半能** | 有 ArtifactId；文件级靠工作区检查（Demo） |
| Review Rejection Rate | Review 判定不接收 | **不能** | Review 无独立 PASS 机；仅写 markdown |
| Checkpoint Density | 每交付 Checkpoint 数 / 阶段数 | **能** | 成功步均有 cp；失败步无 |

不要实现；先定合同再采数。

---

# D. 唯一建议

## 建议：Delivery Bundle（调用方交付包）——仍不进 Kernel / 不做 StageRunner

把今日藏在 `*Scenario` / `DeliveryPatches` 里的东西，收成**显式契约包**：

- `requirement.md`
- `plan/`（或单文件）
- `patches/`（相对路径→内容）
- `discovery.cmd`（可空）
- `verify.cmd`
- （可选）人工 checklist

用**现有** `SerialDeliveryRunner` + Shell/FileEdit + Runtime.submit 执行。  
目标：对**真实 Java 仓库目录**跑通一次「人写 Bundle → Runtime 运送 → mvn 验收」。

### 为什么现在值得做

真实瓶颈（证据）：不是结算不稳，而是 **Production 无法接单**——接单所需的计划/补丁仍编译在 Demo Java 里。  
不先把「人提供什么、Runtime 保证什么」做成可搬运合同，继续压测 sample = 假生产。

### 为什么不是下列各项

| 候选项 | 为何不做 |
|--------|----------|
| Learning | 无学习闭环证据；先要接得住单 |
| Knowledge | 无召回失败样本 |
| StageRunner | 编排已能跑；瓶颈在**内容来源**；且禁令明确 |
| Scheduler | 无多任务竞争 |
| Workflow | 阶段壳已够；DSL 无痛点 |
| Resume | 失败策略仍是重跑；未出现恢复刚需 |

---

# E. Architecture Drift Check

| 问 | 答 |
|----|----|
| 是否污染 Runtime？ | **本分析未改代码。** 若下一刀只做 Demo Bundle 加载：**不污染**。 |
| 是否扩大 Kernel？ | **否**（建议不扩） |
| 是否增加平台？ | **否**（Bundle≠ Plugin/Capability 平台） |
| 是否违背 Frozen？ | **否**（仍 Orchestrator + Worker SPI；Checkpoint 只写） |

---

## 是否建议开始 Sprint-8 Coding

**YES**

### 下一步应该实现什么

1. **Demo 侧** Delivery Bundle 目录约定 + 加载器（读文件 → 现有 Scenario 等价物）。  
2. 选一个**真实目标**：优先本仓库一小改，或外部 Java 模块目录。  
3. 人写一份 Bundle，跑现有六段链 + `mvn`（或合同写明的 verify）。  
4. **禁止**改 Runtime Kernel / 新增 StageRunner / Scheduler / Workflow。

### 为什么

Production Validation 要验证的是 **Delivery Pipeline 能否接生产输入**；证据缺口是「输入形态」，不是「再证明边界」。编码应只补这一刀。
