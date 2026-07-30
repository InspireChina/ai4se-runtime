# 串行工程流水线设计：摸底 → 澄清 → 施工 → 测试 → 交付 → 知识沉淀

> 目标：先用 **一条串行流程** 跑通「需求进来 → 人少介入但关键处可拷问 → 有施工图与测试方案 → 编码受门禁约束 → 测试按方案验收 → 交付并回写知识库」。  
> **不谈自动进化。**  
> 结合当前 AI4SE Runtime 水位（Task / Artifact / Context / Worker / Trace / Checkpoint 可写未 Resume）与 Claude CLI `/resume`、上下文压缩的真实坑。  
> 性质：实操设计说明，不是 Frozen 架构修改案。

---

## 0. 先定生死结论（读完这节就能排期）

### 0.1 你要的闭环，翻译成工程语言

```text
摸底(只读事实)
  → 澄清(人机问答，强制产出)
  → 施工图 + 测试方案(同一「计划阶段」产出，多份 Artifact)
  → 编码(受契约/规则门禁)
  → 按测试方案验收(失败不可假装成功)
  → 交付包
  → 知识库增量更新(供下次同类业务复用)
  → （可选）流程问题记一笔，人工改流程模板 —— 不是系统自己进化
```

### 0.2 哪些能做实，哪些仍要人盯

| 环节 | 无人值守程度 | 必须人介入吗 |
|------|--------------|--------------|
| 摸底 | 可高自动化 | 通常否（结果要可审计） |
| 澄清拷问 | **必须可停等人** | **是**（否则需求幻觉） |
| 施工图/测试方案 | 可半自动生成 | 建议人审一次再开工（至少 v0） |
| 编码 | 可自动跑 Worker | 靠门禁，不靠信任 |
| 测试 | 可自动跑 | 方案本身要人对过 |
| 交付 | 可自动打包证据 | 发布决策可仍由人 |
| 知识回写 | 可自动起草 | **建议人确认后入库**（防脏知识） |
| 「批量消化还不烂」 | **有上限** | 人少 ≠ 无人；门禁越硬人才能越少 |

### 0.3 和当前 Runtime 怎么挂

不要新发明「超级大脑对象」。用现有概念硬套：

| 流水线概念 | Runtime 落点 |
|------------|--------------|
| 一次需求交付 | **一个父 Task**（或「计划 Task + 施工 Task」两段，见后文） |
| 摸底/澄清/编码/测试 | **串行阶段**；每阶段一个（或一组）Worker |
| 人回答拷问 | Task 进入 **等待人工**（蓝图里的 BLOCKED_POLICY 语义）；回答写入 Artifact |
| 施工图、测试方案、代码、报告 | 全部是 **Artifact**（唯一遗产） |
| 阶段边界 | **Checkpoint**（先写；Resume 以后再做） |
| 录像 | **Trace** |
| skills/rules | **能机器执行的进 Verify Worker**；其余只是提示，不算遵守 |

---

## 1. 推荐的串行阶段（v0 施工模板）

> 原则：**串行先跑通**；并行、多 Agent 以后再说。  
> 每一阶段结束必须有 **合格 Artifact**，否则不许进入下一阶段（这是防「执行不到位自己绕过」的总开关）。

### 阶段 A — 需求摸底（Fact Discovery）

**目的：** 搞清楚「仓库里已有什么」，不是发明需求。

**输入：**

- 业务需求原文（Artifact: `req.raw`）
- 项目 Profile：语言、构建命令、测试命令、目录约定

**做什么（只读 Worker 为主）：**

1. Repo 清单：模块、关键路径、技术栈指纹  
2. 相关代码定位：关键词/路径/接口名召回（先粗糙也行）  
3. 已有文档/ADR/OpenAPI 索引  
4. 历史同类 Task/失败 Trace 摘要（有则引用）

**强制产出 Artifact：**

| ID 建议 | 内容 |
|---------|------|
| `discovery.repo-map` | 目录与模块图（文本即可） |
| `discovery.hit-set` | 命中的文件/接口列表 + 为什么相关 |
| `discovery.gaps` | 未知项清单（后面拷问要用） |
| `discovery.risks` | 可能误伤的既有功能 |

**人介入：** 默认不需要。若 hit-set 为空或 gaps 过多，可自动停，让人补关键词。

**和蓝图 Knowledge / Repo Graph / Profile：**

- Profile：v0 用一份 YAML/JSON 配置即可（不是引擎）  
- Repo Graph：v0 用「路径树 + 引用列表」冒充；别先上图数据库  
- Knowledge：v0 = **可检索 Artifact 仓库**，不是向量神话

---

### 阶段 B — 需求澄清（Human Interrogation）

**目的：** 把 gaps 问成人话答案；没有答案不许开工。

**机制（必须可停）：**

```text
系统根据 discovery.gaps 生成「拷问清单」Artifact: clarify.questionnaire
  → Task 状态：等待人工（人工未答 = 未完成）
  → 人提交 clarify.answers
  → 校验：必答项是否都有答案；冲突项是否标记
  → 产出 clarify.resolved-requirements（澄清后的需求正文）
```

**拷问题类型建议（少而狠）：**

1. 范围：做什么 / **明确不做什么**  
2. 用户与场景：谁在什么界面完成什么  
3. 数据与接口：谁拥有数据；前后端边界  
4. 兼容：老数据/老接口怎么办  
5. 验收：怎样算做完（可测试的句子）  
6. 风险：允许破坏什么、不允许破坏什么  

**人介入：** **本阶段核心就是人。**  
想「人少」可以少问，但不能零问就进编码——那是你现在 ai-loop 长远性差的根源之一。

---

### 阶段 C — 计划区：施工图 + 测试方案（同一计划阶段，多份文档）

**目的：** 编码前先有「图纸」和「考卷」；考卷不是编码后补的。

#### C.1 该「一个上下文生成」还是「子 Agent」？

| 方案 | 做法 | 优点 | 缺点 | v0 建议 |
|------|------|------|------|---------|
| **A. 同阶段、同 Worker、一次（或两次）生成** | 一个 Plan Worker：输入摸底+澄清，输出多份 Artifact | 简单、上下文一致、好串行 | 上下文易爆；一份烂可能两份一起烂 | **首选** |
| **B. 同阶段、串行两个 Worker** | 先 `plan.design`，再 `plan.test`（测试 Worker 只读设计稿） | 职责清晰；测试方案可挑设计漏洞 | 多一次调用成本 | **强烈推荐的细化** |
| **C. 子 Agent 并行** | 设计 Agent ∥ 测试 Agent | 快 | 上下文分裂、互相矛盾、难审计 | **v0 不做** |
| **D. 多会话 /resume 拼** | 靠 CLI resume 续写长文档 | 像省事 | **坑最多**（见第 3 章） | **尽量避免当主方案** |

**中肯结论：**

> **计划阶段用「同一父 Task 的同一计划阶段」；  
> 产出拆成多个 Artifact；  
> 生成方式用「串行两次调用」优于「一个超级 prompt 一次写完」；  
> 不要用子 Agent 并行，也不要把 Claude `/resume` 当架构。**

#### C.2 强制产出（施工图包）

| Artifact | 必须包含 |
|----------|----------|
| `plan.non-goals` | 不做清单 |
| `plan.architecture` | 模块归属：前端 / 后端 / 双方；数据归属 |
| `plan.interfaces` | API/事件/页面契约草稿（能测） |
| `plan.work-breakdown` | 有序步骤（仍串行执行） |
| `plan.risks-and-rollback` | 风险与回滚 |
| `plan.test-strategy` | 测什么、不测什么、用项目哪条命令 |
| `plan.acceptance` | Given/When/Then 或等价验收句 |
| `plan.impact-surface` | 可能被影响的既有功能（回归焦点） |

**质量门（进入编码前）：**

- 缺任一强制 Artifact → 失败  
- `plan.interfaces` 与 `plan.acceptance` 对不上 → 失败  
- 前后端都改但没有共享契约字段 → 失败  
- （可选）人点「批准计划」Artifact: `plan.approved` —— **v0 强烈建议要**

---

### 阶段 D — 编码（受 skills/rules 约束的真实含义）

**目的：** 按施工图改代码；不是按聊天感觉改。

**输入必须钉死：**

- `clarify.resolved-requirements`  
- `plan.*` 全套  
- `discovery.hit-set`（防止乱改无关模块）

**执行方式（串行）：**

```text
按 plan.work-breakdown 逐步：
  每步 → Coding Worker（可先人工/后接 Claude）
       → 产出 patch/diff Artifact
       → 跑静态门禁 Worker（编译/lint/格式）
       → 失败则停（或有限次重试，禁止无限绕过）
```

**skills / rules 怎么才不算假遵守：**

| 类型 | 处理 |
|------|------|
| 能机器查的 | **Verify Worker 强制**（例：禁止无测试的 public API；OpenAPI 变更必须提交） |
| 设计原则（扩展性/可维护） | 写成 **计划检查清单 + 评审题**；部分可用静态规则近似，不能 100% 自动化 |
| 「以后发展多角度」 | 主要靠 `plan.non-goals` + 模块边界 Artifact，不是靠更长 skill 文章 |

**防「自己绕过」：**

- Coding Worker **无权**把 Task 标 SUCCEEDED  
- 只有 Verify/Test 阶段成功才能推进  
- 不允许 Worker 修改「门禁列表」本身（改门禁是单独人工 Task）

---

### 阶段 E — 按测试方案验收

**目的：** 考卷是计划阶段就定的；编码后不得偷偷改及格线（改及格线要走澄清/计划变更）。

**执行：**

1. 读取 `plan.test-strategy` + `plan.acceptance`  
2. 映射到项目 Profile 的命令（unit / api / e2e）  
3. Test Worker 跑命令，收集 stdout/stderr/exitCode → `test.report` Artifact  
4. 对照 acceptance 做「覆盖检查」：  
   - 方案里写了但没跑的项 → **失败**（执行不到位）  
   - 跑了但 acceptance 未提及的，可警告  
5. 失败 → Task FAILED 或退回编码阶段（有限次）；**禁止**删测试冒充成功

**无人值守时报错策略（实用）：**

| 失败类 | 策略 |
|--------|------|
| 编译失败 | 回编码，附 `test.report`；最多 N 次 |
| 断言失败 | 回编码或停等人（若像需求误解） |
| 环境/缺依赖 | **停等人**（别让 Agent 乱改环境硬闯） |
| 超时 | 失败；保留 Trace |
| 「跳过测试」 | 视为门禁失败（exit 0 但跳过率超阈也失败） |

---

### 阶段 F — 交付

**强制交付包 Artifact：**

- `delivery.summary`（做了什么、没做什么）  
- `delivery.diff-stat`  
- `delivery.test-report`（最终）  
- `delivery.trace-id` / checkpoint 引用  
- （可选）PR 描述草稿  

人只需做：点合并 / 发布。证据链已在。

---

### 阶段 G — 本项目知识库更新（为下次少走弯路）

**目的：** 同类业务/同技术栈可复用；不是自动进化。

**自动起草、人工确认入库（推荐）：**

| Artifact | 内容 |
|----------|------|
| `knowledge.lesson` | 这次踩坑、决策、可复用模式 |
| `knowledge.stack-notes` | 本技术栈注意点 |
| `knowledge.do-not` | 明确别再做的事 |
| `knowledge.index-entry` | 供下次摸底检索的条目 |

**规则：**

- 未确认的 lesson **不得**进入「高信任知识」索引  
- 下次摸底 Worker **必须引用**命中的 knowledge Id（写进 hit-set）  
- 允许「草稿知识」与「已认证知识」分桶，防脏数据污染

---

### 阶段 H — 基于过程文档优化流程（人工）

把 Trace / 失败报告里反复出现的问题，改成：

- 拷问模板多一道题  
- 门禁多一条  
- Profile 测试命令修正  

这是 **人改流程模板**，不是系统自我进化。你可以定期开「流程改进 Task」，仍走同一套 Artifact 纪律。

---

## 2. 上下文到底怎么切？——细化决策树

### 2.1 三种「工作单元」对比

| 单元 | 是什么 | 适合 |
|------|--------|------|
| **一个长上下文** | 单会话塞进摸底+计划+编码 | 只适合玩具；一长就丢约束 |
| **一个父 Task + 多阶段 Artifact** | 状态在 Artifact，不在聊天记忆 | **平台主方案** |
| **子 Agent** | 多个模型角色并行/分治 | 后期加速；v0 易打架 |

**平台原则：**

> **真理在 Artifact，不在模型窗口。**  
> 每个阶段开始时，只装载「本阶段需要的 Artifact 子集」，不要装载全历史聊天。

### 2.2 每阶段应装载什么（防上下文膨胀）

| 阶段 | 装载 | 不装载 |
|------|------|--------|
| 摸底 | 原始需求 + Profile +（可选）知识索引摘要 | 不要装整仓代码 |
| 澄清 | gaps + 需求原文 | 不要装大段无关代码 |
| 计划 | 澄清后需求 + hit-set 摘要 + 知识条目 | 不要装完整文件内容（必要时按文件定点读） |
| 编码单步 | 本步任务 + 相关接口契约 + 触及文件 | 不要装整份长施工图全文时，可装「本步切片」 |
| 测试 | 测试方案 + 报告格式 | 不要装全部源码 |
| 知识回写 | delivery.summary + risks + 关键决策 | 不要回放全部 Trace 原文 |

### 2.3 「一个上下文区生成施工图+测试方案」怎么细化才对

推荐 **B 模式（同阶段串行双调用）**：

```text
调用1 PlanDesignWorker
  in:  clarified req + discovery
  out: plan.architecture / interfaces / work-breakdown / non-goals / impact

调用2 PlanTestWorker
  in:  上述设计 Artifact（只读）+ acceptance 目标
  out: plan.test-strategy / plan.acceptance 细化
  额外职责：挑设计漏洞（不可测的需求 → 打回澄清）
```

为什么比「一次生成两份」好：

- 测试视角会挑战设计（这是质量）  
- 单次失败可重跑测试计划，不必整份设计作废  
- 上下文更小  

为什么比「子 Agent」好：

- 无并行冲突  
- Trace 清晰：design span → test-plan span  
- 人工审批点只有一个：`plan.approved`

---

## 3. 上下文压缩与 Claude CLI `/resume`：越细越好

> 若 Coding/Plan Worker 底层用 Claude CLI，必须把 CLI 的会话机制当成 **不可靠外设**，不能当成 Runtime 状态机。

### 3.1 上下文压缩会出什么问题

| 现象 | 后果 | 平台侧对策 |
|------|------|------------|
| 中期指令被摘要掉 | 忘掉 non-goals、验收、门禁 | **关键约束放 Artifact，每轮重注入「约束卡片」** |
| 文件内容被压缩 | 改错位置、幻觉 API | 编码步只给精确文件切片；大文件不要全塞 |
| 「好像还记得」其实错记 | 沉默漂移 | 每阶段结束写 Artifact；下一阶段 **禁止依赖会话记忆** |
| 压缩后仍超窗 | 命令失败/胡写 | 阶段拆分；单步单文件优先 |

**铁律：** Runtime 的 Checkpoint/Artifact 才是 resume；模型的聊天 resume 只是加速器。

### 3.2 Claude `/resume` 多次恢复的典型坑

| 坑 | 说明 | 严重性 |
|----|------|--------|
| **状态分叉** | 本地文件已变，会话仍按旧世界说话 | 高 |
| **重复施工** | resume 后再改同一处，diff 叠罗汉 | 高 |
| **门禁失忆** | 压缩/恢复后不再提测试要求 | 高 |
| **工具权限漂移** | 会话间权限/目录假设不一致 | 中 |
| **「接着写」≠「接着对」** | 续写文档结构混乱、前后矛盾 | 中 |
| **多 resume 后不可审计** | 不知道哪次恢复引入错误 | 高 |
| **与 Git 真相冲突** | 会话以为提交了，仓库没有 | 高 |
| **并行人改代码** | 人改了，resume 会话不知 | 高 |

### 3.3 多次 `/resume` 的实操规范（若暂时必须用）

1. **每个阶段新开会话优于无限 resume**（澄清会话、计划会话、编码会话分开）。  
2. 若 resume：开场强制粘贴 **Artifact 约束卡片**（non-goals、本步目标、禁止项）。  
3. resume 前：`git status` / `git diff` 真相注入（你们已有 ShellWorker 可做）。  
4. **禁止**跨阶段 resume（不要从「写计划」的会话直接 resume 成「写代码」）。  
5. 同一阶段最多 resume K 次（如 3）；超过则新开会话 + 只带 Artifact。  
6. 每次 resume 在 Trace 打点：`cli.resume` + 序号 + 当前 HEAD。  
7. 编码会话结束必须留下 `patch` Artifact；不要以会话还在为成功。

### 3.4 和 Runtime Checkpoint 的关系（避免概念混淆）

| | Claude `/resume` | Runtime Checkpoint |
|--|------------------|--------------------|
| 存的是什么 | 聊天与工具纠缠状态 | Task 耐久切片（产物索引等） |
| 能否当真相 | **否** | **设计上应当是**（Resume 落地后） |
| 多次恢复 | 易腐、易漂 | 应不可变、可校验 integrity |
| v0 现状 | 外设可用 | **能写，正式 Resume 未做** |

**建议：** 产品叙事上写「工程 Checkpoint」；对内规定「CLI resume 禁止替代 Checkpoint」。

---

## 4. 怎么防「无人值守自己绕过」

这是你最关心的质量与完整性问题。机制要叠几层：

### 4.1 阶段门禁（Stage Gate）

没有上一阶段合格 Artifact，下一阶段 Worker **直接拒绝执行**（Engine 级，不是靠 prompt 自觉）。

### 4.2 双轨产物

- **过程产物**：问卷、计划、报告  
- **可执行门禁**：测试退出码、编译退出码  

只有过程文档没有退出码 = 未完成。

### 4.3 「改考卷」隔离

测试方案变更 = 计划变更事件，需重新 `plan.approved`；编码 Worker 不能改 `plan.test-strategy`。

### 4.4 覆盖率对方案，不只对行数

最小实现：acceptance 条目 ID 列表；测试报告必须引用这些 ID。未引用 = 缺失。

### 4.5 影响面回归

`plan.impact-surface` 列出的模块，测试策略必须点名；否则失败。

### 4.6 人工点仍保留的最小集（人少但不人无）

v0 建议保留两处人：

1. **澄清答题**  
2. **计划批准**（施工图+测试方案）  

编码与测试尽量无人；人从「写代码」转为「答边界 + 批图纸」。

---

## 5. 「以后批量消化、人少、还不烂」——诚实预期

### 5.1 能逼近的部分

- 同类需求摸底更快（因知识库有认证条目）  
- 拷问模板越来越准（人改模板，不是自动进化）  
- 门禁挡住大量低级烂代码与漏测  
- 前后端边界靠契约 Artifact 减少扯皮  

### 5.2 不能承诺的部分

- 零人介入还保证需求理解正确  
- 自动保证「架构长远」与「优雅扩展性」满分  
- 任意业务批量进来都质量均一  
- 无测试基础设施的项目也能魔法验收  

### 5.3 批量消化的前提清单（少一条都别吹）

- [ ] Profile 具备真实测试命令  
- [ ] 澄清+计划两处人闸还在（或等价强门禁）  
- [ ] Artifact 遗产可检索  
- [ ] 编码不能关闭门禁  
- [ ] 失败可从 Trace 复盘  
- [ ] 知识入库要认证  

---

## 6. 映射到「一个父 Task」的推荐编排（串行）

```text
Task: feature-X
  A discovery.*     Workers(readonly)     → gate
  B clarify.*       wait human answers    → gate
  C plan.*          design then testplan  → wait human approve → gate
  D code steps      coding + lint/compile → gate per step
  E test.*          according to plan     → gate
  F delivery.*      bundle                → gate
  G knowledge.*     draft → human certify → done
```

中间每个 gate 可写 Checkpoint（成功边界）。  
正式跨进程 Resume 可后做；先把门禁与 Artifact 纪律立住。

---

## 7. 和现有蓝图组件的取舍（再钉一次）

| 蓝图词 | 在本流水线中的真实用法 |
|--------|------------------------|
| Knowledge | 认证 Artifact 索引 + 摸底引用 |
| Repo Graph | 先路径/模块图，后图计算 |
| Profile | 项目命令与约定；先配置文件 |
| Skill | 提示可选；能检查的变门禁 |
| Rule | 少数可执行 |
| Scheduler | v0 用 Runtime 串行阶段即可 |
| 子 Agent | 非默认；计划阶段尤忌并行 |
| Claude resume | 外设；受第 3 章规范约束 |
| 自动进化 | 不做；用人工改模板代替 |

---

## 8. 你若下周只做一件事

不要先做 Knowledge 引擎。做这个：

> **固定串行模板 + 强制 Artifact 清单 + 澄清等待人工 + 计划批准 + 测试按方案引用验收 ID。**

有了这五样，你的流水线才开始像「工程」；否则再细的 prompt 仍是 ai-loop 的亲戚。

---

## 文档信息

| 项 | 值 |
|----|----|
| 相关 | `build-pathway-playbook.md` · `project-status-plain-language.md` |
| 非目标 | 自动进化、完整 Scheduler、子 Agent 编排框架 |
