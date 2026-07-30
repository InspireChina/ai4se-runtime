# Sprint-8.5 Runtime Boundary Validation Report

> **Mode:** Architecture Validation（只分析，不改代码 / 不改 Architecture / 不新增 Domain）  
> **Evidence base:** First Production Delivery（PASS）+ 现有 `Runtime.submit` 实现  
> **禁止结论来源：** 未来平台幻想、未发生的第二/第三个交付

---

## ① Runtime Boundary Report

### Runtime 负责什么

| 职责 | 为什么 | 举证（First Production Delivery） |
|------|--------|-----------------------------------|
| 创建并推进 **单个** Task 生命周期 | Frozen：工作必须以 Task 为根 | 每阶段一次 `Runtime.submit` → 独立 `task_*`（报告列出 6 个 TaskId） |
| 打开 / 激活 / 冻结 **ExecutionContext** | 执行期唯一合法上下文 | 各阶段 lifecycle：MATERIALIZING → ACTIVE → FROZEN |
| 打开 Trace、记录 submit/worker/artifact/finish | 可审计轨迹 | 每阶段 `TraceSpans=[submit, worker, artifact, finish]` |
| 调用 **一个** `Worker.execute` | Orchestrator only；不做业务动作 | `Runtime.java` 内仅一次 `worker.execute`；六阶段换 Worker 靠 Demo 新建 `Runtime(worker)` |
| 将 `WorkResult` 映射为 Artifact COMMITTED/ABANDONED | G2/G5：产物经 Artifact | Shell → `shell-stdout.txt`；FileEdit → `file-edit-manifest.txt` |
| Artifact 后写 Checkpoint（只写不 Resume） | 可回溯边界点 | 成功阶段均有 `cp_*`；`RuntimeResult.checkpointId` 有值 |
| 汇出 `RuntimeResult`（含 status / artifacts / timeline / duration / worker / goalType） | 交付报告需要可观测结果 | Delivery Report 各阶段字段来自 `RuntimeResult`，非翻私有 store |
| 传递 Budget 超时到 Worker | 否则 Verification 被默认 30s 杀掉 | Lesson #4；Verification ~1500ms+ 需 `Budget.maxWallClock` |

### Runtime 不负责什么

| 职责 | 为什么 | 举证 |
|------|--------|------|
| 定义业务 Requirement 文本 | Requirement 是交付意图，不是 Kernel 对象 | `REQUIREMENT` 常量在 `FirstProductionDeliveryPipeline`（Demo） |
| 决定阶段顺序 / 失败是否继续下一阶段 | 无 Workflow；单次 submit 不知「下阶段」 | `run()` 里 `if (!lastOk) return` 全在 Demo |
| 选择用哪个 Worker、什么 command/files | 编排与参数是调用方知识 | Demo 分别 `new ShellWorker()` / `new FileEditWorker()` + params |
| 写 PLAN / 补丁内容 / README 文案 | 业务内容 | `DeliveryPatches` 硬编码在 Demo |
| 执行 git / mvn / 写文件 | 真实世界副作用在 Worker | Discovery=`git status`；Verification=`mvn … test`；Plan/Exec=`FileEditWorker` |
| 生成 Review/Delivery **叙述文本** | 跨阶段汇总是 Demo formatter | `ReviewReportFormatter` / `DeliveryReportFormatter` 在 submit **之前**拼好，再经 Worker 落盘 |
| 判定业务验收（timeoutMs==5000） | 验收在 sample 测试，不在 Runtime | `ConfigServiceTest` 在 sample workspace；Runtime 只看 Worker exit/status |
| Learning / Evolution / Knowledge / Scheduler / Resume | 本次未发生；禁止空抽象 | FPD Drift：**No** 此类对象 |

**一句话边界：**  
Runtime = **单步 Task 生命周期编排 + Artifact/Trace/Checkpoint 结算**。  
「一次软件工程交付」= **Demo（或未来调用方）串多次 Runtime + Worker 干活**。

---

## ② Responsibility Matrix

| 能力项 | 归属 | 原因（证据） |
|--------|------|--------------|
| **Requirement** | **Human**（定义）+ **Demo**（本次写入常量） | 人给定需求；代码里 `REQUIREMENT` 在 Demo，Runtime 只收 `GoalSpec` 字符串类型/参数 |
| **Planning** | **Demo**（内容）+ **Worker**（落盘） | Plan 正文来自 `DeliveryPatches.planFiles()`；`FileEditWorker` 只写文件。**不是** Runtime Planning 引擎 |
| **Discovery** | **Worker**（执行）+ **Demo**（发起） | `ShellWorker` 跑 `git status`；命令与是否跳过由 Demo 决定。可为空（手册亦允许） |
| **Execution** | **Worker** | 补丁内容 Demo 提供；`FileEditWorker` 写 `ConfigService` / `TimeoutSource` / README |
| **Verification** | **Worker** | `ShellWorker` 跑 `mvn -f pom.xml -q test`；Runtime 只结算 OK/FAIL |
| **Review** | **Demo**（生成叙述）+ **Worker**（落盘） | Formatter 读多阶段 `RuntimeResult` 写 markdown；再 `FileEditWorker` 写出 |
| **Delivery** | **Demo**（汇总报告）+ **Worker**（落盘）+ **Runtime**（提供单步结果字段） | 报告正文 Demo；字段来自多次 `RuntimeResult`；落盘 Worker |
| **Learning** | **Future Capability**（当前 **Human/文档**） | 仅有 Lessons Learned 文档；无 Runtime Learning 对象或回写 |
| **Evolution** | **Future Capability**（当前禁止） | 零证据；FPD 明确未做 |

说明：上表「属于 Runtime」的单元格为空或仅「提供结算字段」——因 **单次交付未证明** Runtime 应拥有阶段语义。

---

## ③ Abstraction Candidate

### 已重复 / 看似可抽象（Evidence）

| 现象 | 是否值得进入 Runtime | 判定 |
|------|----------------------|------|
| 六次「建 Runtime → submit → 看 success → 再下一步」 | **暂不** | 只发生 **1** 次交付；阶段集合是否稳定未知。本 Sprint **禁止** StageRunner |
| 各阶段都读 `RuntimeResult` 字段写报告 | **暂不** | 报告 schema 只服务本次 Review；进 Runtime = 过早固化叙述格式 |
| `FileEditWorker` vs `ShellWorker` 都产 Artifact | **已在 Runtime** | `ArtifactLifecycleService` + `WorkResultMapper` 已统一；无需再抽象 |
| `DeliveryPatches` 硬编码补丁 | **绝对不要进 Runtime** | 业务补丁；换需求即变 |
| Formatter 拼 REVIEW/DELIVERY markdown | **绝对不要进 Runtime** | Demo/Human 审计产物；非 Kernel |
| sample `TimeoutSource` / `ConfigService` | **绝对不要进 Runtime** | 业务域 |
| Shell allowlist / File path 安全 | **留在 Worker** | 执行面策略；ArchUnit 已禁 engine→workers |

### 真正「有重复迹象、但仍不够进 Runtime」的唯一候补

**固定串行多 submit 编排**（FPD【I】曾推荐）——**证据不足升格**：仅一条通路；下一真实需求可能跳过 Discovery、插入 Human-wait、或 Verification 不是 mvn。  
→ 本 Validation：**标记为 Future Candidate，本 Sprint 不抽象。**

---

## ④ Runtime Purity Check（只指出，不修改）

| 检查 | 结果 | 说明 |
|------|------|------|
| Demo Logic 进入 Runtime？ | **No** | 阶段顺序、REQUIREMENT、Patches、Formatter 均在 `ai4se-demo` |
| Worker Logic 进入 Runtime？ | **No**（方向正确） | ArchUnit：engine 不依赖 `workers`；`ProcessBuilder`/写文件在 Worker |
| Business Logic 进入 Runtime？ | **No** | 无 `app.timeout.ms` / ConfigService 知识 |
| 浅层「编排气味」？ | **可观测但未越界** | `Runtime` 构造时绑定 **单个** Worker；换工人靠外部新建实例——这是边界清晰，不是污染 |
| `workflowId` 字段名？ | **命名债，非逻辑** | `RuntimeRequest.workflowId` 仍存在，但 FPD **未**实现 Workflow；勿据此声称有 Workflow 能力 |

**Purity 结论：** 以 First Production Delivery 为准，**Runtime 仍纯净为单步 Orchestrator**。污染风险在 **Demo 变厚**（编排+计划正文+报告），不在 Runtime 内核。

---

## ⑤ Three Future Deliveries Prediction

假设连续再做 **三个真实需求**（仍禁止先抽象）：

| 类别 | 预测 | 依据 |
|------|------|------|
| **大概率会稳定** | 单步：`submit` → Task/Context/Trace → Worker → Artifact → Checkpoint → `RuntimeResult` | 六阶段 ×1 交付已 6 次重复同一内核路径，全部成功 |
| **大概率会稳定** | Worker SPI：`WorkResult` metrics → Artifact；engine 不依赖具体工人 | Shell 与 FileEdit 已两种实现共用同一结算 |
| **可能完全不同** | Discovery 是否存在、用何命令 | 本次仅 `git status`；下一项目可能空 Discovery 或搜代码 |
| **可能完全不同** | Plan 形态 | 本次预写 PLAN.md；下一可能是人审过的清单或无 Plan 文件 |
| **可能完全不同** | Execution / Verification 工具链 | 本次 FileEdit + mvn；下一可能是格式化、集成测、手工验收 |
| **还不能抽象** | 阶段列表 / StageRunner | 阶段名与跳过规则样本 n=1 |
| **还不能抽象** | Review/Delivery 报告 schema | 只服务 Architecture Review 一次格式 |
| **还不能抽象** | Learning / Evolution | 零运行时证据 |

---

## Architecture Risks

1. **Demo 成为隐式 Orchestrator** — 交付能力看似「Runtime 有了」，实则编排在 Demo；新人易误判水位。  
2. **FPD【I】与 Sprint-8.5 禁令张力** — 上次推荐 StageRunner，本 Sprint 禁止抽象；正确姿态是 **先用更多真实交付证伪阶段稳定性**。  
3. **`workflowId` 命名** — 易被外部 Review 误读为已有 Workflow。  
4. **报告双轨** — workspace 内 DELIVERY_REPORT vs `docs/first-production-delivery-report.md`；均非 Runtime 职责，但易被当成平台能力。

---

## Recommendation（二选一）

### **继续真实交付**

**不**允许现在抽象 StageRunner / Planning / Scheduler。

**理由（证据）：**  
边界已清晰；纯度合格；唯一重复候补（多阶段编排）**样本数 = 1**，三预测显示阶段内容「可能完全不同」。再抽象 = 把 Demo 偶发结构冻进 Runtime。

---

# ChatGPT Review Package

## 1. Runtime Boundary（Runtime 负责什么）

- 单次 `submit` 的 Task 生命周期推进  
- ExecutionContext 打开/冻结  
- Trace 记录  
- 调用一个 Worker  
- WorkResult → Artifact COMMITTED/ABANDONED  
- Checkpoint 只写  
- 输出可观测 `RuntimeResult`（含 checkpointId / durationMs / workerId / goalType）  
- 传递 Budget 超时  

## 2. Runtime Excluded（永远不负责什么）

- 业务 Requirement 定义与补丁内容  
- 多阶段顺序 / 成败门闸编排  
- Worker 选择与命令/文件参数  
- git/mvn/写文件等真实副作用  
- Review/Delivery 叙述文案生成  
- 业务验收断言（如 timeoutMs==5000）  
- Learning / Evolution / Knowledge / Scheduler / Workflow DSL / Resume  

## 3. Responsibility Matrix

| Item | Owner |
|------|--------|
| Requirement | Human (+ Demo 承载本次文本) |
| Planning | Demo content + Worker write |
| Discovery | Demo invoke + Worker execute |
| Execution | Worker |
| Verification | Worker |
| Review | Demo narrative + Worker write |
| Delivery | Demo aggregate + Runtime fields + Worker write |
| Learning | Future（今日仅文档） |
| Evolution | Future（禁止） |

## 4. Abstraction Candidates

- **可考虑、但现在不够：** 固定串行多 submit（StageRunner）— n=1，本 Sprint 禁止  
- **已在 Runtime、保持：** Artifact/Trace/Checkpoint 结算  
- **永不进 Runtime：** DeliveryPatches、Formatter、sample 业务类、Shell/File 策略细节  

## 5. Purity Check

- Demo / Worker / Business logic：**未进入** Runtime 执行路径  
- 风险在 Demo 变厚，不在 Kernel 污染  
- `workflowId` 为命名债，非已实现 Workflow  

## 6. Architecture Risks

- Demo 被误认为 Runtime 交付引擎  
- 过早 StageRunner 与「阶段可能完全不同」冲突  
- 外部 Review 误读 workflowId / 双轨报告为平台能力  

## 7. Recommendation

**继续真实交付**（不要现在抽象 StageRunner/Planning/Scheduler）。

证据阈值：Runtime 边界已验证；抽象候补样本不足。
