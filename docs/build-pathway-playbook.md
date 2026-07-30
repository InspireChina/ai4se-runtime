# 建造通路手册：蓝图怎么收 · 每一步怎么走 · 每一步怎么验

> **这份文档要解决什么：**  
> ChatGPT 给了「大而全蓝图」；我这边又写了「现状 / 对照 / 流水线」多份分析。你现在缺的是 **第三条路**：  
> **既不假大空拼接，也不被当下绑死** —— 用「北极星 + 台阶 + 门禁 + 决策树」把系统一步步建成。  
>
> **怎么用：** 当「项目操作系统」。每开一个 Sprint / 每让 AI 干活前，先打开本文对应台阶；做完跑门禁；用决策树决定下一步。  
> **不替代：** Frozen Invariants（宪法）。本文是 **施工操作系统**。

---

## 0. 先承认两边的毛病（避免再各走极端）

### 0.1 ChatGPT 蓝图常见病（你们仓库里也有）

| 病 | 表现 | 后果 |
|----|------|------|
| 分类学完备症 | Scheduler/Plugin/Capability/Workflow…一次列齐 | 无法排期，永远「还差一层」 |
| 开源拼图 | 像多个名项目并集 | 没有一条最小可证伪通路 |
| 文档即能力 | 写了 Rule/Skill 就算有治理 | ai-loop：规则很多但不强制 |
| 提示词当架构 | 下一步靠更长 prompt | 无 Artifact、无门禁、不可续作 |
| 双中枢叙事 | 又要 Scheduler 又要 Runtime 直推 | 实现与宪法打架 |

### 0.2 我（Cursor）侧文档可能的病

| 病 | 表现 | 后果 |
|----|------|------|
| 过度当下 | 「先别做 Scheduler/知识/进化」说太多次 | 好像永远到不了你要的流水线 |
| 文档增殖 | 现状、对照、流水线、学习报告… | 新人不知道以谁为准 |
| 校准有余、台阶不足 | 知道问题，缺「下周精确做什么」 | 仍要靠人临场发挥 |

### 0.3 第三条路（本文立场）

```text
L0 宪法     Frozen Invariants（少改，改必 ADR）
L1 北极星   你要的「可集成工程 Runtime + 串行交付流水线」（愿景，不排期细节）
L2 台阶图   本文：S0→S9 每阶可证明增量（唯一排期来源）
L3 当下冲刺 当前只允许做一个台阶上的一个切片
```

- **展望未来**：看 L1 + L2 全图（知道火车开往哪）。  
- **聚焦当下**：只允许打 L3 当前切片（知道今天钉哪颗钉子）。  
- **防假大空**：每阶必须有「最小通路证明」；证伪了才许升阶。  
- **防短视**：每阶结束问「是否仍指向 L1」；若偏离，先纠偏再加功能。

**现行文档只认这四类（其余进 `_archive`，不排期）：**

| 文档 | 角色 |
|------|------|
| `docs/architecture/*` + `docs/adr/*` | L0 宪法 |
| `serial-pipeline-design.md` | L1 流水线形状（目标，不排期） |
| **本文 `build-pathway-playbook.md`** | **L2/L3 操作系统（唯一排期/门禁）** |
| `project-status-plain-language.md` | 现状说明书（能做什么） |

不要再新增平行「真理」文档；新内容优先改本文或 status，不够再开 ADR。

---

## 1. 蓝图应该「如何化」——收成三层，而不是重画一张更大的

### 1.1 北极星（L1）一句话（钉死）

> **可集成的软件工程 Runtime：用 Task / Artifact / Worker / Trace / Checkpoint，把「摸底→澄清→计划→编码→测试→交付→知识回写」串成可门禁、可续作、可换项目/语言工人的流水线。**

不是：全能 Agent、自动进化、规则海洋自治。

### 1.2 蓝图对象怎么处理

| 蓝图对象 | 化法 |
|----------|------|
| Task / Artifact / Context / Worker / Trace | **留下，做硬骨头** |
| Checkpoint | **留下**：先写后恢复；恢复单独成阶 |
| Profile | **先落地为项目配置（YAML/JSON）**；「引擎」仅在满足升级解锁条件时才做（见 §1.3），**不承诺必升** |
| Knowledge / Repo Graph | **先落地为可检索 Artifact + 命中列表 / 可再生地图**；图引擎同样要解锁条件，**不承诺必升** |
| Rule / Skill | **少数可执行门禁** + 大量降级为可选提示 |
| Scheduler / WorkItem 队列 | **延后到「多步真实需要」**；v0 用 Runtime 串行阶段 |
| Plugin / Capability 平台 | **延后**；先手动组装 Worker |
| Workflow DSL | **用固定阶段模板代替**；DSL 很后 |
| 自动进化 | **移出主叙事** |

### 1.3 「先 YAML / 后引擎」会升吗？——诚实规则

> **默认假设：可能永不升级。**  
> YAML 若已够用，它就是产品，不是半成品。  
> 「后引擎」不是时间诺言，是 **痛点解锁**；说「以后自然会升」而不写解锁条件 = 另一种假大空。

| 配置形态 | 何时就够 | 何时才值得升级成「引擎」 |
|----------|----------|--------------------------|
| Profile YAML | 项目数少、字段稳定、人改配置可接受 | ≥N 个项目且配置错误频发；需要强校验/热加载/继承/多环境合并 |
| 知识 = Artifact 索引 | 条目少、检索靠路径/标签够用 | 有可测的召回失败，且证明结构查询/索引服务能降失败率 |
| 阶段模板（代码/表驱动） | 单流水线串行够用 | 多模板并存、频繁改阶段、非开发生改流程 |

**升级前必须有的证据（缺一不许开工「引擎」）：**

1. 可复现的痛点案例（不是「蓝图里有这个词」）  
2. YAML/现状方案明确失败的地方  
3. 引擎版的最小证明（比配置版多解决哪一条）  
4. 维护成本估算（谁养这个引擎）

**反模式：** 为了「以后好扩展」先写 ProfileEngine / KnowledgeEngine 空壳。

---

## 1.4 若直接按 ChatGPT 全景蓝图实现——难在哪？

不是单一原因，是三类叠加：

| 类型 | 含义 | 表现 |
|------|------|------|
| **A. 极难验证** | 什么叫「做完」说不清 | 模块在了、文档在了，但无人值守质量仍烂；无法证伪 |
| **B. 执行面过大** | 并行子系统太多 | Scheduler+Plugin+Capability+Workflow+Knowledge… 任一未完成都不能形成闭环 |
| **C. 过早抽象** | 在没有第二条用例前造平台 | 空 SPI、空引擎、假治理（skills 很多但不强制） |

**核心失败模式是 A：**  
进度用「文档完成度 / 模块数量」衡量，而不是「最小通路证明」。  
B/C 会加重 A。所以不是「难所以别做」，而是 **「先做成不可验证的大系统」几乎必惨**。

通路手册的对策：每阶只有一个可证伪证明；未解锁不许做引擎级东西。

---

## 1.5 门禁「自己给自己勾通过」——能过么？

> **不能。** AI 或作者自填 Yes/No **只算申报，不算通过。**

| 角色 | 可以做什么 | 不算什么 |
|------|------------|----------|
| 实现者 / AI | 提交门禁表 + 证据链接 | 最终裁决 |
| 自动化 | `mvn test`、Demo exit code、ArchUnit、脚本断言 | — |
| 第二人（或隔日的自己） | 按证据勾选、抽测 Demo | — |

**一阶算通过的最低证据（至少满足适用项）：**

1. 自动化命令退出码 0（测试/Demo/门禁脚本）  
2. 「最小证明」里写的场景有可复查记录（日志/报告 Artifact）  
3. 门禁表由 **非本阶主要作者** 勾选，或作者 **隔日按证据重勾**（禁止当日自拍通过）  
4. 若纯文档阶（如 S0 ADR）：合并前第二人读一遍「有没有宣称未实现能力」

本文里的「提示词要求先写门禁 Yes/No」= **防瞎做的自检申报**；  
**通过** = 申报 + 外部证据 +（人审或隔日复检）。

若只有「我写了通过」—— **视为未通过。**

---

## 1.6 可行方案（一页纸）——有，而且可执行

**方案名：闸门 Runtime（Gate-first Runtime）**

```text
已有：Task 生命周期 + Artifact + Trace + Checkpoint(写) + ShellWorker
补齐：能停人(S4) → 阶段门禁(S5) → 计划双产物(S6) → 项目测试门禁(S7)
      → 按需摸底(S8a) → 瘦知识(S8c) → 第二项目(S9)
不做：Scheduler/Plugin/Capability 平台/全景知识引擎/自动进化（除非解锁）
配置：Profile/阶段用 YAML 或代码表；引擎仅痛点解锁，默认可永不升
验证：自动化 exit 0 + 最小证明 + 第二人/隔日/新窗口复检（禁止自拍通过）
```

**为什么可行：**

1. 不依赖未建成的子系统就能形成闭环增量  
2. 每阶有证伪方式（测试/Demo/对照 diff）  
3. 直接对准你的痛点（假治理、摸底弱、质量、测试）  
4. 前瞻靠 L2 全图 + 解锁条件，不靠空壳模块  

**90 天建议节奏（可按人力压缩）：**

| 窗口 | 台阶 | 完成长相 |
|------|------|----------|
| 第 1–2 周 | S0 + S3 | ADR 落地；Result 能指出 checkpoint |
| 第 3–5 周 | S4 | 澄清/批准可阻塞 Task；无答案不能假装成功 |
| 第 6–7 周 | S5 | 缺 Artifact 阶段跳转失败（测出来） |
| 第 8–9 周 | S6 | 设计→测试方案串行；无 approve 不能编码 |
| 第 10–11 周 | S7 | 红测试必失败 Task；方案项未跑必失败 |
| 第 12 周 | S8a 试点 | 一次真实需求摸底证据包 + token 上限 |

未完成 S4/S5 前：**禁止**开 Claude 主路径编码工人、禁止对外说「可批量消化」。

---

## 1.7 手册还要怎么优化 / 细化 / 前瞻（本版已吸收）

| 方向 | 做法 |
|------|------|
| 细化 | §1.6 90 天表；S8→S8a/b/c（见 discovery 挑战文） |
| 前瞻 | S10+ 保留，但每项单独最小通路 + 解锁条件 |
| 可独立验证 | §11 **新窗口冷启动验收**（不依赖本聊天上下文） |
| 防假延后 | §1.3 默认永不升引擎 |
| 防自拍门禁 | §1.5 外证才算过 |

仍可继续加强（未做也可先跑）：`scripts/verify-stage.sh` 把门禁自动化——有余力再加。

**2026-07-30 起：** 不再继续「优化手册架构叙事」。只认 §2.1 DoD + §2.2 回退 + §2.3 Stop；其余进验证。

这不是否定 ChatGPT，是把「百科全书」收成「可建造的产品定义」。

---

## 2. 总通路图（从现在到「人少但可靠的批量消化」）

**水位（2026-07-30，验证通路前请以此为准）：**

| 台阶 | 状态 | 证据口径 |
|------|------|----------|
| S0 ADR | ✅ | `docs/adr/0016-engine-as-scheduler-v0.md` + `docs/current-support-status.md` |
| S1 | ✅ | Walking Skeleton / 假工人测试 |
| S2 | ✅ | ShellWorker + FileEditWorker；Production jar |
| S3 | ✅ **部分** | `RuntimeResult` 已有 checkpointId/duration/worker/goal；Trace sink 仍弱 |
| S4 | ✅ 最小 | `POLICY_EXCEPTION`→`BLOCKED_POLICY`；`submitClarifyAnswers`/`approvePlan`；见 `HumanWaitIntegrationTest` |
| S5 | ✅ 最小 | `StageGate`：PLAN 需 `discovery.hit-set`；缺则 Worker 不调用；见 `StageGateIntegrationTest` |
| S6 | ✅ 最小 | 串行 `PLAN_DESIGN`→`PLAN_TEST`；`EXECUTION` 需 `plan.approved`+`plan.test-strategy`；见 `PlanDualProductIntegrationTest` |
| S7 | ✅ 最小 | VERIFY 读 `plan.test-strategy`；skip/红测/缺 acceptance 引用 ⇒ FAILED；见 `PlanVerifyIntegrationTest` |
| S8a | ✅ 最小 | Engine Map+Search → `discovery.hit-set`；gaps→澄清题 BLOCKED；体积上限；见 `DiscoveryIntegrationTest` |
| S8b/c | ❌ | 未解锁 |
| S9 | ✅ 最小 | 两 Profile（java-maven / shell-script）同 Runtime；vision 四问；见 `SecondProjectIntegrationTest`（非外部生产多仓） |

```text
S0 契约对齐（ADR）                              ✅ ADR-0016
S1 最小 Runtime 闭环（假工人）                 ✅
S2 真副作用窄通路（Shell/FileEdit）             ✅
S3 结果可观测（checkpointId 等）               ✅ 部分（Result 字段齐；sink 可选）
S4 人工等待闸（Runtime 内停人）                 ✅ 最小（BLOCKED_POLICY + 人工 Artifact API）
S5 阶段门禁引擎（Engine 内强制）               ✅ 最小（StageGate；非 Demo 串行冒充）
S6 计划双产物                                  ✅ 最小（design→test 串行；无 approve 拒编码）
S7 Verify 接项目测试                           ✅ 最小（按 plan.test-strategy；skip/红测拒 SUCCEEDED）
S8a 按需摸底                                   ✅ 最小（Engine Map+Search→hit-set；非 Graph）
S8b/c 索引/瘦知识                              ❌ 不做直至解锁
S9 第二真实项目                                ✅ 最小（两 Profile 同 Runtime + 四问；非外部生产多仓）
S10+ Resume / 多语言 / 窄 AI Worker            未解锁
```

**旁路 ≠ 台阶完成（多轮验证后必读）：**

| 已有旁路 | 对应台阶 | 说明 |
|----------|----------|------|
| Production Input + jar | S2 增强 | 输入外置，仍非 Scheduler |
| Requirement Analysis → Bundle | **Delivery 前半段** | 不在 S0–S9 原编号内；验证通路应单列 |
| Engineering Bundle（促销 UNKNOWN） | Analysis 诚实性 | **假仓**；不能当 S8a/S9 完成 |
| First Production Delivery | S2+执行 | sample 补丁+mvn，非全流水线引擎 |
| Delivery/Analysis **合同文档** | 规范 | 有合同 ≠ Runtime 已实现门禁 |
| `DemoGateBundle` | Demo↔S5/S6/S7 | 串行 Demo 共享 Artifact 店并种子门禁 kinds；**不是** StageRunner |

**原则：**  
S4 最小人闸已进 Engine（`BLOCKED_POLICY`）；完整 UI / Policy 引擎仍不做。  
Demo Analysis Stop ≠ 取代 Engine 人闸。不要跳去 Scheduler/Plugin/Graph。

**续开规则（2026-07-30）：**  
文档关（如 S0）门禁自检通过 → **可直接关闭并开下一关，不默认打断人工**。  
仅当文档偷宣称未实现能力、或代码门禁红、或要改蓝图/扩禁谈项时 → 才请示人工。

---

## 2.1 交付通路完成标准（Definition of Done）——必须

> **与 S0–S9 台阶不同：** 台阶是「造 Runtime」；本节是「用一条真实需求把交付链跑通」。  
> **以后聊天只问：今天验证哪一步？** 不再讨论未解锁概念。

### 通路成功长什么样

必须**连续**完成下面七步，**中间不得人工跳步骤**：

```text
Requirement
  → Analysis
  → Clarification（如需要）
  → Planning
  → Execution
  → Verification
  → Delivery
```

| 步骤 | 通过条件（缺一不可） |
|------|----------------------|
| **Analysis** | UNKNOWN 全部进入 Clarification；Facts **不允许造事实** |
| **Clarification** | 关键题有**具体**回答；Gap 变为 **CLEAR** 或 **ASSUMABLE**。回答仍为 UNKNOWN → **保持 BLOCKED**，禁止 Planning |
| **Planning** | 基于 Context；**不允许**引用 Facts 直接写 Plan |
| **Execution** | 修改**真实**代码（非空 README 占位仓冒充） |
| **Verification** | 相关测试通过；新增能力有新增测试覆盖 |
| **Delivery** | 输出 Review + Delivery Report |

**整条通路判定：**

- 七步全部按序完成 → **PASS**
- 任一跳步、造事实、BLOCKED 进 Planning、假仓当真仓 → **FAIL**

**为什么必须有本节：**  
以后永远知道「在验证哪一步」；没有 DoD，手册只会永远停在概念讨论。

### 单步验证节奏（取代「再设计一轮」）

| 今天只验 | 证据 |
|----------|------|
| Analysis | Bundle：`requirement` + `facts` + `analysis-context`；无 plan/patch |
| Clarification | `clarification.md` 有答；Gap 重算后非 BLOCKED（或合法 STOP） |
| Planning | `plan.md` 只引用 Context；Gap 已 CLEAR/ASSUMABLE |
| Execution | 真仓 diff + Runtime/Worker 执行记录 |
| Verification | 测试命令 exit 0 + **`verification-matrix.md`（Acceptance→COVERED\|MISSING）** |
| Delivery | Review + Delivery Report |

整链第一次真正跑通 = 七步连续 PASS。

### 2.1.1 交付通路验证阶段（操作切片，非新架构）

> **不改：** §2.1 七步 DoD、§2.2 回退、§2.3 Stop、§2.4 禁谈清单、S0–S9。  
> **读法：** 日常只看「状态表 + Now 指针 + 改动准入」；已完成关的细节只点证据文件，不在此复述。

| Phase | 状态 | 证据（点开即止，勿在手册扩写） |
|-------|------|--------------------------------|
| Phase 1 Analysis Path | ✅ 关闭 | `pathway-verification-report.md` |
| Phase 2 Execution（fixture） | ✅ 关闭 | `execution-path-sprint-a/b/c-evidence.md` |
| Acceptance Provenance | ✅ 关闭（即停） | `acceptance-provenance-evidence.md` |
| 真仓连续 DoD（timeout/config） | ✅ 关闭 | `real-repo-continuous-evidence.md` |
| **第二形态连续 DoD（REST）** | ✅ 关闭 | `second-shape-continuous-evidence.md` |
| **全程回顾 + 蓝图对齐** | ✅ 关闭（Reviewer 终端复跑） | `pathway-retrospective-evidence.md` |
| **§2.2 局部回退（Loop）** | ✅ 关闭（Reviewer 终端复跑） | `loop-return-path-evidence.md` |
| **§2.2 再执行闭环** | ✅ 关闭（Reviewer 产物抽检 / 开下一关） | `loop-reexecution-evidence.md` |
| **§2.3 Stop Condition（旁路）** | ✅ 轻关闭 | `stop-condition-evidence.md`（计算器级；抽检意义低） |
| **§2.3 Stop 接入 Pipeline** | ✅ 关闭 | `stop-pipeline-wired-evidence.md`（真路径；Reviewer 可跳过抽检） |
| **S0 契约对齐（ADR-0016）** | ✅ 关闭（门禁自检通过） | `docs/adr/0016-engine-as-scheduler-v0.md` |
| **S4 Runtime 人闸** | ✅ 关闭（集成测试绿） | `HumanWaitIntegrationTest`；`Runtime.submitClarifyAnswers` / `approvePlan` |
| **S5 阶段门禁** | ✅ 关闭（集成测试绿） | `StageGate` + `StageGateIntegrationTest`（缺 discovery.hit-set 拒 PLAN） |
| **S6 计划双产物** | ✅ 关闭（集成测试绿） | `PlanDualProductIntegrationTest`（串行 design→test；无 approve 拒 EXECUTION） |
| **S7 Verify 接 plan** | ✅ 关闭（集成测试绿） | `PlanVerifyIntegrationTest`（skip 不调 Worker；红测 FAILED；缺 acceptance 引用 FAILED） |
| **S8a 按需摸底** | ✅ 关闭（集成测试绿） | `DiscoveryIntegrationTest`（hit-set 召回 diff；gaps→澄清题；payload 上限） |
| **S9 第二项目 Profile** | ✅ 关闭（集成测试绿） | `SecondProjectIntegrationTest`（两栈 Profile；四问；红测 FAILED） |
| **S0–S9 压测（不开新关）** | ✅ 关闭（Reviewer 复跑+负例抽检） | `s0-s9-pressure-test-evidence.md` |
| **换思路压测 B（非串行 DoD）** | ✅ 关闭（Reviewer 复跑 AltApproach） | `alt-approach-pressure-evidence.md` |
| **Post–StageGate 多维复核** | ✅ 关闭（Reviewer 抽检） | `post-stagegate-regression-evidence.md` |
| **下一关** | **不开新关**；**等待外部真实业务仓路径** | S8b/c·S10+ 仍锁定 |

**Evidence Delta：** 本轮只写以前没有的新证据；已证项不得再当主成绩。

#### 验证通路固定手段（每关必走 · 对接 §1.5）

> **目的：** 防「顺着自己思考检查自己」。Agent 只做申报；**关闭权在 Reviewer**。  
> **不新增 Framework 对象**；本手段是操作纪律，不是新 Gate 类型。  
> **防自证怎么验：** Reviewer 在**独立终端**（脱离本对话上下文）复跑证据命令看 exit code + 抽看产物。  
> 这就是「另一窗口」——用人脑+机器复跑对照，**不是**再开一个 AI 聊同一上下文互相确认。

| 角色 | 允许 | 禁止 |
|------|------|------|
| Agent（实现者） | 实现、复跑命令、写 Evidence Delta、填蓝图表、标 `AGENT_DECLARE: PASS\|FAIL` | 自行把状态表改成 ✅ 关闭；当日自拍通过 |
| 自动化 | 旧关 Main **子进程** exit 0、本关负例、手册水位断言 | 把叙事当证据 |
| Reviewer（你） | **独立终端**复跑 + 抽产物/负例；裁决关闭/打回；点名下一关 | 跳过抽检只听 Agent 口头 PASS |

**每关三块（缺一块 = 未完成申报）：**

1. **本关修复产出** — 新证据命令可复跑；至少 1 个负例或「不做会误判 PASS」对照。  
2. **已验证流程全程回顾** — **复跑**已关闭关的 Main/命令（exit code），不是复述旧结论。  
3. **蓝图对齐** — 对照 §1 北极星 + 水位表，三选一且必须有事实：  
   - `ALIGN`：未偷标台阶、未扩禁谈项；或  
   - `HOLD_SURFACE`：表面偏离但**有事实**说明为何不改蓝图（痛点未满足 §1.3 解锁）；或  
   - `DEVIATE`：改蓝图/水位，但须代入实际（可复现痛点 + 现状失败点 + 最小证明），**禁止**「给不出建议所以不改」也禁止「无实际依据就改」。

**Reviewer 操作方案（固定 · 防自证落地）：**

> **默认不整链复跑 Agent 刚跑过的同一 Main。** 默认做「产物抽检」；仅在下列情况才整链复跑。

| 何时 | 做什么 | 防的是什么 |
|------|--------|------------|
| **默认（每关）** | 打开证据 md + 1 份失败/成功产物 + 扫一眼 Not claimed / ALIGN | 空宣称、假绿叙事、断言题目设歪 |
| **整链复跑 Main** | 仅当：本关首次关闭前你从未跑过；或隔日/换机；或产物对不上证据；或你怀疑造假 | 不可复现、环境假绿 |
| **打回** | 任一红灯：产物无关键句、负例变绿、ALIGN 空话、偷标 S0/S4/S5 | — |

**默认 3 步（约 3–5 分钟）：**

1. 打开 `docs/<本关>-evidence.md` → 看 Verdict / Not claimed / 蓝图 ALIGN 是否有事实。  
2. 打开证据里指向的 **1 份失败产物**（如 `…/DELIVERY_REPORT.md`）→ 应含 FAIL + 本关关键句（如 `Recommended return: Execution`）。  
3. （可选）再看 **1 份成功产物** 或负例描述是否仍成立。  

通过 → 在对话回复「关闭本关」（可顺带点名下一关）。  
存疑 → 再跑 Now 指针里的 Main；仍存疑 → 打回并写明哪一块。

**整链复跑（仅上表触发时）** — 仓库根 `ai4se-runtime/`：

```bash
mvn -pl ai4se-demo -am -q compile
mvn -pl ai4se-demo -q exec:java -Ddemo.mainClass=<本关 Main>
```


**通路改动准入（防补丁成瘾 · 必问 · 答不出就不改通路）：**

1. **职责还是质量？** 下一层无法消费上一层产物 → 可讨论改通路；仅文案/漏检/防未来 → **Checklist 或测试**，不进 Framework。  
2. **会否新增消费者/对象？** 新 Gate/Matrix/Bundle 须证明「以前无人消费该产物」；否则拒绝。  
3. **去掉它通路还能跑吗？** 能跑 → 多半不是骨架，默认不进 Framework。  
4. **不做，真仓下一关会不会系统性误判 PASS？** 不会 → **不做**。

**永久原则（一句）：** 只有当问题导致下一层无法继续消费上一层产物时，才允许改通路；否则默认实现质量问题，不进入 Framework。消费者不得新增 Requirement / Acceptance / Verification ID。

**回顾纪律：** 全程回顾 = **复跑命令**；**禁止**把 Phase 1/2 再论证成「新成绩」。本关新问题与旧关复跑分栏书写。

---

## 2.2 失败后的返回路径（Loop 基础）——建议且应写死

> Verification FAIL **不要**默认回 Requirement。只回「出错的那一环」。

```text
Verification FAIL
  → 若实现错误（代码/测试未达 plan） → Execution → Verification
  → 若计划错（改对了码仍验不过 / 方案本身错） → Planning → …
  → 若执行中发现新 Unknown / 产品规则变了 → Clarification → Gap Re-check → …
  → 仅当需求本身被否决或换题 → Requirement（少见）
```

| 失败点 | 默认返回 |
|--------|----------|
| Verification | Execution（优先）；确认是 Plan 错才回 Planning |
| Planning | Clarification（若新 Unknown）或留在 Planning 改稿 |
| Clarification 仍 BLOCKED | 见 §2.3 Stop；**禁止**进 Planning |
| Analysis 造事实被抓 | 重做 Analysis（Facts 重写），不跳步「补 Plan」 |
| Execution 改错仓 | 停；回 Clarification 确认真仓 |

这是以后 Loop 的基础：**局部回退，不全盘重开。**

---

## 2.3 Stop Condition（停止条件）——建议且应写死

> 防止 Agent / 流水线无限追问。

| 条件 | 动作 |
|------|------|
| Clarification **超过 3 轮**（同一需求）仍无法 CLEAR/ASSUMABLE | **STOP** → Human Decision Required |
| **连续两轮**回答后 Gap 仍为 **BLOCKED** | **STOP** → Human Decision Required |
| 用户明确拒绝回答关键题 | **STOP**；不得 ASSUMABLE 偷渡 |
| 连续两轮仍在「占位仓 / 无模块」上转 | **STOP**；要求换真仓，禁止继续空转 |

STOP 后只允许：换人决策、换仓、缩 scope、或正式取消需求。  
**禁止：** 再问一轮假装推进、BLOCKED 写 Plan、臆造 Facts。

---

## 2.4 明确停止讨论的清单（即日起）

下列在**未出现可复现痛点证据前**禁止开题、禁止进 Sprint、禁止写设计文：

Graph · StageRunner · Scheduler · Resume · Knowledge Engine · AI Worker · Learning 平台 · Evolution

理由：以后可能需要；**现在没有证据**。Execution 还没在真仓连续跑通前，讨论它们 = 空转。

有漏洞只修**交付通路**（合同 / Demo 门禁 / 本手册 §2.1–2.3），不提前造未来能力。

---

## 3. 每一步的「标准作业」（像提示词，但带门禁）

> 下面每阶都按同一模板：目标 / 做 / 不做 / 最小证明 / 门禁检查 / 决策树。  
> 你可以直接复制「提示词块」发给 AI；但 **门禁由人或脚本执行**，AI 不能自评过关。  
> 详见 §1.5：**自填 Yes/No ≠ 通过。**

### 通用决策树（每阶结束必跑）

```text
跑门禁检查清单
  ├─ 失败：设计思想偏离？ ──是──► 改文档/ADR（改架构叙事），再回来
  │                      └─否──► 代码质量/测试红？ ──是──► 只改代码，禁止开新阶
  │                                              └─否──► 证明不足？补测试/Demo
  └─ 通过：仍指向 L1 北极星？ ──否──► 停，重评台阶
                           └─是──► 可开展下一阶（只开一阶）
```

四种下一动作（只能选一个）：

1. **改架构（叙事/ADR/边界）**  
2. **改代码（实现/测试）**  
3. **继续检查（补证明、补门禁）**  
4. **开展下一阶计划**

---

## 4. 台阶详表（可执行）

### S0 — 契约对齐（强烈建议先做，几乎纯文档）

**目标：** 消掉「Scheduler vs Runtime」裂缝；标明状态机支持子集。

**做：** 一篇短 ADR：`Engine-as-Scheduler-v0` + Supported Task path。  
**不做：** 实现 Scheduler 模块。

**最小证明：** ADR 合并进 `docs/adr/`；README 链到「当前支持状态」。  
**门禁：**

- [ ] 是否宣称实现了未实现的 Scheduler？必须否  
- [ ] Checkpoint 是否写明「可写未 Resume」？必须是  
- [ ] 是否仍指向「工程 Runtime 非 Agent」？必须是  

**提示词块：**

```text
基于 docs/architecture/runtime-invariants.md 与当前实现，
写一篇 ADR（不改 Frozen 正文）：
1) v0 由 Runtime Engine 推进 Task 状态机，Scheduler Deferred
2) 支持状态子集列表
3) Checkpoint 语义：write-only until Resume milestone
禁止引入新 Domain 对象。输出放到 docs/adr/。
写完后对照门禁自检表列 Yes/No。
```

**决策：** 过门禁 → S3 或 S4；不过 → 只改 ADR。

---

### S3 — 结果可观测（小代码，防短视又防空谈）

**目标：** Demo/流水线能稳定指出「这次存档点是谁」；Trace 不靠糊日志碰运气。

**做：** `RuntimeResult` 增加可选 `checkpointId`；Trace 可注入 sink（默认仍可打印）。  
**不做：** Resume、DB、新 Domain。

**最小证明：** 单测断言成功路径 Result 含 checkpoint；Demo 打印该字段。  
**门禁：**

- [ ] Kernel 是否被污染？必须否  
- [ ] 是否仍 Worker SPI 无关？必须是  
- [ ] `mvn test` 绿？  

**决策：** 过 → S4；红 → 只改代码。

---

### S4 — 人工等待闸（通往你真实流水线的第一跳）

**目标：** Task 能「停下来等人」，人答完才能继续——对应澄清/计划批准。

**做（最小）：**

- Task 状态使用已有 `BLOCKED_POLICY` **或** 引擎级 `WaitingHuman` 标记（优先用已有状态，避免新枚举争论）  
- API：`submitClarifyAnswers(taskId, artifact)` / `approvePlan(taskId)`  
- 答案/批准必须是 Artifact  

**不做：** UI 产品化、完整 Policy 引擎、LLM 自动答题。

**最小证明：** 集成测试：submit → 进入等待 → 无答案不可编码 → 提交答案 → 可进入下一阶段桩。  
**门禁：**

- [ ] 人没答也能「假装成功」？必须否  
- [ ] 答案是否落 Artifact？必须是  
- [ ] 是否发明了绕过 Task 的旁路？必须否  

**提示词块：**

```text
在不新增 Domain 对象、不实现 Scheduler 的前提下，
为 Runtime 增加「等待人工」最小通路：
澄清问卷 Artifact → Task 阻塞 → 人工答案 Artifact → 解除阻塞。
先写失败测试，再实现。对照 serial-pipeline-design.md 阶段 B。
禁止用 prompt 自觉代替状态阻塞。
```

**这是防 ai-loop「不问人就开干」的结构性补丁。**

---

### S5 — 阶段门禁引擎

**目标：** 没有合格 Artifact，下一阶段 Worker 直接拒绝。

**做：** `StageGate`（引擎支持类即可，非 Kernel 新对象）：阶段 → 必需 Artifact kind 列表。  
**不做：** Workflow DSL。

**最小证明：** 缺 `discovery.hit-set` 时 Plan Worker 调用被拒；测试覆盖。  
**门禁：**

- [ ] 门禁是否在 Engine，而不在模型自觉？必须是  
- [ ] Worker 能否关闭门禁？必须否  

---

### S6 — 计划双产物（施工图 + 测试方案）

**目标：** 同阶段串行两次生成；测试方案可挑战设计。

**做：** PlanDesignWorker（可先假/模板）→ PlanTestWorker（读设计产出测试方案）；人工 `plan.approved`。  
**不做：** 子 Agent 并行；一次超长上下文写万物。

**最小证明：** 产出 Artifact 清单齐全；无 approve 不能进编码阶段。  
**门禁：**

- [ ] 是否同阶段串行双调用？必须是  
- [ ] 编码能否改测试方案？必须否  

---

### S7 — Verify 接项目测试

**目标：** 按 `plan.test-strategy` 跑 Profile 里的真实命令；失败不可伪装。

**做：** TestWorker 调 Profile 命令；acceptance ID 必须在报告中引用。  
**不做：** 自研万能测试框架。

**最小证明：** 故意写红测试 → Task 失败；跳过测试检测失败。  
**门禁：**

- [x] exitCode≠0 能否 SUCCEEDED？必须否 — `PlanVerifyIntegrationTest.redCommand_exitFail_taskFailed`  
- [x] 「方案写了但没跑」能否过？必须否 — `skipCommand_failsWithoutWorker_andCannotPretendSuccess`  

---

### S8 — 摸底 + 知识（已被挑战细化，勿按旧「一锅炖」执行）

> **修正说明：** 原 S8 过粗。历史长论证见 [`_archive/conversation-2026-07/discovery-knowledge-challenge.md`](./_archive/conversation-2026-07/discovery-knowledge-challenge.md)（归档，不排期）。  
> 拆成：

```text
S8a 按需摸底（地图+检索+TopK+gaps）—— 无知识库也能跑
S8b L1 活事实可再生索引（目录/依赖，可重跑）
S8c L2 决策遗产极瘦库（ADR/do-not/认证 lesson，有配额）
```

**S8a 目标：** 为单次需求生成可审计证据包并省 token（学 Repo Map / search-first）。  
**S8a 不做：** 全仓向量、企业知识中台、手维护完美图谱。  
**S8a 最小证明：** hit-set 召回可对照最终 diff；gaps 能流入澄清题；输入体积有上限。  
**S8a 证明（Engine）：** `DiscoveryIntegrationTest`；`Runtime` DISCOVERY 走 `MapSearchDiscovery`（不调 Worker）。  
**S8c 硬规则：** 无 `related-paths` 不得高信任检索；条目有配额；90 天无引用可删。

**门禁（思想）：**

- [x] 是否用「知识条数」冒充能力？必须否 — Map+Search，无知识库计数  
- [ ] L1 能否再生？必须是 — **S8b 未解锁**  
- [x] 是否仍指向「摸底≠懂业务，澄清才补齐」？必须是 — gaps → `clarification.questionnaire` + BLOCKED_POLICY  

---

### S9 — 第二个项目接入

**目标：** 证明「可适配」不是口号。

**做：** 另一技术栈或另一仓库 Profile；复用同一 Runtime。  
**最小证明：** 两周验收四问（见 vision 文档）全是。  

**门禁（四问 · 已证）：**

- [x] 能否提交 Task 并看到 Trace？ — `SecondProjectIntegrationTest`  
- [x] 能否跑项目自己的测试门禁并挡住烂代码？ — 各 Profile 红测 ⇒ FAILED  
- [x] 能否留下 Artifact 供下次续作？ — `verify.report` COMMITTED  
- [x] 前后端/阶段有没有共享契约？ — `plan.test-strategy` acceptance ID 引用  

**不做宣称：** 外部生产多仓、第三方真实客户仓、ProfileEngine/Plugin SPI。

---

## 5. 「展望未来」怎么写才不空——路线图只允许「已解锁」

```text
未解锁条件（例子）：
- 未完成 S5：禁止讨论 Workflow DSL
- 未完成 S7：禁止宣传「质量有保障的无人值守」
- 未完成 S4：禁止上 Claude 编码工人主路径
- 未完成 S8 认证知识：禁止谈「知识库驱动少走弯路」为已能力
- 未完成人工闸稳定：禁止「批量消化」对外承诺
```

这叫 **可信展望**：未来在图上，但被门卡住，不能跳。

---

## 6. 每次让 AI 干活的「总提示词壳」（防偏离 + 防糊弄）

复制下面壳，填入「当前台阶 Sx」：

```text
你是 AI4SE Runtime 的实现助手。必须遵守：

【北极星 L1】
可集成工程 Runtime：Task/Artifact/Worker/Trace/Checkpoint 串起可门禁流水线。
不是 Agent 玩具，不是自动进化，不堆 skills 假治理。

【当前台阶】Sx：<目标一句话>
【允许】仅本阶「做」列表内事项
【禁止】本阶「不做」列表；禁止新增 Domain；禁止 Scheduler/Plugin/Capability 平台

【设计思想检查（输出必须先写）】
1. 是否扩大了 Kernel？
2. 是否用提示词代替门禁？
3. 是否让 Worker 能把自己判成功？
4. 是否指向串行流水线而非旁路脚本？

【质量要求】
- 先补/改测试再改实现（若适用）
- mvn test 必须可运行通过
- 变更面最小

【结束时强制输出】
A. 做了什么
B. 门禁清单 Yes/No
C. 决策树选择：改架构 / 改代码 / 继续检查 / 可开下一阶
D. 若选下一阶：下一阶是什么、为什么解锁
```

---

## 7. 最小通路验证节奏（双周循环，防空转）

| 日 | 动作 |
|----|------|
| D1 | 选中唯一台阶；写「本阶完成长什么样」 |
| D2–D3 | 实现 + 测试 |
| D4 | 跑门禁清单；跑 Demo |
| D5 | 决策树；更新本文「当前台阶」指针；写 10 行 Learning |
| 禁止 | 同周开两个台阶；同周「先重构架构再加功能」打包 |

**当前推荐指针（验证通路用 · 2026-07-30）：**

> **Now = S0–S9 最小已关；压测 PENDING 已关闭；不开新关。**  
> **下一刀（唯一）：** 客户云桌面 **只读** 真仓压测（零提交 / 零外泄）。提示词：`docs/external-readonly-pressure-prompt.md`。  
> **仍不痛 → 继续停**；有可复现痛点再按失败形态解锁（勿默认 Graph/Claude/Resume）。  
> **禁止：** Graph、知识中台、Claude、Scheduler、Workflow DSL；**禁止**客户仓 commit/push。

---

## 8. 会不会「过于当下」？本文如何平衡

| 机制 | 作用 |
|------|------|
| L1 北极星每阶必勾 | 防止短视做成「只有 Shell 的玩具」 |
| L2 全阶可见 | 你知道澄清/知识/多项目在后面哪 |
| 解锁条件 | 防止空谈未来能力 |
| 串行流水线文档 | 未来形状已定，只是未全实现 |
| 禁止跳阶 | 防止 ChatGPT 式「先把插件体系盖了」 |

所以：**当下钉钉子，墙上挂着造桥图；没钉完桥墩不许铺桥面。**

---

## 9. 对你现有文档的优化建议（执行层面）

1. **README 顶部**只留三链：现状大白话 · **建造通路（本文）** · 串行流水线。  
2. Calibration / Learning / Vision 降为附录，避免平行「多个真理」。  
3. Frozen 不动；差异用 ADR（S0）。  
4. `_archive` 继续只读。  
5. 每完成一阶，在本文加一行「完成证据：commit / 测试名 / Demo 命令」。

---

## 10. 一句话通路

> **把 ChatGPT 蓝图收成北极星与延后清单；  
> 把已写的分析收成现状与流水线形状；  
> 用本文台阶 + 门禁 + 决策树当唯一施工操作系统；  
> 现在补齐「能停人、能挡关」，再接到摸底/计划/测试/知识；  
> 每步可证明，才许展望下一步——这样既不假大空，也不困在当下。**

---

## 11. 新窗口冷启动验收（不依赖本聊天上下文）

> **可以、而且应该。** 另开 Cursor 窗口 / 换模型 / 隔日自己，只读仓库做检测——这才是「外证」。  
> 提示词就在本节；**不要**再拆成单独文件。

### 11.1 复制即用提示词

```text
你是 AI4SE Runtime 的独立验收官，不是实现助手。

【硬规则】
1. 禁止使用「上一段实现聊天」的记忆当证据；只认本仓库文件与用户粘贴的命令输出。
2. 结论只能是 PASS / FAIL / INCONCLUSIVE（缺证据），每项给证据路径或命令。
3. 本模式默认不改代码；发现失败只报告「该改架构/改代码/补证明」哪一类。
4. AI 或作者自填门禁 Yes/No 不算通过；必须有测试/Demo/文档对照。
5. 「先 YAML 后引擎」：无解锁痛点却存在空引擎 = FAIL 倾向。

【必读（按序）】
- docs/build-pathway-playbook.md（§2 水位、§7 Now、§11）
- docs/engineering-delivery-contract.md（交付 SUCCESS）
- docs/requirement-analysis-contract.md（Analysis 前半段）
- README.md

【请用户先跑并粘贴输出】
cd <repo>
mvn -q clean test
# Execution 侧（任选其一有输出即可）
mvn -pl ai4se-demo -q exec:java -Ddemo.mainClass=com.ai4se.runtime.demo.ShellWorkerMain
# Analysis 侧
mvn -pl ai4se-demo -q exec:java -Ddemo.mainClass=com.ai4se.runtime.demo.analysis.EngineeringValidationMain

【验收清单】
A. Runtime 水位
- S1 假工人闭环是否有测试证据？
- S2 Shell/FileEdit 真副作用是否有 Demo/测试？
- S3 RuntimeResult 是否含 checkpointId？（有则 S3 部分 PASS）
- S0 ADR 是否存在？无则标 INCOMPLETE（不因此否决整个通路，但须写入报告）
- S4 Runtime Human-Wait 产品路径是否存在？无则 S4=未完成（合同层有不算完成）

B. Delivery / Analysis 通路（验证重点）
- 能否 NL → Bundle（requirement/context/gap/clarification/plan/verify/profile）？
- Gap 对未知是否标 UNKNOWN（禁止假装已有表/服务）？
- Bundle 是否含 patches/？Analysis 阶段不应有业务补丁
- jar/Production Input 能否在**已有补丁**下跑 Execution？（与 Analysis 分开验）

C. 设计思想
- Kernel 是否仍不依赖 workers 实现？
- 是否出现未使用的 *Engine 空壳 / Graph？
- 是否把 Demo 串行误标为 StageRunner/Workflow 已完成？有则文档 FAIL

D. 通路可行性
- §2 是否区分「旁路 vs 台阶」？
- §2.1 DoD 七步是否写清？§2.2 回退、§2.3 Stop 是否存在？
- 门禁是否禁止自拍通过？
- 是否唯一 Now 指针且与 §2 / §2.1 一致？

【输出格式】
## 总评：PASS | FAIL | INCONCLUSIVE
## 当前在验证哪一步（§2.1）
## 分项表（项 / 结果 / 证据）
## 最大三个问题
## 建议下一动作（只选一个）：留在本步补证据 / 回退到 §2.2 指定步 / STOP(§2.3) / 可进下一步 / 修通路漏洞（不设计新平台）
## 我是否可能偏袒实现者？自陈风险
```

### 11.2 用法

1. 实现聊天**不要**当最终裁判。  
2. 新开窗口粘贴 §11.1。  
3. 把 `mvn test` / Demo 输出贴给验收官。  
4. 只有验收官给 **PASS** 且你抽查证据，才算该水位通过。

---

## 文档信息

| 项 | 值 |
|----|----|
| 文件 | `docs/build-pathway-playbook.md` |
| 当前建议 Now | **压测已关；等待外部真实业务仓**（不开 S8b/c·S10+） |
| 通路 DoD / 回退 / Stop | §2.1 · §2.2 · §2.3 |
| 明确不做 | §2.4 |
| 冷启动验收 | §11 |
| 现行同伴 | Delivery/Analysis 合同 · `architecture/*` · README |
