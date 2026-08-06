# 蓝图对齐 · 环节思想加固施工单

> **落地进度（2026-08-06）：** Ω0–Ω3 已合入。**Ω4** Review Package+Adapter、ASSUMABLE ack、附件槽；**Ω5** Knowledge 读侧 + Learning 拒绝静默 fixture；**Ω6** Verify ENV_FAIL≠Defect + 分类测——**已合入本仓代码**。开跑闸门按问题类勾选见 §4。  
> **实测的位置：** [第一次压测证据](./wmp-260805-pressure-test-report-and-fix-plan.md) = **规划落地时**各环节思想/契约未闭合的现场记录——用来校准「哪一环要加强」，**不是**改蓝图、也不是发现 A 就补 A。  
> **本单目标：** 按蓝图主链，对每个能力域做**问题类级**的环节思想加固，使同类变体（解析 A / 解析 B、入口 A / 入口 B、失败 A / 失败 B）同一次被封住；再开第二次真需求。

---

## 0. 思想纪律（高于一切具体改法）

### 0.1 三句话

1. **不偏离蓝图规划** — 域、顺序、谁负责什么，以蓝图与 Stage Contract 为准；实测只暴露「实现未达标 / 契约过软」。  
2. **不为实测打补丁** — 禁止「碰到反引号就剥反引号」「碰到 WSL stub 就在一处跳过 stub」。  
3. **按问题类加固环节思想** — 先问「这一环在蓝图里本该靠什么机制保证？」，再落契约与共享机制，使可预见的同类问题一并消失。

### 0.2 实例补丁 vs 问题类加固（必读）

| | ❌ 实例补丁（禁止当完成） | ✅ 问题类加固（本单要求） |
|--|---------------------------|---------------------------|
| **Allowed / 解析** | 修了「反引号」；下次 `(new)`、引号、全角括号、列表嵌套又炸 | **Plan 产物是带 schema 的制品**：Allowed 行语法进 Planning Contract；写入/读取**校验拒绝**；提示词服从契约；normalize 只做路径语义。A/B/C 变体同属「不合规制品」一类 |
| **进程 / Shell** | 只在 `CommandArgv` 跳过 WSL stub | **执行环境是 Orchestration/Execution 共享原语**：凡起 shell 走同一解析；全调用点；preflight 属 Control 开跑门闸。stub / 找不到 / 权限 同属「环境未就绪」一类 |
| **Claude 二进制** | 文档写「请 export」；或只加一条 `%APPDATA%` 路径 | **Adapter 二进制解析策略**：env → 探测表 → **明确失败**；与 preflight、IO 失败分类同一套「执行器未就绪」思想 |
| **测试入口** | 手改某次 `entries.yaml` 加上前端；或只改 Verify 读多条 | **Onboarding 的职责是数字化全部相关入口**（累加、可读 scripts）；Verify **只消费**清单。漏前端与漏 gradle 同属「入口事实不完整」一类 |
| **Dev 不见 AC** | Dev 提示词里补一句「去读 requirement」 | **Context Engineering：角色可见面 = Stage Contract P1**；缺 P1 拒建包。AC 与 Allowed、Defect 同属「契约切片」，不是某个 bug |
| **Review 恒通过** | 把字符串改成别的默认值 | **Review 是蓝图第 9 环**：有 Package、有 Adapter、有结构化结论；fixture 只能显式披露且不得冒充闭环 |
| **Verify 假红** | 针对 Bug#3 特判 | **失败分类是 Control 思想**：环境/IO/门禁/客户测试红 分流；环境红不得进 Defect Loop。WSL 与「claude 找不到」同属一类 |
| **KB 半成品** | 某次打开 NOOP | **Knowledge 必须可检索**：写与读是同一域职责；Package 消费 hits；空库诚实披露 |

**验收口诀：** 若修复描述里只能说出「修了现象 A」，说不出「封住了哪一类机制、同类 B 为何不再发生」——则仍是补丁，退回重做。

### 0.3 规划轴（蓝图主链 · 不增域）

```text
① Onboarding → ② Knowledge → ③ Context → ④ Analysis
  → ⑤ Planning → ⑥ Development → ⑦ Verification
       ↘ FAIL → ⑧ Defect Loop → ⑥ → ⑦ …
       ↘ PASS → ⑨ Review → ⑩ Delivery → ⑪ Learning → ②
横贯：⑫ Orchestration
```

当前八域落地目录与蓝图十二件事情的对应，以 [capability-map](../00-product/capability-map.md) 为准；**本单不发明第十三件，不改主链顺序。**

### 0.4 每环书写格式

每节固定四层（先思想，后证据，再机制，最后触点）：

1. **蓝图本意** — 这一环本来保证什么  
2. **实测暴露的问题类** — 用类名说，不堆个案标题  
3. **环节思想加固** — 机制/契约怎么变硬（封住该类）  
4. **触点与完成定义** — 代码与文档；完成定义必须能回答「同类 B 为何不再发生」

---

## 1. 按蓝图主链的环节思想加固

### ① Onboarding — 仓库数字化（入口是事实，不是猜一次）

| 层 | 内容 |
|----|------|
| **蓝图本意** | 第一次接触客户仓，建立 AI 可理解的槽位与**构建/测试入口清单**；不思考、不分析业务 |
| **问题类** | **入口事实不完整 / 探测模型过窄**（互斥单选、只看根、写死 `npm test`）— 个案「看不见 frontend」只是该类的一个实例 |
| **环节思想加固** | 探测模型改为：**多模块累加 + 可配置深度 + 读 package.json scripts 选键**；结果写入可审计 Facts（`build_systems_detected`）。Verify **禁止**靠人手补 entries 假装 Onboarding 完成 |
| **同类不再发生** | 漏前端、漏第二套 maven、漏 vitest 而非 npm test → 同属入口模型，同一次机制覆盖 |
| **触点** | `scripts/onboard-repo.sh`；`onboarding-runbook.md`；`repository-facts-contract.md` |
| **完成定义** | 多模块 fixture 绿；Runbook 写清 depth/scripts；合同写明「入口清单是 Onboarding 成功标准」 |

---

### ② Knowledge — 可检索的知识面（不是只写盘）

| 层 | 内容 |
|----|------|
| **蓝图本意** | 维护理解项目所需知识：建/更/汰/**检**；供 Context Builder 取用 |
| **问题类** | **只写不读 / 闭环断开**（NOOP 无披露、Analysis 不消费 index） |
| **环节思想加固** | 读侧是域内一等能力；Package 组装必须经过检索（hits 可审计）；Lifecycle 策略显式；Learning 写回与读出同一套 index 契约 |
| **同类不再发生** | 「这次忘了打开 learning」「空库却声称基于知识库」→ 用 hits 与 noop_reason 机制封死，不是改某次配置 |
| **触点** | Lifecycle；检索组件；Analysis/Planning PackageBuilder；`docs/60-knowledge-lifecycle/*`；knowledge-retrieval-contract |
| **完成定义** | 写→读夹具；manifest 有 `knowledge_hits`；NOOP 必带 reason |

---

### ③ Context Engineering — 角色可见面 = 契约（最重要）

| 层 | 内容 |
|----|------|
| **蓝图本意** | 每一步 AI 该看见什么 → Context Package；缺 P1 拒跑；约束靠 Contract 不是靠念经 |
| **问题类** | **契约切片缺失 / 角色提示空洞**（Dev 无 AC、Review 无包、roleExtra 空）— 不是「忘了加一个文件」的个案 |
| **环节思想加固** | 维护并实现 **角色 × P1 矩阵**（与各 Stage Contract 逐条对齐）；缺任一条 P1 → 拒建包/拒跑；每个角色有结构化 `roleExtra`；预算触顶不得丢掉 Contract 规定的 P1 |
| **同类不再发生** | 缺 AC、缺 Allowed、缺 Defect、缺 Verify 结论给 Review → 同属「P1 矩阵未满足」，同一拒跑机制 |
| **触点** | 各 `*PackageBuilder`；`ContextPackagePrompt`；`docs/20-context-engineering/*`；各 stage-contract |
| **完成定义** | 矩阵入库；五角色单测锁 P1 集合；故意缺 AC 建 Dev 包失败 |

---

### ④ Analysis — 按 Contract 理解需求（含未知与停）

| 层 | 内容 |
|----|------|
| **蓝图本意** | 范围/风险/未知/BLOCK；可停问；吃的是 Package 不是自由翻仓 |
| **问题类** | **假设无治理 / 输入通道过窄**（ASSUMABLE 无声放行；需求仅纯文本、原型无槽位） |
| **环节思想加固** | Gap 状态机思想补全：CLEAR / ASSUMABLE（须 ack 或披露策略）/ BLOCKED（硬停）；需求输入契约含附件槽位与「未读附件不得假装已审」；Analysis 包消费 Knowledge 读侧（接 ②） |
| **同类不再发生** | 无人确认的假设、未声明的附件 → 同属 Analysis 输入/闸门契约 |
| **触点** | `PathwayRunner` Gap；Clarification；requirement 附件约定；analysis-contract |
| **完成定义** | ASSUMABLE 策略有测；附件槽位与拒跑有约定；与 ② 联调 hits |

---

### ⑤ Planning — Plan 是制品（有 schema），不是散文

| 层 | 内容 |
|----|------|
| **蓝图本意** | 需求 → Plan Bundle；含 Allowed/Declared Files、测试策略等 |
| **问题类** | **制品格式无契约**（模型用反引号、尾注、夹叙 → 下游门禁误杀）。个案 Bug#1/#2 同属此类 |
| **环节思想加固** | Allowed（及关键 Declared 列表）**语法进 Planning Contract**；Adapter 提示服从契约；`PlanRecords` **校验拒绝**不合规制品；路径规范化是语义层。**禁止**「多写几个 if 兼容一种新写法」当演进方式——要扩展先改正文契约再改校验器 |
| **同类不再发生** | 反引号、双引号、`(new)`、`# comment`、全角括号 → 一律「不合规制品」；合规后 DiffScope 只比路径集合 |
| **触点** | `planning-contract.md`；`ContextPackagePrompt` Planning；`PlanRecords`；`DiffScopeGuard`（薄） |
| **完成定义** | 合同有 BNF/样例；脏制品拒入单测；扩展新语法必须先改合同（流程写入 Runbook） |

---

### ⑥ Development — 在允许面内交付变更（看见 AC）

| 层 | 内容 |
|----|------|
| **蓝图本意** | 按 Plan 改码；不越权；不自评验绿 |
| **问题类** | **Dev 包违背已有 Development Contract**（P1 已写 Acceptance，实现未装） |
| **环节思想加固** | 还清契约债：P1 = AC + Allowed + plan 要点 + 条件 gap/defect；roleExtra 禁止验绿自评；越权由 DiffScope 按**路径集合**执行（上游制品已合法） |
| **同类不再发生** | 「模型不知道 AC」「不知道假设」→ 同属包契约，不靠临时加 prompt 一句 |
| **触点** | `DevPackageBuilder`；`development-contract.md`；prompt |
| **完成定义** | 无 AC 拒建包；有 AC 出现在 manifest priority1 |

---

### ⑦ Verification — 消费入口事实，诚实判据

| 层 | 内容 |
|----|------|
| **蓝图本意** | 调用客户已有测试能力证明需求；不自评；输出可复查 Report |
| **问题类** | **只消费到残缺入口 / 把环境失败当成测红 / 单命令思想** |
| **环节思想加固** | Verify **只信任 Onboarding 入口清单**（可 story 覆盖但须披露）；多 entry **合取**；Report 逐条结果；`verdict_basis` 诚实；Diff 触及某侧代码则该侧入口缺失 → **门禁类失败**（不是补一条前端命令了事）。结构化报告可作证据附件，**不**伪装 AC 逐条评分 |
| **同类不再发生** | 缺前端、缺 e2e、缺第二套后端模块测试 → 「入口相对 Diff 不完整」一类门禁 |
| **触点** | `VerificationControl`；verification-contract；defect-package-contract |
| **完成定义** | 多命令合取测；Diff∩前端∧无前端 entry → 拒跑/硬披露有测 |

---

### ⑧ Defect Loop — 仅客户验证真失败才回环

| 层 | 内容 |
|----|------|
| **蓝图本意** | Verify FAIL → 结构化 Defect → Dev → Verify |
| **问题类** | **回环触发条件过宽**（环境假红进入 Defect）；**真业务回环未用问题类验证过** |
| **环节思想加固** | Control 规定：仅 `VERIFY_FAIL` 进 Loop；Defect 制品含失败 entry、AC、建议范围；用受控夹具验证「真红→再 Dev→再绿」这一**类路径**（不是只修 Bug#3） |
| **同类不再发生** | WSL、找不到命令、超时启动 → 永不进 Defect；只有客户测试红才进 |
| **触点** | Runner V4；`DefectPackageWriter`；defect-contract |
| **完成定义** | 分类单测 + 一条真回环证据包 |

---

### ⑨ Review — 结构化终检（第九环要真）

| 层 | 内容 |
|----|------|
| **蓝图本意** | 查需求/Rule/质量；不替代 Verify；输出结论与残留风险 |
| **问题类** | **第九环被常量短路**（无 Package、无 Adapter、恒通过） |
| **环节思想加固** | 与 Analysis/Plan/Dev 同一 Adapter 思想：Review Package（diff+AC+verify 结论）→ Adapter → **结构化** AC→证据；解析失败拒入 Delivery；fixture 仅显式披露且不得用于「蓝图闭环已证明」的签收 |
| **同类不再发生** | 一句「通过」、缺证据、无 Verify 却过 Review → 同属制品/门禁拒绝 |
| **触点** | ReviewPackageBuilder；`reviewAdapter`；review-contract |
| **完成定义** | 结构校验测；假 Adapter 不通过可测；合同禁止替代 Verify |

---

### ⑩ Delivery — Commit 与人验收闸（不 Push）

| 层 | 内容 |
|----|------|
| **蓝图本意** | Commit / 产物 / 等人验收；不 Push |
| **问题类** | **人验收在闭环中无闸**（FIXTURE 仍可触发 Learning） |
| **环节思想加固** | 在**不偏离**「Delivery 后等人验收」蓝图前提下：验收是 Learning 前置闸；`acceptance_kind` 诚实（human / deferred 披露 / 禁止静默 fixture 写回）。若将来升 `WorkflowStage.ACCEPTANCE`，**先改 capability-map/蓝图叙述再改枚举** |
| **同类不再发生** | 假验收写知识、无验收写知识 → 同一闸 |
| **触点** | `HumanAcceptanceRecords`；Lifecycle.requireAccepted；delivery-contract；status 文档 |
| **完成定义** | 无验收且非 deferred → Learning 不写 |

---

### ⑪ Learning — 经验回写（为下次检索）

| 层 | 内容 |
|----|------|
| **蓝图本意** | 沉淀交付经验 → Knowledge/Rule/Skill |
| **问题类** | **写回未纳入主链验证**（NOOP 当默认成功） |
| **环节思想加固** | Learning 是主链末环：验收闸后写回；index 更新；供 ② 检索；条目带 story/证据指针可汰 |
| **同类不再发生** | 「又忘了写回」「写了读不到」→ 写读契约同一套 |
| **触点** | Lifecycle；learning-contract |
| **完成定义** | 写回 + 下 Story 读到的夹具 |

---

### ⑫ Orchestration — 调度与执行就绪（横贯）

| 层 | 内容 |
|----|------|
| **蓝图本意** | 启停、Resume、Session、熔断、审批、回环；直到 Story 完成 |
| **问题类** | **执行就绪与失败分类未成为 Control 思想**（裸 bash、裸 claude、无 preflight、IO≈业务红、Session 账未接线） |
| **环节思想加固** | ① **环境原语**（`ShellExecutable` 等）全仓唯一；② **开跑 preflight** 属 Control 门闸；③ **失败分类表**（ENV / ADAPTER_IO / GATE / VERIFY / BUSINESS）决定是否重试、是否进 Defect、是否 STOP；④ 阶段切换写 Session 审计（角色切换 = new session 可证明） |
| **同类不再发生** | WSL stub、Git Bash 路径、claude 未装、entries 命令不存在 → 「未就绪」一类，开跑前死；中途 IO → 有限重试类；测红 → Loop 类 |
| **触点** | `ai4se-common`；CommandArgv；OnboardRepoScript；ClaudeCliAdapter；PathwayRunner；SessionDecisionRecorder；adapter-contract |
| **完成定义** | 全仓无裸 bash 生产调用；preflight/分类/session 审计均有测 |

---

## 2. 问题类总表（用类验收，不用个案验收）

| 问题类 | 蓝图环节 | 加固思想 | 禁止的补丁形态 |
|--------|----------|----------|----------------|
| 制品格式无契约 | ⑤ Planning（及一切列表型制品） | Schema + 校验拒绝 + 合同演进 | 每遇到一种写法加一个 strip |
| 入口事实不完整 | ① Onboarding → ⑦ 消费 | 累加探测 + scripts + Diff 相对完整性门禁 | 手改某次 entries；只改 Verify |
| 契约切片缺失 | ③ Context | 角色×P1 矩阵 + 缺则拒跑 | 某个角色 prompt 里补一句 |
| 执行未就绪 | ⑫ Orchestration | 共享原语 + preflight + 明确失败 | 单点 PATH 特判；文档要求人工 export |
| 失败语义混淆 | ⑫ + ⑧ | 分类表驱动去向 | 某个 exit code 特判 |
| 知识闭环断开 | ② + ⑪ | 读侧一等 + 写读同一 index | 某次关掉 NOOP |
| 终检环短路 | ⑨ Review | Package+Adapter+结构结论 | 改默认字符串 |
| 验收闸缺失 | ⑩ Delivery | Learning 前置诚实验收 | 继续 FIXTURE 装完成 |
| 假设/附件无治理 | ④ Analysis | Gap 状态+ack；附件槽契约 | 忽略 ASSUMABLE；需求永远纯文本 |

---

## 3. 实施批次（先思想与合同，后机制）

```text
Ω0  合同与矩阵：各 Stage Contract / Package 矩阵 / 失败分类表 / Allowed schema
     （无代码补丁；蓝图表述不一致处只补「实现欠债」，不改域边界）
Ω1  ⑫ 执行就绪原语 + preflight + 失败分类 + Session 审计
Ω2  ① 入口累加模型 + ⑦ 合取消费 + Diff 相对完整性
Ω3  ⑤ 制品 schema 拒绝 + ⑥ Dev P1 还债 + ③ 全角色矩阵落地
Ω4  ⑨ Review 真环 + ④ Gap/附件治理
Ω5  ②⑪ 知识写读闭环 + ⑩ 验收闸
Ω6  ⑧ 真回环类验证 + 问题类回归集 + 开跑闸门
→ 第二次真需求
```

---

## 4. 开跑闸门（按问题类勾选）

- [x] **制品类：** 脏 Allowed 拒入；合同有 schema；扩展流程是「先改合同」  
- [x] **入口类：** 多模块+scripts 探测；Diff 相对入口完整门禁  
- [x] **切片类：** 五角色 P1 矩阵测过；无 AC 拒建 Dev 包  
- [x] **就绪类：** Shell/Claude/preflight；全仓无裸 bash  
- [x] **失败类：** 环境红不进 Defect；测红进 Loop 有证据  
- [x] **知识类：** hits 可审计；写读夹具过  
- [x] **终检类：** Review 结构结论；非静默 fixture 签收  
- [x] **验收类：** Learning 前置闸  
- [x] **分析类：** ASSUMABLE 策略 + 附件槽  
- [x] 文档与 `current-support-status` 已按蓝图环节回写  

---

## 5. 与压测个案的映射（个案只作证据，不作施工单位）

| 压测个案 | 归属问题类 | 环节 |
|----------|------------|------|
| Bug#1 反引号 / Bug#2 尾注 | 制品格式无契约 | ⑤ |
| Bug#3 WSL bash | 执行未就绪 + 失败语义混淆 | ⑫⑧ |
| `AI4SE_CLAUDE_BIN` / CreateProcess | 执行未就绪 | ⑫ |
| 只测后端 | 入口事实不完整 | ①⑦ |
| Dev 无 AC | 契约切片缺失 | ③⑥ |
| Review 恒通过 | 终检环短路 | ⑨ |
| KB NOOP / 不读 | 知识闭环断开 | ②⑪ |
| ASSUMABLE 无声放行 | 假设无治理 | ④ |
| 无图片通道 | 附件无治理 | ④ |
| V4 未经真红 | 回环触发/验证不足 | ⑧ |

§6 三处 diff = 当时为跑通压测的**实例补丁样本**；本单要求按上表**问题类重做**，禁止原样合入当完成。

---

## 6. 执行约定

- 每改一处，用 §0.2 口诀自检：能否说明「同类 B 为何不再发生」。  
- 合同与蓝图冲突时：**先讨论是否契约欠清晰**，默认不改蓝图域边界。  
- 说「按环节思想施工单开做」→ 从 **Ω0 合同与矩阵**开始，而不是从剥反引号开始。
