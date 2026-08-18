# AI4SE 可用化改造手册 v1

> **用途：Codex 执行手册 / Release 1.0 改造基线。** 本手册回答三件事：工程与模型怎样协作；AI4SE 自身怎样避免漂移和无尽加固；如何用真实证据判断一种 Context/流程设计是否更好。执行者应逐 PR 实现，不得把本文再次扩写成新的概念蓝图。
>
> 产品目标：[客户仓交付操作模型](../00-product/customer-repo-delivery-operating-model.md) · Context 细则：[阶段上下文执行手册](../20-context-engineering/context-package-execution-handbook.md) · 现有能力边界：[Capability Map](../00-product/capability-map.md)

---

## 0. 最终要做成什么

AI4SE 不是一个 Agent，也不是一个模型。它是客户仓旁的软件交付控制系统：

```text
人给需求/附件/业务决定
  → AI4SE 把它们持久化为规格和状态
  → AI4SE 为当前阶段编译最小工作包
  → 可替换模型分析/计划/编码/评审
  → AI4SE 用范围、测试、独立 AC 和状态机判断下一步
  → 本地 commit，等人验收
```

Release 1.0 的用户路径只有一条：

```text
onboard 客户仓
  → run --interactive 一张 Story
  → 有歧义：显示问题并停止
  → resume --answers：模型处理答案并重新分析
  → 显示 Plan/Test/范围，等待批准
  → resume --approve-plan
  → 无人值守 Dev → Verify → Defect 修复（≤3）→ Review → local commit
  → 人验收
```

首版不做多 Agent、通用 Graph Engine、自动 push、自动发布、知识自动晋升和并行开发。三张串行真实卡稳定后，再决定多卡调度。

---

## 1. 工程项目与模型之间，好的交互是什么

### 1.1 模型不是工作流，也不是数据库

模型擅长：理解局部语义、对比代码模式、提出方案、写代码、根据明确失败修复、做语义 Review。

模型不擅长：长期可靠记住所有决定、自己维护不可变状态、判断自己是否真的完成、在超长混杂上下文中稳定选择最高优先级、为自己的输出提供独立证明。

所以交互协议必须把“稳定控制”和“灵活推理”分开：

| 层 | 谁负责 | 内容 |
|---|---|---|
| Workflow state | Java 控制面 | 当前阶段、合法转换、暂停、恢复、预算、终态 |
| Control input | Context Builder | 精确 AC、批准范围、工程硬约束、当前任务 |
| Working evidence | 客户仓 + 受控读取工具 | 相关代码、测试、调用链、附件、最小失败证据 |
| Reasoning/execution | 模型 | 分析、方案、实现、修复、Review |
| Truth/acceptance | Verification + 人 | 命令结果、独立 probe、业务接收 |

模型每次只接受一份明确的 Work Order：

```text
Role Contract（这个角色能做什么、输出什么）
+ Control Card（目标、精确 AC、冻结决定、范围、命中规则）
+ Task Card（本轮唯一任务）
+ Initial Evidence（目标代码/测试/范例/当前缺陷）
+ Read Scope（允许继续只读探索的模块）
+ Output Contract（必须落哪些结构化制品）
```

这比“把所有资料都塞给模型”好，也比“只给一句需求让模型自由探索”好：前者会稀释注意力，后者会让范围和风格漂移。

### 1.2 Context Package 是权威起点，不是信息监狱

Context Builder 要提供精准起点；Coding Agent 仍应被允许在客户仓中做**受控、只读、即时检索**，例如读取目标接口、直接调用者、邻近测试和同模块范例。否则 Builder 一旦漏选一个依赖，模型只能凭缺失信息写错代码。

规则如下：

- Package 内的 Control Card 是权威，不得被工具读取到的散文覆盖；
- `read_scope` 可覆盖目标模块、直接依赖和测试，比 `write_scope` 宽；
- `write_scope` 仍由 Runtime 强制，模型发现确实需要扩大时必须停止并提出 scope change，不可自行修改；
- 模型工具读取属于本次 Adapter turn 的工作证据，不把整仓预先 dump 进 prompt；
- 禁止读取凭据、`.git` 内部、生产数据和明确 forbidden 路径。

这也修正“执行器只能吃 Package”的过度字面理解：**Package 决定任务、边界和初始材料；受控工具用于按需取证。**

### 1.3 高质量提示不是越长越好

好的模型输入应满足：

1. 目标和输出明确；
2. 解释关键约束为什么存在；
3. 给少量客户仓内正确范例；
4. 机械规则交给 formatter/lint/test，不让模型背诵；
5. 语义规则、领域不变量和禁止范围放在不可裁剪 Control Card；
6. 日志只给根因、期望/实际、关键栈和复现命令；
7. 不要求模型输出隐藏思维链，只要求可审计的依据、选择、风险和结果。

Anthropic 的官方提示指南也强调明确指令、说明上下文/动机，并确保示例与期望行为一致；这支持“短控制卡 + 正确范例”，不支持无限叠加提示。[Anthropic prompt engineering](https://docs.anthropic.com/zh-CN/docs/build-with-claude/prompt-engineering/claude-4-best-practices)

### 1.4 `interrupt/resume` 借语义，不必引入图框架

成熟实现的共同点是：暂停时持久化状态；把可序列化的问题交给外部；恢复时把人的输入作为后续计算的正式输入；暂停前副作用必须幂等。LangGraph 的官方文档明确描述了 checkpoint、可序列化 interrupt payload、resume value 和节点重放语义。[LangGraph interrupts](https://docs.langchain.com/oss/python/langgraph/interrupts)

AI4SE 已有 Java 状态机和 ledger，因此 Release 1.0 只实现等价 Contract：`ClarificationRequest → Answer → Resolution → Gap re-check`。不引入 LangGraph 运行时，也不恢复同一个模型会话。

---

## 2. 什么样的输出文件真正帮助模型和工程

保留现有宿主：客户级材料在 `.ai4se/`；每卡过程在 `.story/<id>/`。禁止为了目录美观迁移为 `.ai4se/stories/`。

### 2.1 客户仓长期材料

| 路径 | 内容 | 质量标准 |
|---|---|---|
| `.ai4se/repository/entries.yaml` | 已实际验证的 build/test/start 入口 | 每条命令记录最近验证结果；不能是假默认值 |
| `.ai4se/repository/baseline.md` | 技术栈、模块、环境、风险事实 | 有来源，不写改造建议 |
| `.ai4se/repository/module-map.md` | 模块、职责、主要依赖、入口 | 路径可点查；Unknown 明示 |
| `.ai4se/knowledge/business-context.md` | 术语、角色、核心规则 | 人确认或有证据引用 |
| `.ai4se/knowledge/architecture-context.md` | 当前分层、依赖方向、主要模式 | 描述现状，不强行套 DDD |
| `.ai4se/rules/*.md` | 阻断级工程/业务规则与正确范例 | 有角色、路径、严重级别和 check/review check |

### 2.2 每张 Story 的最小必需制品

| 阶段 | 必须文件 | 条件文件 |
|---|---|---|
| Input | `requirement.md`、元数据、附件 manifest | 附件快照 |
| Analysis | `discovery.report.md`、`gap.report.properties` | `clarification.request.properties` |
| Clarification | 人的 `answer.properties`、模型的 `resolution.md/properties` | 多轮问题 |
| Planning | `plan.md`、`change-map.properties`、`test-strategy.md`、`effective-constraints.md/properties`、批准记录 | API/Data/Migration 文档 |
| Development | 每轮 Package manifest、changed files、实现摘要 | 无；不写“测试通过”自评 |
| Verification | AC matrix、命令报告、probe 结果 | Defect Package |
| Review | 结构化 decision、风险和规则符合性 | 架构/安全专项 Review |
| Delivery | commit、AC 完成率、验证、限制、回滚、人工验收项 | release notes |

Markdown 负责给人和模型阅读；`.properties` 负责 Java 8 下稳定解析与状态门禁；脚本/JUnit 负责执行。首版不为文件格式引入 YAML/JSON 依赖，也不创建重复叙述文件。

### 2.3 API、数据库、前端和 DDD 文档何时生成

- API 行为变化才生成 `api-contract.md`：请求、响应、错误、鉴权、兼容性；
- 表、索引、数据语义变化才生成 `data-change.md`：迁移、回滚、数据验证；
- UI 变化在 Plan/Test Strategy 中区分视觉、交互、接口和权限；
- 客户仓已有 DDD 时遵守其聚合和依赖边界；没有 DDD 时不借 Story 强行重构；
- “无影响”用 Plan 中一行结构化声明表达，不创建空文档。

---

## 3. 当前实现审阅：保留什么，修什么

### 3.1 已经正确，禁止推翻

- Java Workflow/Control 持久化状态；
- RunLedger 和 resume 边界；
- Cursor/Codex/Claude Adapter 抽象；
- write scope 和本地 commit、不 push；
- 有界 Dev↔Verify、无进展熔断；
- Acceptance probe、PASS-only Review/Delivery；
- 生产 preflight、干净工作树和 evidence。

这些是 AI4SE 区别于“模型即编排器”的价值，不为交互流畅而退化。

### 3.2 当前 P0 差距

| 差距 | 代码事实 | 影响 |
|---|---|---|
| Clarification 是假闭环 | `PathwayRunner` 在发现 resolved 文件后直接把 Gap 写为 CLEAR | 人回答未被模型正式处理，可能带着误解进入 Plan |
| CLI 没有真实交互入口 | `Ai4seMain` 只有 run/status/resume/scorecard，resume 无 answers/approval 参数 | 用户必须手工造文件，体验不是产品 |
| Adapter 没有明确收到 P1 正文 | `ContextPackagePrompt` 主要提交 manifest 和角色说明 | 是否读取所有 slice 依赖模型临场行为，跨 Adapter 不稳定 |
| Planning 输入不完整 | `PlanningPackageBuilder` 只有 requirement/AC/discovery/allowed hint | 缺 Gap、澄清、规则、知识、仓库入口和相关模式 |
| Development 上下文粗糙 | `DevPackageBuilder` 复制全 Plan/全适用角色规则，Defect 主要是路径指针，没有局部代码/测试切片 | 可能过大，也可能缺真正修复证据 |
| Package 预算没有生产约束 | `PackageBudget.UNLIMITED` 是默认，预算按 bytes 且只局部用于 Rule | 无法比较 Context 效率，也无法防日志/规则膨胀 |
| Rule 选择过粗 | `CustomerRuleLoader` 只按 role，未按目标路径/模块/严重级别 | 规则过多或漏掉真正适用约束 |
| 附件只形成索引 | Analysis Package 未验证模型能力或引用附件内容 | 有图片不等于模型读图 |

### 3.3 当前文档需要收敛的地方

- 目标文档中曾把 Story 收到 `.ai4se/stories/`，现已修正为保留 `.story/<id>`；
- 初始 18k/24k token 只是实验起始值，不是产品标准；Release 1.0 先做分区 bytes/字符审计，只有 Adapter 能可靠报告 tokens 时才记 token；
- 不一次创建所有理想制品；先实现第 2.2 节的最小必需文件，API/Data 等按影响生成；
- `Effective Constraint Bundle` 首版应由现有 Markdown Rule 编译，不先引入新配置框架；
- 不要求 Builder 预判并装入所有代码。提供 Control Card + 初始切片 + 受控 read scope，允许 Coding Agent 即时调查。

---

## 4. 如何判断一种“给模型的方式”是真的更好

不能因为 Prompt 更长、文档更多、单元测试更多就宣布更好。必须在固定 Story/基线/AC/probe 上比较。

### 4.1 六个质量维度

| 维度 | 指标 |
|---|---|
| 需求忠实 | `PROVEN AC / 总 AC`；不得以入口测试代替 |
| 修改精准 | 越权文件数、无关 diff、scope 扩展次数 |
| 工程健康 | 必跑规则通过率、Review 阻断、复杂度/重复/分层违规、人工代码缺陷 |
| 修复稳定 | 修复当前 Defect 后旧 AC 回归数、重复失败指纹、开发轮次 |
| Context 效率 | 包总 bytes/tokens、阶段耗时、每个 PROVEN AC 的输入成本 |
| 可恢复/一致 | 重启或换 Adapter 后状态、规则 SHA、AC 和范围是否一致 |

### 4.2 三类评估，不混为一谈

1. **Contract tests**：验证 P1 存在、SHA/范围一致、状态合法、不可修改 probe；快且确定。
2. **Scenario tests（假 Adapter）**：覆盖 CLEAR、BLOCKED→回答→CLEAR、Defect→修复、恢复和熔断；验证编排，不评估代码质量。
3. **真实模型评估**：固定 2–3 张客户卡、相同 baseline/adapter/model/probe，比较 Package v1/v2 的完成率、缺陷、耗时和 Context 大小。

OpenAI 的 Evals API也把“数据集 + 测试标准/graders + 多次运行”作为评估基本形态；AI4SE 不必依赖该服务，但应采用同一思想：先固定样本和判据，再比较流程/模型配置。[OpenAI Evals](https://platform.openai.com/docs/api-reference/evals)

### 4.3 判断结论

- AC 更高但越权/技术债更高：不能晋升；
- Context 更短但失败率更高：不能晋升；
- 单卡更好：只标“候选”；
- 至少两张不同卡同方向改善且无安全回归：可成为默认；
- 三张卡重复暴露同类问题：才新增通用规则/能力；
- 只有 Prompt wording 变化而无真实指标变化：不继续微调。

---

## 5. AI4SE 自身如何不漂移、不陷入审阅死循环

### 5.1 只维护一个产品验收故事

整改期间固定一张 Release Story：

> 在一个已 onboard 的真实 Java 8 客户仓中，用户提交一张包含 3–5 条 AC 和一个真实歧义的需求；系统提出具体问题；用户回答并批准计划；系统在 ≤3 个 Development round 内完成代码、独立验证、Review 和本地 commit；用户无需手工编辑 `.story` 文件。

所有 PR 都要说明它推进了这条路径的哪一步。不能说明的功能进入 backlog。

### 5.2 WIP 和 PR 纪律

- 同时只允许一个主链 PR；
- 每个 PR 只解决一个可观察失败类型；
- PR 必须有“原始事实 → 根因 → 最小改动 → Contract/Scenario test → 对主链影响”；
- 不因一次 P2 不美观阻塞下一 PR；
- 不在一个 PR 中改目录体系、模块体系、状态机和文件协议四件事；
- 连续两个 PR 都只有门禁/测试、没有让 Release Story 前进一步时，第三个 PR 必须是集成运行或删减工作；
- 新抽象必须替代现有重复，而不是与旧路径并存；没有两个真实用例不新增通用 Engine。

### 5.3 失败分类决定是否改 Runtime

| 类型 | 例子 | 动作 |
|---|---|---|
| Product P1 | 错误交付、范围外写入、假 CLEAR、验证被篡改 | 暂停主链，最小修复 + 回归 |
| Context/Contract | 模型没看到澄清/规则/Defect | 修 Builder/Package，不先换模型 |
| Model capability | 已提供正确材料仍无法完成 | 记录评估；尝试已注册 Adapter/模型，不扩状态机 |
| Environment/Quota | CLI 配额、DB、网络、JDK | 诚实终态或切已注册 Adapter；不改业务 Contract |
| Lab/Procedure | evidence 收集脚本、路径、手册错误 | 修工具/手册；不把它当 Runtime 能力缺陷 |
| Requirement/Probe | AC 歧义、probe 自身错误 | 回到人澄清/测试设计；不放宽 Delivery Gate |

### 5.4 审阅强度按风险分级

- 纯文档/模板：diff check + 一次总审，不反复做真仓；
- Context/解析：定向 Contract test + 全量单测；
- 状态/恢复/权限/Delivery：定向故障注入 + 全量单测 + Scenario test；
- 达到一个可见用户里程碑后才跑一次全新真仓；
- 真仓失败只修首个根因，不同时优化五个观察项。

---

## 6. Codex 改造执行计划

### 全局执行约束

Codex 在每个 PR 开始前必须：

1. 读取本文、两份上位文档和本 PR 指定现有代码；
2. 记录当前 HEAD 与用户已有未提交改动，禁止纳入/覆盖无关改动；
3. 只实现当前 PR；不提前开始后续 PR，不启动客户真仓；
4. 使用 Java 8；优先复用 `.properties`、现有 Markdown Rule 和现有模块；
5. 运行定向测试、`mvn clean test`、`git diff --check`；
6. 交付 commit、文件清单、测试结果、已知限制和下一 PR 的明确前置；
7. 不因为测试数增加就宣称用户路径完成。

### PR1 · Context Package v2：确保模型真的拿到正确工作单

**目标：** 所有 Adapter 以相同方式收到短 Control/Task 正文、P1 清单和受控读取范围；生产不再使用 unlimited Package。

**主要改动：**

- 在 `ai4se-context` 增加最小的 `ModelInputEnvelope`/source reference/section budget 数据结构；不建新模块；
- 每个 Package 生成 `model-input.md`：内嵌 Role Contract、Control Card、Task Card，列出初始 evidence 和 read/write scope；
- `manifest` 记录源路径、SHA、字节数、included/dropped 与理由；
- `ContextPackagePrompt` 内嵌 `model-input.md` 的短正文，而非只给 manifest；代码/测试大切片仍由明确路径让 Agent 读取，避免 argv 过大；
- `AdapterRequest` 带 read scope；write scope 继续沿用现有强制门禁；
- 生产配置设置有限 bytes 预算；P1 超限返回 `CONTEXT_CONTRACT_TOO_LARGE`，不得静默裁 AC/规则；
- 保持现有 `manifest.md` 可读兼容；不迁移 `.story`。

**验收：**

- Codex/Cursor/Claude 的最终 prompt 都包含相同 Control/Task 语义；
- 缺 model-input、源 SHA 变化、P1 超限时 Adapter 不启动；
- P2 先裁剪，Control Card 的 AC/范围/blocker 规则不能裁；
- 恢复后从冻结源重建出相同 control SHA；
- 全量测试通过。

### PR2 · Analysis/Planning 上下文完整：先理解再计划

**目标：** 模型的问题与计划建立在真实仓库入口、相关知识、附件和现有代码模式上。

**主要改动：**

- Analysis P1 加入已验证 `entries.yaml`、baseline、附件 manifest/能力说明；Knowledge 正文继续按相关性进 P2；
- Analysis 允许受控读取仓库，必须在 discovery 中引用实际路径/符号；
- Planning P1 加入 Gap、Clarification Resolution、相关规则、entries、相关 Repository Context/Knowledge IDs；
- Planning 输出最小 `plan.md`、Change Map、Test Strategy 和影响声明；只有 API/Data 改动才要求对应文档；
- 附件记录 mime/SHA/用途；Adapter 不支持图像时必须产生可回答问题，不能假装已读；
- onboard 补 `module-map.md` 和最小架构/业务知识草案，但标记 draft，需人确认后才作为 Rule/业务事实；不做自动写回。

**验收：**

- Planning Package 缺 Gap/澄清/入口时拒建；
- 模型计划至少引用一个仓库证据和每条 AC 的验证方式；
- 图片能力支持/不支持两条路径均有测试；
- 不创建空 API/Data 文档；
- 全量测试通过。

### PR3 · Durable Clarification + Plan Approval：完成真实人机闭环

**目标：** 用户只通过 CLI/OMP 输入答案和批准，不手工创建 `.story` 文件。

**主要改动：**

- 用 `.properties` + 渲染 Markdown 实现多问题 `ClarificationRequest/Answer/Resolution`；每题有 id、why、evidence、options、recommended、impact；
- Analysis BLOCKED 但没有具体问题时合同失败；
- `resume --answers <file>` 构建 Clarification Package，调用专门 `Clarification` role；
- Resolver 只能输出仍 BLOCKED 或 CLEAR + 逐题依据；Control 不因 answer 文件存在而直接 CLEAR；
- `resume --approve-plan` 写入受控批准事件；生产默认等待一次 Plan Approval，不让用户手写 approval；
- CLI 显示当前状态、问题/计划摘要和下一条可复制命令；`interactive` 代表友好 interrupt payload，不让 Java 进程或模型子进程无限等 stdin；
- batch 保持 BLOCKED 后退出并入队，不猜答案。

**验收：**

- CLEAR 需求直接到 Plan Approval；
- BLOCKED → 回答 → 仍 BLOCKED 可再次提问；
- BLOCKED → 回答 → CLEAR → Planning；
- 答案不能静默修改 AC/write scope；
- 进程重启后可恢复；暂停前副作用幂等；
- 全量测试通过。

### PR4 · Effective Constraints + Test Design + 高质量 Defect 回环

**目标：** 首轮开发和每轮修 Bug 使用同一工程边界；验证由开发前冻结的测试设计决定。

**主要改动：**

- 扩展现有 Rule Markdown header：`roles`、`paths`、`severity`、`check/review_check`；保持旧格式兼容并记录 legacy；
- 在 Plan 批准前按 Change Map 编译 `effective-constraints.md/properties`；包含 baseline、Plan/rule SHA、命中规则、必跑命令和正确范例；
- 在 Planning 内增加受控 Test Design 子步骤：形成 AC→probe spec；由 Test Design role 只能写 probe 目录，baseline 执行后冻结，Development 不可写；不新增一级 WorkflowStage；
- Dev Package 使用短 Control Card，不复制全 Plan/全规则；添加目标代码、直接接口、邻近测试和 approved exemplar 初始切片；
- Defect Package 包含当前 AC、期望/实际、复现、关键栈和文件指针；下一轮复用相同 control/constraint SHA，只更新 Task/Evidence/代码切片；
- 每轮验证运行受影响 AC + 正式回归入口；修复一个 AC 后重新检查全部已冻结 AC；
- Review 使用同一 Constraints、真实 diff、AC matrix 和检查结果；不得只看 changed-files 列表。

**验收：**

- Round 1/2/3 的 Control/Constraint SHA 相同；
- 修 Bug 时当前 Defect 是增量输入，旧 AC/范围/规则仍在；
- Rule/probe/范围外修改均被拒绝；
- formatter/lint/typecheck/build/test 只运行客户仓已声明入口；
- 至少一个 Scenario 测试证明“修 AC2 不能破坏已通过 AC1”；
- 全量测试通过。

### PR5 · 产品入口、交付制品与一张真仓 Release Story

**目标：** 用自然操作跑通 Release 1.0，而不是再证明单个门禁。

**主要改动：**

- `onboard`、`run --interactive`、`resume --answers`、`resume --approve-plan`、`status` 形成完整 CLI；OMP 只需调用这些命令并展示产物；
- Delivery Report 汇总 commit、AC matrix、测试、规则、缺陷轮次、已知风险、回滚和人工验收项；
- evidence 收集器理解新制品，不覆盖原始 evidence；
- 更新用户手册，删除需要人工手写 `.story`/ack/approval 的生产步骤；
- 在全新 litemall worktree 选择一张未泄露实现答案、3–5 AC、有一个真实歧义的卡，完成一次正式运行；不 retry 覆盖证据。

**Release 验收：**

- 用户仅提供 requirement/附件、回答、批准和最终验收；不手工修过程文件；
- Analysis 问题有证据且可回答；
- Plan、范围、测试策略和 Constraint 可读；
- ≤3 轮达到所有 AC PROVEN、Review PASS、本地 commit，或以诚实可诊断终态停止；
- scope violation=0；probe/规则未被修改；
- 原始 evidence、Package SHA、最终 diff 可复核；
- 代码人工审阅没有 P1 架构/安全问题。

PR5 通过后可以进入客户仓的低风险真实使用。多 Story 调度不属于 Release 1.0 阻塞项。

---

## 7. Release 后怎样让代码一轮轮更健康

每张被人接收的 Story 成为新的客户 baseline。下一张卡必须基于这个 commit 重新 Analysis；不能复用旧候选文件和旧 Plan。若两张卡并行，必须独立 worktree，合并后重跑两张卡的 AC 与客户回归入口。

人验收后才允许提出知识/规则更新：

- 仅本卡事实写 Story retrospective；
- 两张卡重复的模式才候选 Knowledge/Skill；
- 三张卡重复且违反会造成真实缺陷，才候选 blocker Rule；
- Rule 晋升需要人审和一个自动 check 或明确 Review check；
- 不自动把某次模型的实现选择晋升成“客户规范”。

维护一个固定回归故事集：至少包括文本清晰卡、需澄清卡、附件卡、一次 Defect 修复卡。Context Builder、Prompt、Adapter、规则选择或模型版本变化时，先在该集合比较六类指标，再改默认值。这使系统的迭代由证据驱动，而不是不断打补丁。

---

## 8. 明确禁止 Codex 在本轮整改中做的事

- 不新增第九能力域或新 Maven 模块；
- 不引入 LangGraph、AutoGen、向量库、消息队列或新的配置框架；
- 不搬迁 `.story`；
- 不重写已稳定的 RunLedger、Workflow、Adapter registry、probe 和 PASS-only Delivery；
- 不以更多测试数量代替 Release Story；
- 不在修一个失败时顺手重构全项目；
- 不未经两张真实卡证据新增通用抽象；
- 不提前实现多卡并行；
- 不自动 push、部署或使用生产凭据。

这五个 PR 的标准不是“文档全部实现”，而是让同一条用户路径逐步获得：正确输入、真实澄清、受控计划、稳定编码、独立验证和可接受交付。完成 PR5 后再以真实客户使用暴露的问题决定下一版本。
