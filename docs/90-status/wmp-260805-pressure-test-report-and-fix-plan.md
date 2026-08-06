# ai4se-runtime 真仓真需求压测报告 + 完整修复手册

**未提交。** 本文档基于 2026-08-05 对 `dji-wmp-be`「260805-daily-demand-type-management」Story 1 的一次真实压测（真实 `claude` CLI 驱动，全程只读客户仓库，生成物落在 `ai4se-runtime/.pressure-test/` 一次性 workspace、从未 push、workspace 无 remote）。所有结论均基于本次运行产出的真实文件/代码/测试输出，不是推断。与 `wmp-ai-loop-replacement-gap-analysis.md` 是同一条主线的后续：前份文档建议项 #3「拿一个真实历史需求做试跑对照」，本文档就是那次试跑的**过程证据** + **治本修复计划**。

**怎么读：**

| 部分 | 用途 |
|---|---|
| §0 | 证据文档内的 Wave 计划（背景） |
| **[second-run-one-shot-repair-playbook.md](./second-run-one-shot-repair-playbook.md)** | **蓝图对齐 · 环节思想加固（落地以它为准；问题类级，禁 A→补丁 A）** |
| §1–§4 | 压测跑法与现象证据（过程总结，勿当施工顺序） |
| §5 / §8 | 历史清单（发现顺序堆积），**已被 §0 取代**；保留便于对照原文 |
| §6 | 压测机本地 diff 考古（最小补丁，落地时按 §0 重做，勿原样照抄） |
| §7 | 二次复核的根因分析（喂给 §0） |

**重要约束：** 压测机上的 `ai4se-runtime` 禁止提交/push——§6 的三处 diff 只是「能跑通压测」的最小补丁。本机基线落地请跟 **[第二次一次性跑通施工单](./second-run-one-shot-repair-playbook.md)**，并同步改契约文档。出现「需确认」字样，说明该处需自行查证。

---

## 0. 完整修复计划（治本 · 权威）

### 0.1 对这份文档的定性

这是一次**真仓 + 真需求 + 真 Claude CLI** 全链路压测的过程总结，证据扎实。但它原先的「落地清单」是按**发现顺序堆出来的**：§5 与 §8 重叠、Bug#1/#2/#3 的本地改法偏解析兼容、Verification「只测后端」被拆成消费侧与 onboard 两侧各说各话。

优化原则（硬约束）：

1. **先契约，后代码** — 角色看得见什么、产物长什么样，写进 Stage Contract / Package 契约；代码对齐契约，禁止「碰巧能跑」。
2. **禁止打补丁当修复** — 解析端兼容反引号/尾注、只在 `CommandArgv` 里藏一份 bash 解析、只改 Verify 消费侧不改 onboard，一律不算完成。
3. **一条根因一条 Wave** — 同源问题一次抽干净（共享原语、多调用点一起改、单测锁住）。
4. **契约文档同步入库** — 每个 Wave 结束时，对应 `docs/**` Contract / Runbook / Status 必须改到与代码一致。
5. **诚实字段不撒谎** — Verification 仍承认客户测试入口是神谕；不把 LLM/XML 解析伪装成「逐条 AC 已核对」。

对照产品宪法：Development Contract **已写** P1 含 Acceptance；代码没装进 Dev Package —— 这是**契约未落地**，不是新发明功能。

### 0.2 根因地图（现象 → 系统缺口）

```text
现象层（压测看到的）
├─ Allowed Files 门禁误杀          ──┐
├─ 只测了后端 / entries 缺前端     ──┤
├─ bash/WSL 假 FAIL、claude 找不到 ──┤
├─ Review 恒「通过」、Dev 不见 AC   ──┤
├─ KB 只写不读、ASSUMABLE 无声放行 ──┘
              │
              ▼
系统缺口（真正要修的）
├─ A. 执行环境原语缺失     Shell / Claude / preflight / 失败分类
├─ B. Plan 产物契约过松     Allowed 无 schema；解析在「猜」模型输出
├─ C. Context Package 契约  Dev/Review 包与 Stage Contract 不一致
├─ D. 仓库数字化不完整     onboard 单根互斥探测 → Verify 无前端入口
├─ E. Verification 语义    多命令 + env vs test 区分（保持诚实字段）
└─ F. 闭环与知识           Review Adapter、Session 审计、KB 读侧
```

### 0.3 Wave 施工顺序（完整修复 · 合理依赖）

> 验收口径：每个 Wave 有「代码完成定义」+「文档完成定义」。未改文档不算 Wave 完成。

#### Wave A — 执行环境原语（堵住假失败与烧 API）

**修什么：** Bug#3 同源、操作失误、§7.4 / §7.5。

| 项 | 做法（治本） | 禁止（补丁） |
|---|---|---|
| A1 | 新增共享 `ShellExecutable.resolve()`（建议 `ai4se-runtime-common` 或等价公共模块），跳过 Windows WSL stub；**所有** `ProcessBuilder("bash"…)` 调用点改用它（至少 `CommandArgv` + `OnboardRepoScript`，全仓 Grep 清零） | 只改 `CommandArgv` 私有方法；各处复制粘贴 |
| A2 | `ClaudeCliAdapter`：`AI4SE_CLAUDE_BIN` → 探测常见安装路径 → 明确失败；禁止静默裸 `"claude"` | 文档里写「记得 export」当修复 |
| A3 | `PathwayRunner` 入口 **preflight**：bash 可执行且非 stub；claude `--version`；`entries.yaml` 每条命令可解析到二进制；失败则**不进 Analysis** | 跑到 Analysis/Verify 才报环境错 |
| A4 | Control 层失败分类：`ENV_FAIL` / `ADAPTER_IO_FAIL`（有限次退避重试）vs `BUSINESS_FAIL` / `VERIFY_FAIL`（走既有 V4 / Stop） | IO 与业务 FAIL 一视同仁 STOPPED |

**代码完成定义：**

- `mvn -q -pl ai4se-orchestration,ai4se-context,ai4se-execution test` 绿（含原 36 个因裸 bash 炸的用例）。
- 单测：模拟 PATH 前置 WSL stub → 解析到真实 bash；preflight 缺 claude → 零次 Adapter invoke。

**文档完成定义：**

- 更新 `docs/40-ai-execution/adapter-contract.md`（或 README）：二进制解析、ENV vs IO 失败归属 Control。
- 更新 `docs/10-repository-intelligence/onboarding-runbook.md`：脚本经 `ShellExecutable` 调用说明。
- `docs/90-status/current-support-status.md`：记下 preflight 门闸已有。

---

#### Wave B — Plan Allowed Files 契约（治本 Bug#1/#2）

**修什么：** 根因是「模型输出格式无硬契约」，不是「解析不够宽容」。

| 项 | 做法（治本） | 禁止（补丁） |
|---|---|---|
| B1 | **Planning Contract / Plan 提示词 / `ContextPackagePrompt` Planning `roleExtra`**：Allowed Files **只能**是相对裸路径；禁反引号、禁尾注、禁中文夹叙 | 只靠解析端「尽量兼容」并宣称已修 |
| B2 | `PlanRecords`：**校验并拒绝**不合规条目（写/读两侧）；合规路径规范化（`\`→`/`、去 `./`） | 仅 `extractPath` 吞掉脏输入却不拒绝、不回写干净 Plan |
| B3 | `DiffScopeGuard.normalize`：只做路径规范化；**契约已保证** Allowed 侧无装饰符。可保留极薄防御，但单测主断言是「不合规 Plan 进不了 Dev」 | 把剥反引号当成唯一修复 |
| B4 | 回归：`` `a/b.java` ``、`a/b.java (new)`、合规裸路径 — 前两者在 Plan 门禁 FAIL；后者门禁 PASS |

**文档完成定义：**

- 改 `docs/30-delivery-orchestration/workflow/stages/planning-contract.md`：Output 明确 Allowed 行格式。
- 改 `docs/20-context-engineering/context-engineering-spec.md`（若有 Package 通例）：Plan slice 格式。
- 可选：`templates/` 下 Plan 样例改成合规格式。

---

#### Wave C — Context Package 对齐 Stage Contract（Dev / Review）

**修什么：** §7.1、Review fixture、P0-4 / P0#2 / P1-9。Development Contract **已要求** P1 含 Acceptance —— 代码必须跟上。

| 项 | 做法（治本） | 禁止（补丁） |
|---|---|---|
| C1 | `DevPackageBuilder`：P1 必含 `slices/acceptance.md`（来源唯一：`StoryRequirementReader`）；有 Gap/ASSUMABLE 时附 `slices/gap-ref.md`；附 `slices/plan-summary.md`（整份 `plan.md` 拷贝，避免二次解析） | Dev 靠「自己去翻仓库里的 requirement」 |
| C2 | `ContextPackagePrompt`：Development `roleExtra` 硬约束 Allowed + 遵守 acceptance；有 gap-ref 须遵循假设 | `roleExtra` 继续空字符串 |
| C3 | Review：**ReviewPackageBuilder** + `reviewAdapter` 挂上 `PathwayRunner`（与 analysis/plan/dev 同模式）；产物结构化 `AC1..ACn → 通过|不通过|证据`；写入 `ReviewRecords` | 继续硬编码「通过」；或只加 Adapter 不改包契约 |
| C4 | `reviewAdapter == null` 时：测试/fixture 路径必须**显式披露** `review_source: fixture`，禁止静默伪装真人/模型评审 | 无披露的假绿 |

**文档完成定义：**

- 确认并必要时加严 `docs/30-delivery-orchestration/workflow/stages/development-contract.md`（P1 已有 Acceptance，补「必须进入 Dev Package slices」）。
- 改 `docs/30-delivery-orchestration/workflow/stages/review-contract.md`：输出必须含 AC→证据表；输入包切片清单。
- 改 `docs/20-context-engineering/` 相关 Package 契约：三角色 P1 对齐表。

**代码完成定义：**

- 单测：`DevPackageBuilder.build` → acceptance 在 P1；fake Review Adapter 返回「不通过」→ Records 非「通过」；null adapter → `review_source: fixture`。

---

#### Wave D — 仓库数字化：多模块 entries（「只测后端」的根因）

**修什么：** §7.2 / P0-5；并承接原 P0#3 消费侧。

| 项 | 做法（治本） | 禁止（补丁） |
|---|---|---|
| D1 | `onboard-repo.sh`：根 + 一层子目录**累加**探测（maven/npm/gradle）；跳过 `target`/`node_modules` 等；生成多条 build/test | 互斥 if/elif；只手改某次压测的 `entries.yaml` |
| D2 | `VerificationControl`：**多条** test 命令全部 exit 0 才 PASS；任一条 FAIL → Defect（带上是哪条命令） | 只支持单 entry；或只改脚本不改 Control |
| D3 | 现场/压测复跑：根 `pom.xml` + `wmp-be-frontend/package.json` → `entries.yaml` 同时含 `mvn …` 与 `cd wmp-be-frontend && …` | 假定「人会记得写前端命令」 |

**文档完成定义：**

- 改 `docs/10-repository-intelligence/onboarding-runbook.md` + `repository-facts-contract.md`：多模块入口为必须事实。
- 改 `docs/50-verification/verification-contract.md`：多 entry 合取；报告须列出每条命令结果。

---

#### Wave E — Verification 语义诚实增强

**修什么：** §7.3；与产品诚实字段对齐。

| 项 | 做法（治本） | 禁止（补丁） |
|---|---|---|
| E1 | 区分 **环境/壳失败** vs **客户测试失败**（preflight + 命令启动失败 → `ENV_FAIL`，不进 V4 业务回环） | 把 WSL stub 失败当成「代码有 bug」走 Defect→Dev |
| E2 | 可选：Surefire/Failsafe XML 作为 **证据附件**写入 Report；`verdict_basis` 仍诚实（`customer_entry_exit_code` 或升级为 `customer_entries_all_exit_codes`）；**禁止**写成「已逐条 AC 评分」 | 用 XML/LLM 伪装 `acceptance_item_scoring` |
| E3 | Report 固定字段：每条 entry 的 cmd / exit / 日志指针；前端未配置时显式 `frontend_verify: not_configured`（若 Diff 含前端路径可升级为拒跑——需确认产品是否要硬闸） | 静默只跑后端却宣称 Verification 完整 |

**文档完成定义：**

- 改 `docs/50-verification/verification-contract.md`：多 entry、env vs test、证据附件 vs 神谕边界。
- 改 `docs/50-verification/defect-package-contract.md`：Defect 须带失败 entry 标识。

---

#### Wave F — 闭环与知识（主链后半段变真）

**修什么：** 蓝图半成品、§4 补测项、知识读侧、Session 审计。

| 项 | 做法（治本） | 禁止（补丁） |
|---|---|---|
| F1 | Analysis Package 读 `.ai4se/index/knowledge.yaml`（按 tags/refs）注入 P1/P2；Lifecycle 默认策略与现场披露一致（NOOP 必须写明 reason） | 只开 write 路径；或默默 NOOP 当「已有知识库」 |
| F2 | `SessionDecisionRecorder.onStageHop` 接入 `PathwayRunner` 阶段切换 | 只测不过主链 |
| F3 | **专门补测（不计入功能完成，计入签收）：** ① 真实业务缺陷走通 V4；② 跨 Story 知识累积；③ Review Adapter 判断质量 | 用环境假 FAIL 冒充 V4 已验 |

**文档完成定义：**

- 改 `docs/60-knowledge-lifecycle/`：读侧契约 + Analysis 消费点。
- 改 `docs/90-status/current-support-status.md` / playbook：哪些是 adapter_driven、哪些仍 fixture。

---

#### Wave G — 明确延期（不阻塞主链签收，但要立项）

| 项 | 说明 |
|---|---|
| G1 | `ACCEPTANCE` 是否升为 `WorkflowStage`：产品宪法现为「Delivery 之后人验收」。升枚举前先改 capability-map / workflow README，禁止代码先行 |
| G2 | 需求 seed 图片/原型图摄入：产品通道设计，非本次压测阻断项 |
| G3 | ASSUMABLE 软停人闸：默认关；契约写清开关与审计字段 |

### 0.4 推荐落地批次（给人排期）

| 批次 | Waves | 目标 |
|---|---|---|
| **批次 1（地基）** | A + B | Windows/环境假绿假红消失；Allowed 契约硬化；单测稳定 |
| **批次 2（主链诚实）** | C + D + E | Dev/Review 包契约对齐；多模块真测；Verify 语义诚实 |
| **批次 3（闭环）** | F | KB 读侧、Session 审计、三项专项补测 |
| **批次 4（产品）** | G | 人验收阶段模型、多媒体 seed、ASSUMABLE 软停 |

### 0.5 本次压测三项「已宣称修复」如何处置

| 压测机改动 | §0 处置 |
|---|---|
| §6.1 `CommandArgv` 内嵌 `resolveBashExecutable` | **作废为最终方案**；重做 Wave A1 共享原语 |
| §6.2 `PlanRecords.extractPath` 吞脏输入 | **降级为过渡**；重做 Wave B（拒绝 + 提示词契约） |
| §6.3 `DiffScopeGuard` 剥反引号 | **降级为防御层**；主修复在 B |

### 0.6 文档修改总表（每个 Wave 勾选）

| 文档 | A | B | C | D | E | F | G |
|---|---|---|---|---|---|---|---|
| `docs/40-ai-execution/adapter-contract.md` | ● | | | | | | |
| `docs/10-repository-intelligence/onboarding-runbook.md` | ● | | | ● | | | |
| `docs/10-repository-intelligence/repository-facts-contract.md` | | | | ● | | | |
| `docs/30-delivery-orchestration/workflow/stages/planning-contract.md` | | ● | | | | | |
| `docs/30-delivery-orchestration/workflow/stages/development-contract.md` | | | ● | | | | |
| `docs/30-delivery-orchestration/workflow/stages/review-contract.md` | | | ● | | | | |
| `docs/20-context-engineering/*`（Package 通例） | | ● | ● | | | | |
| `docs/50-verification/verification-contract.md` | | | | ● | ● | | |
| `docs/50-verification/defect-package-contract.md` | | | | | ● | | |
| `docs/60-knowledge-lifecycle/*` | | | | | | ● | |
| `docs/00-product/capability-map.md` | | | | | | | ●（若升 ACCEPTANCE） |
| `docs/90-status/current-support-status.md` | ● | ● | ● | ● | ● | ● | ● |
| 本文 §3 蓝图表（修完后回写现状列） | | | | | | ● | |

### 0.7 总验收（何时算「这些问题修完了」）

1. 无 WSL 的 Windows：preflight 过 → 全链路不因裸 bash/裸 claude 假 FAIL。  
2. 不合规 Allowed Plan：**进不了** Dev；合规 Plan + 真改 Allowed 内文件：DiffScope 不误杀。  
3. 多模块仓：onboard 产出前后端 test；Verify 两条都跑；缺前端入口且 Diff 含前端时有诚实披露或硬闸（按产品选定）。  
4. Dev Package P1 含 AC；Review 非静默 fixture（有 Adapter 或显式 `review_source: fixture`）；AC→证据可审计。  
5. 契约文档与代码一致；`current-support-status` 已改写「有/没有」。  
6. §4 三项专项补测至少留下可复查证据包（不必与功能同 PR，但要立项完成）。

---

## 1. 本次压测跑法摘要

| 项 | 内容 |
|---|---|
| **真仓** | `wmp-be-backend/` + `wmp-be-frontend/`（不含 `.git`，一次性 workspace 独立 `git init`，`git remote -v` 为空） |
| **真需求** | Story 1「日常需求类型管理列表」，脱敏后的 `requirement.md` 作为 seed |
| **驱动类** | `WmpPressureTestMain.java`（本机专用，不提交） |
| **结果** | 11 次尝试后 `COMPLETED`；其中 3 次因下文 Bug#1/#2/#3 失败，1 次因操作失误（`AI4SE_CLAUDE_BIN` 未在同一条 shell 会话里导出）失败 |
| **最终 commit** | `c7910e24e39129b087621ed54d27fddc49dd4e7b`（仅一次性仓库内，未 push） |

---

## 2. 本次暴露的产品级 Bug（按发现顺序）

### Bug#1 — `DiffScopeGuard.normalize()` 未剥离反引号包裹的路径（已修复）

- **现象：** Claude 撰写 `plan.md` 的 Allowed Files 用 `` `path/to/file.java` `` 形式的 Markdown 行内代码包裹路径时，与 `git status` 观测到的真实 diff 路径比较时判定「超出 Allowed Files」，即使 Dev 阶段实际只改了 Plan 声明过的文件。
- **修复：** `DiffScopeGuard.java` 的 `normalize()` 增加反引号剥离。
- **修复质量：** 防御性解析修复，未补回归测试。

### Bug#2 — `PlanRecords.readAllowedFiles()` / `extractPath()` 未处理路径后的尾注（已修复）

- **现象：** Claude 在 Allowed Files 条目最后追加 `(new)` 等中文/英文注释时，`extractPath()` 把注释一起当作路径的一部分，导致同样的「误判超出范围」。
- **修复：** 同一文件内改进路径提取逻辑，只取真正的路径 token。
- **修复质量：** 同 Bug#1，防御性、未补回归测试。Bug#1+#2 共同的根因是 Plan 撰写提示词模板没有约束 Claude 输出 Allowed Files 时必须用「裸路径、不加反引号、不加尾注」的格式——现在的修复是在解析端「尽量兼容各种写法」，属于治标；更优的做法是同时收紧上游提示词模板，从源头减少格式变体（见 5.P1#6）。

### Bug#3 — `CommandArgv.shellCommand()` 裸 `"bash"` 在 Windows 被 WSL 占位程序劫持（已修复，但确认不完整）

- **现象：** 本机同时存在 Git Bash 的 `bash.exe` 与 Windows 自带的 WSL-launcher 占位程序 `%WINDIR%\System32\bash.exe`。`ProcessBuilder` 解析裸命令 `"bash"` 时命中 WSL 占位路径；若未安装 WSL，该占位程序只打印「请到 Microsoft Store 安装 WSL」并以失败码退出。结果：`report-round-1.md` 显示 `FAIL` / `exit_code: 1`；Verification 阶段 `mvn test` 也因此假失败（实际业务测试本身可过）。
- **修复：** `CommandArgv.java` 增加 `resolveBashExecutable()`：遍历系统 `PATH`，显式跳过 `%WINDIR%\System32\bash.exe` 与 `%WINDIR%\Sysnative\bash.exe`，定位真正的 Git Bash。单测确认已指向 Git Bash。
- **⚠️ 新发现（修复不完整）：** 为验证追问而跑 `mvn -q -pl ai4se-orchestration test` 时发现：`OnboardRepoScript.java:54` 仍使用裸调用 `new ProcessBuilder("bash", script.toString(), workspace.toString())`，同样会被劫持。证据：本模块约 100 个单测中有 36 个失败（含 `PathwayRunnerIntegrationTest`、`SameRoleFlashResumeTest`、`AdapterDoesNotAdvanceWorkflowTest` 等）。这是既有缺陷，被本次压测环境暴露。
- **修复质量结论：** 识别真实 shell 的逻辑正确，但应用面过窄（只改了一处调用点）。应抽成共享工具（如 `ShellExecutable.resolve()`），让 `CommandArgv` 与 `OnboardRepoScript` 共用；并补一条「无 WSL 的 Windows」专项回归（见 5.P0#1）。

### 操作失误（非产品 Bug，记录以防复现）

- **问题：** `AI4SE_CLAUDE_BIN` 为必填环境变量。
- **细节：** `mvn exec:java` 必须在同一 shell 会话里 `export`；后台 Bash 工具不共享该状态。
- **建议：** 改进 `ClaudeCliAdapter`，增加更聪明的 fallback——自动探测常见 Windows Node 全局 bin 路径（如 `%APPDATA%\npm\claude.cmd`、`nodejs\node-v*\claude.cmd`），而不是默认回落到裸 `"claude"`（见 5.P2#8）。

---

## 3. 对照蓝图「摸底库 → 生成知识库 → 基于知识库摸底需求 → 问题澄清 → 生成规格 → 开发 → 测试 → 验收」的能力现状

| 蓝图阶段 | 现状 | 证据 |
|---|---|---|
| **摸底库** | ✅ **真实。** `Analysis Adapter` 按 Story 现场扫描仓库，产出详细的 `discovery.report.md`（含 DDD 分层、分发文件实际删除逻辑等）。 | `discovery.report.md` §1–9；`gap.report.md` 交叉索引 |
| **生成知识库** | ⚠️ **半成品。** 基建存在（`KnowledgeLifecycleControl` + `.ai4se/index/knowledge.yaml` + `.ai4se/learning/*.md`），但：(1) 目前单向——learning 只在 Review/Acceptance 之后，且本次调用设为 `LifecycleMode.NOOP`；(2) 代码检索确认没有任何组件把 KB 回灌到 Analysis/Gap 阶段的 prompt 包。当前流程与历史 KB 脱节，仅依赖实时扫描。 | `KnowledgeLifecycleControl.java`；`WmpPressureTestMain.java:91`（`NOOP`）；全局搜 `knowledge.yaml` / `learning/` 仅出现在写路径 |
| **基于知识库摸底需求（Gap 分析）** | ✅ **分析本身真实。** `gap.report.md` 识别出 5 条 Unknown → Assumption → Risk 结构化项。`gap_status=ASSUMABLE`、`blocking_gap_count=0` 是 Claude 动态判断，非硬编码，已经过人审。⚠️ `PathwayRunner` 对 `ASSUMABLE` 与 `CLEAR` 同等放行，仅 `BLOCKED` 才停。 | `PathwayRunner.java:209-236`；`GapStatus.java` |
| **问题澄清** | ✅ 机制真实存在（`GapStatus.BLOCKED` → 写 `clarification.pending.md` → `StoryWorkflowMachine.stop`）。本次无需触发是真实判断，不是跳过。⚠️ 完全没有原型图/图片摄入通道——需求 seed 只支持纯 Markdown 文本。 | `PathwayRunner.java:181-206`；`requirement.md` 格式 |
| **生成规格文件（Plan）** | ✅ **真实**（Plan Adapter + Allowed Files diff-scope 强制校验）。本次两个解析层 Bug 就出在这条链上。 | 见 Bug#1 / #2 |
| **开发 agent 执行** | ✅ **真实。** 确认 Claude 写入 28 个业务文件（后端 DDD 分层约 14 + 前端 Vue/TS 约 9 + 测试文件）。`-dangerously-skip-permissions` 仅用于一次性 workspace。 | `find ... -newer manifest.md` 结果 |
| **测试 agent 测试（Verification）** | ⚠️ **只验证了后端。** `entries.yaml` 的 `test` 仅有 `mvn test -Dtest=*DemandType*` 与 `mvn -q test`，纯 Maven/后端。前端 `wmp-be-frontend/package.json` 里已有的 `vitest run`、`vue-tsc -b`、`eslint` 从未被调用——9 个前端文件（含 2 个 `.test.ts`）甚至未做编译校验。未配置/未跑 E2E。`VerificationControl` 的 PASS/FAIL 依据是单条 entry 命令的 exit code（`verdict_basis=customer_entry_exit_code`）。 | `entries.yaml`；`wmp-be-frontend/package.json` scripts |
| **验收 agent 验收交付质量** | ❌ **完全是 fixture。** `PathwayRunner.Config` 无 `reviewAdapter` 字段；`ReviewRecords.write()` 直接写入常量「通过」；本次 Human Acceptance 明确配成 FIXTURE（「无真实 human/agent reviewer 执行验收」）。`WorkflowStage` 枚举只有 `ANALYSIS / PLANNING / DEVELOPMENT / VERIFICATION / REVIEW / DELIVERY`，没有 `ACCEPTANCE`——验收甚至不是状态机一等阶段，只是 Delivery 完成后的旁路记录（`HumanAcceptanceRecords`）。 | `PathwayRunner.java:313, 551`；`WorkflowStage.java:5-10` |

### 一个额外的好消息：会话隔离已经天然满足

1. `ClaudeCliAdapter.buildArgv()` 构造的命令行从不携带 `--resume` / `--continue` 或任何 session ID。对 Claude CLI，Analysis / Planning / Development /（未来）Review 的每次 Adapter 调用都是全新、无记忆的独立进程——不存在「续上下文」的技术路径。这对所有角色成立，不仅是 Review。
2. `SessionDecisionRecorder.java` 是另一本审计账（把角色切换的 resume/new 记到 `.story/<id>/sessions/*.md`），但 `PathwayRunner.run()` 主流程从未调用它（全文检索零命中）——写了、测了，但未接入主线。
3. **结论：** 接入 Review Adapter 时，「断上下文」不需要额外设计（`ClaudeCliAdapter` 天然保证）。建议把 `SessionDecisionRecorder.onStageHop()` 接到每次阶段切换，把「设计如此」变成「可审计账本上的确实是新会话」，为将来可能支持 session 续接的 Adapter 留一道防线。

---

## 4. 本次压测没有测到、需要专门补测的问题

1. **真实业务缺陷下的 V4 Defect Loop 效果：** 本次唯一触发的 `Verify FAIL` 是 Bug#3 造成的假环境失败；修好后一次就过。`Verify FAIL → DefectPackageWriter → returnToDevelopment → 真实第二轮 Dev 修复` 这条链，从未在「Claude 写出的代码真有业务 bug」场景下验证过。建议专门做一次「故意让 Dev 产出真实业务缺陷」的补测。
2. **知识回写的真实累积效果：** 本次为 `NOOP`，`applyLearning()` 路径完全未执行。知识能否跨多个 Story/多轮真正累积，尚未验证。
3. **Review Adapter 接入后的判断质量：** 当前 Review Adapter 不存在。接入后，在「仅 diff + AC + verify report」输入下 Claude 的判断质量需要专项验证。

---

## 5. 历史落地清单（发现顺序堆积 · 已被 §0 取代）

> **勿按本节施工。** 权威顺序见 **§0 Wave A–G**。本节保留，便于对照压测机原文与 §7/§8 编号。

### P0 — 结构性缺口，建议先做

1. **补齐 Bug#3 同源问题：** 把 `resolveBashExecutable()` 提炼成共享工具（如 `ShellExecutable`），`CommandArgv` 和 `OnboardRepoScript` 都改用它；补一个模拟「无 WSL 分发版环境」的回归测试。这个改完，本机 `ai4se-orchestration` 的 36 个现存测试错误应该会消失，可以作为验收标准。

2. **接入 Review Adapter：** `PathwayRunner.java` 里 `Config` / `Config.Builder` 已经有三套几乎一模一样的字段可以直接照抄（约第 503–506、618–623、774–789 行）：

```java
// 字段声明（Config 类内，约第 503–506 行）
public final ModelCliAdapter devAdapter;
public final ModelCliAdapter analysisAdapter;
public final ModelCliAdapter planAdapter;
public final Duration adapterTimeout;

// Builder 字段（约第 618–623 行）
private ModelCliAdapter devAdapter;
private ModelCliAdapter analysisAdapter;
private ModelCliAdapter planAdapter;
private Duration adapterTimeout;

// Builder 方法（约第 774–789 行）
/** W4-on-spine: Development submits built Package to this Adapter once. */
public Builder devAdapter(ModelCliAdapter adapter) {
    this.devAdapter = adapter;
    return this;
}

/** Analysis Package → Adapter once → must leave discovery.report|skip. */
public Builder analysisAdapter(ModelCliAdapter adapter) {
    this.analysisAdapter = adapter;
    return this;
}

/** Planning Package → Adapter once → must leave plan.md with Allowed. */
public Builder planAdapter(ModelCliAdapter adapter) {
    this.planAdapter = adapter;
    return this;
}
```

照这个模式再加一套 `reviewAdapter`（字段 + Builder 字段 + Builder 方法）。然后改 `run()` 里 REVIEW 阶段目前硬编码的（约第 313 行）：

```java
ReviewRecords.write(workspace, storyId, config.reviewDecision, config.reviewResidualRisk);
```

改为：若 `config.reviewAdapter != null`，参照 `VerifyPackageBuilder` / `DevPackageBuilder` 模式构造 **Review Context Package**（P1：至少含全量 diff、`slices/acceptance.md`、最新 `report-round-N.md`），调用 `config.reviewAdapter.invoke(...)`，解析输出的 decision / residual risk，再写 `ReviewRecords`；若 `reviewAdapter == null`，保持现有 fixture 行为以兼容旧用例。

- **验收：** 新增用例，用 fake `ModelCliAdapter` 作 `reviewAdapter`，固定返回「不通过」+ 某段 residual risk 文本，断言 `ReviewRecords` 写入的是 Adapter 返回值而非硬编码「通过」；同时验证 `reviewAdapter` 留空时旧行为不受影响（回归）。
- **输出格式建议：** Review Adapter 输出做成结构化：`AC1..ACn → 通过/不通过/证据` 映射。AC 字段名来自 `StoryRequirement.acceptance()`，不要只返回一句简单的「通过/不通过」，否则后续 UI 难呈现。

3. **Verification 支持前端 / e2e：** `entries.yaml` / 驱动类需能声明多条 verify 命令（后端单测 + 前端 `vue-tsc` / `vitest` + 可选 e2e）。`VerificationControl` 需支持「多条命令全部 PASS 才算成功」，而不是单 entry 模型。涉及前端交互的需求至少要配置一条前端 `build` / type-check 命令。

   > **已被 7.2 / §8 P0-5 修正根因表述：** 只改消费侧不够——`onboard-repo.sh` 当前 root-only + 互斥探测才是「只测后端」的根因；onboard 生成多条命令与消费侧多命令支持必须一起做。

### P1 — 能力增强

4. **打通知识库读侧：** `AnalysisPackageBuilder` 打包 Analysis 上下文时，按 `tags` / `refs` 检索 `.ai4se/index/knowledge.yaml` 相关 learning 条目，喂给 Analysis Adapter，让「基于知识库摸底需求」真正成立。

5. **Gap Assumption 可见性：** 给 `ASSUMABLE` 增加可选的「软停」提示——不是硬 STOP，但要求人工确认已阅读 `gap.report.md` 再进入 Planning（可默认关闭，避免低风险场景每次都要人闸）。

6. **收紧 Plan 撰写提示词模板：** 明确要求 Allowed Files 使用裸路径、不加反引号、不加尾注，从源头减少 Bug#1/#2 复发。

### P2 — 体验 / 鲁棒性

7. **给 Bug#1/#2/#3 各补一个单元测试。** 此前用一次性手写 repro 脚本验证过又删了，缺少回归保护。

8. **`AI4SE_CLAUDE_BIN` 未设置时：** 增加更智能的 Windows 常见安装路径探测。

9. **把 `ACCEPTANCE` 纳入 `WorkflowStage` 枚举：** 做成状态机一等阶段，而不是 Delivery 之后的旁路记录。

10. **视产品方向决定是否给需求 seed 增加图片 / 原型图附件摄入能力。** 当前完全不支持。

---

## 6. 重要：这台云桌面上的 `ai4se-runtime` 也不能提交——以下是需要手动搬回 git 基线的 3 处改动原始 diff

本机 `ai4se-runtime` 同样是禁止提交 / push 的工作副本。Bug#1/#2/#3 的修复目前只以本机未提交本地改动存在。Cursor 在另一台机器上只有原始 git 基线，不会自动同步到这些改动——请按下列 diff 手动搬回（6.4 为尚未写代码的同源缺口；6.5 为其它未跟踪文件核对清单）。

### 6.1 `ai4se-orchestration/src/main/java/com/ai4se/orchestration/support/CommandArgv.java`

```diff
--- a/ai4se-orchestration/src/main/java/com/ai4se/orchestration/support/CommandArgv.java
+++ b/ai4se-orchestration/src/main/java/com/ai4se/orchestration/support/CommandArgv.java
@@ -2,6 +2,7 @@ package com.ai4se.orchestration.support;
 
 import com.ai4se.orchestration.analysis.StageGateException;
 import com.ai4se.runtime.common.util.Strings;
+import java.io.File;
 import java.util.Arrays;
 import java.util.List;
 
 /**
  * Builds process argv for observed gates.
  * Customer entry commands run under {@code bash -lc} (already allowlisted by entries).
  * Git observations use fixed argv (no shell).
  */
 public final class CommandArgv {
 
+    private static final String BASH_EXECUTABLE = resolveBashExecutable();
+
     private CommandArgv() {
     }
 
     /** Entry / verify command — shell form, gated by VerificationEntries beforehand. */
     public static List<String> shellCommand(String command) {
         if (Strings.isBlank(command)) {
             throw new StageGateException("Command required");
         }
-        return Arrays.asList("bash", "-lc", command.trim());
+        return Arrays.asList(BASH_EXECUTABLE, "-lc", command.trim());
     }
+
+    /**
+     * Windows ships stub {@code bash.exe} launchers under System32 / Sysnative that only
+     * prompt to install WSL. ProcessBuilder PATH lookup may hit those stubs before a real
+     * bash (e.g. Git Bash). Walk PATH and skip the known stubs.
+     */
+    private static String resolveBashExecutable() {
+        String path = System.getenv("PATH");
+        if (path == null) {
+            return "bash";
+        }
+        String windir = System.getenv("WINDIR");
+        String stub1 = windir == null ? null : new File(windir, "System32\\bash.exe").getAbsolutePath();
+        String stub2 = windir == null ? null : new File(windir, "Sysnative\\bash.exe").getAbsolutePath();
+        for (String dir : path.split(File.pathSeparator)) {
+            if (dir.isEmpty()) {
+                continue;
+            }
+            File candidate = new File(dir, "bash.exe");
+            if (!candidate.isFile()) {
+                continue;
+            }
+            String candidatePath = candidate.getAbsolutePath();
+            if (candidatePath.equalsIgnoreCase(stub1) || candidatePath.equalsIgnoreCase(stub2)) {
+                continue;
+            }
+            return candidatePath;
+        }
+        return "bash";
+    }
```

> **不完整**——落地时应把 `resolveBashExecutable()` 提炼成共享工具类，同时改掉下面 6.4 节的 `OnboardRepoScript.java:54`，不要原样照抄这个 diff（这只是「能跑通压测」的最小修复）。

### 6.2 `ai4se-orchestration/src/main/java/com/ai4se/orchestration/analysis/PlanRecords.java`

```diff
--- a/ai4se-orchestration/src/main/java/com/ai4se/orchestration/analysis/PlanRecords.java
+++ b/ai4se-orchestration/src/main/java/com/ai4se/orchestration/analysis/PlanRecords.java
@@ -67,7 +67,7 @@ public final class PlanRecords {
                 continue;
             }
             if (inAllowed && (t.startsWith("- ") || t.startsWith("* "))) {
-                String file = t.substring(2).trim();
+                String file = extractPath(t.substring(2).trim());
                 if (!file.isEmpty()) {
                     allowed.add(file);
                 }
@@ -87,6 +87,20 @@ public final class PlanRecords {
         readAllowedFiles(workspace, storyId);
     }
 
+    /** Bullet text may be a bare path or a backtick-quoted path with trailing annotation, e.g. {@code `a/b.java` (new) - note}. */
+    private static String extractPath(String bulletText) {
+        String t = bulletText.trim();
+        if (t.startsWith("`")) {
+            int end = t.indexOf("`", 1);
+            if (end > 0) {
+                return t.substring(1, end).trim();
+            }
+        }
+        return t;
+    }
+
     private static List<String> normalizeAllowed(List<String> allowedFiles) {
```

### 6.3 `ai4se-orchestration/src/main/java/com/ai4se/orchestration/development/DiffScopeGuard.java`

```diff
--- a/ai4se-orchestration/src/main/java/com/ai4se/orchestration/development/DiffScopeGuard.java
+++ b/ai4se-orchestration/src/main/java/com/ai4se/orchestration/development/DiffScopeGuard.java
@@ -50,7 +50,12 @@ public final class DiffScopeGuard {
 
     static String normalize(String path) {
-        String p = path.trim().replace('\\', '/');
+        String p = path.trim();
+        if (p.length() >= 2 && p.startsWith("`") && p.endsWith("`")) {
+            p = p.substring(1, p.length() - 1).trim();
+        }
+        p = p.replace('\\', '/');
         while (p.startsWith("./")) {
             p = p.substring(2);
         }
         return p;
     }
```

### 6.4 尚未修复、只在本文档 5.P0#1 里描述、还没写代码的部分

- **位置：** `ai4se-context/src/main/java/com/ai4se/context/onboard/OnboardRepoScript.java:54`
- **现状：** 仍是 `new ProcessBuilder("bash", script.toString(), workspace.toString())`，未改。
- **动作：** 留给 5.P0#1 一并处理——把 `resolveBashExecutable()` 提炼成共享 `ShellExecutable`，`CommandArgv` 与 `OnboardRepoScript` 都改用它。**不要只把 6.1 的 diff 搬过来就完事。**

### 6.5 其它未提交的本地改动（核对用，勿误提交）

压测机 `git status --porcelain` 大意如下（未执行过 `git add` / `git commit`）：

```text
 M ai4se-orchestration/src/main/java/com/ai4se/orchestration/support/CommandArgv.java
 M ai4se-orchestration/src/main/java/com/ai4se/orchestration/analysis/PlanRecords.java
 M ai4se-orchestration/src/main/java/com/ai4se/orchestration/development/DiffScopeGuard.java
?? .pressure-test/
?? ai4se-demo/src/main/java/com/ai4se/runtime/demo/pathway/WmpPressureTestMain.java
?? docs/90-status/wmp-260805-pressure-test-report-and-fix-plan.md
?? docs/90-status/wmp-ai-loop-replacement-gap-analysis.md
```

| 项 | 处理 |
|---|---|
| 三个 `M` 文件 | 对应 6.1 / 6.2 / 6.3，diff 已全文列出；搬回基线时按 diff 落地 |
| `.pressure-test/` | 一次性压测 workspace，测完后手动删除，**不要提交** |
| `WmpPressureTestMain.java` | 本机压测专用驱动类，**不要提交** |
| 本报告 + `wmp-ai-loop-replacement-gap-analysis.md` | 同批文档；是否入库由产品决定 |

`.pressure-test/` 内部的 `git init` / commit 与 `ai4se-runtime` 主仓完全独立，无 remote。

---

## 7. 二次复核追加发现（按证据强度排序，前序文档未写）

每条标明「具体代码修复」或「需要调整契约设计」。

### 7.1 【需要调整契约设计，不是打补丁】Development 是全流程唯一不携带验收标准的阶段

- **证据（生产侧）：** `AnalysisPackageBuilder.java:52-58` 与 `PlanningPackageBuilder.java:46-51` 都会把验收标准写入 `slices/acceptance.md` 并列入 P1。

  > **路径警告：** 包路径是 `ai4se-context`，**不是** `ai4se-orchestration`。落地前请 Glob 核实，不要只信正文（作者本人曾写错过路径）。

```java
// AnalysisPackageBuilder.java:52-58（示意）
Path acceptanceSlice = slices.resolve("acceptance.md");
Files.write(acceptanceSlice, renderAcceptance(requirement).getBytes(StandardCharsets.UTF_8));
p1.add("slices/acceptance.md");
```

- **消费方：**
  - `VerificationControl.java` 会读并判断该 slice 是否存在。
  - 但 `DevPackageBuilder.java`（约 200 行）的 P1 列表只有 `slices/allowed-files.md` 与 `slices/diff-ref.md`，**从不创建或引用** `slices/acceptance.md`；`gap.report.md` 也未进入 Dev 包。
  - `ContextPackagePrompt.java` 只给 Analysis / Planning 填了 `roleExtra`；Development 角色落成空字符串。

- **为什么是契约问题而不是补丁：** Context Package 故意限制每个角色可见信息（控 token、可审计）。某角色在某步「能看见什么」必须是硬契约，不能指望 Agent「碰巧去读完整 `plan.md`」。

- **权威来源：** 在 `PlanRecords.java` 搜 `acceptance` / `AC`——Plan 阶段本身不生产、不存储 AC。唯一权威来源是：

  `com.ai4se.context.story.StoryRequirementReader.read(workspace, storyId)` → `requirement.acceptance()`

  `DevPackageBuilder` 应复用同一路径。`ai4se-orchestration` 已依赖 `ai4se-context`，无需改 POM。

- **建议修复：**

  1. 在 `DevPackageBuilder.build()` 写完 `allowed-files.md` 之后，插入：

```java
StoryRequirement requirement = StoryRequirementReader.read(workspace, storyId);
StringBuilder accBody = new StringBuilder("# Acceptance (P1)\n\n");
for (String a : requirement.acceptance()) {
    accBody.append("- ").append(a).append('\n');
}
Files.write(dir.resolve("slices/acceptance.md"),
        accBody.toString().getBytes(StandardCharsets.UTF_8));
```

     然后把 `slices/acceptance.md` 加入 `p1`（建议紧跟 `diff-ref.md` 之后），并把其字节数计入 `baseBytes`。

  2. **`slices/plan-summary.md`：** 设计决策散落在完整 `plan.md` 里；最稳妥是整份拷贝，避免再解析引入 bug。`PlanRecords` 已有路径方法：

```java
Path planFile = PlanRecords.planningDir(workspace, storyId).resolve(PlanRecords.PLAN_FILE);
Files.copy(planFile, dir.resolve("slices/plan-summary.md"), StandardCopyOption.REPLACE_EXISTING);
```

  3. **`slices/gap-ref.md`：** 仅当存在 `gap.report.md`（例如 `gap_status == ASSUMABLE`）时整份拷贝；`CLEAR` 或不存在则不要创建该文件，避免噪声。

  4. **`ContextPackagePrompt.java`：** 为 Development 角色补齐 `roleExtra`（结构对齐 Analysis/Planning），并严格强调 Allowed Files 边界。

- **验证标准：** 单测调用 `DevPackageBuilder.build(workspace, storyId)` 后断言：`slices/acceptance.md` 存在且含每条 AC 原文；路径出现在 `manifest.md` 的 `priority1` 列表。`ContextPackagePrompt` 的 Development `roleExtra` 应要求：遵守 `slices/acceptance.md` 每条 AC；若存在 `slices/gap-ref.md` 须遵循其假设。

### 7.2 【具体代码修复，证据最扎实】`onboard-repo.sh` 构建探测是 root-only + 互斥优先级，结构性识别不了多语言仓库

- **证据：** `scripts/onboard-repo.sh:215-239` 只看根目录，`if/elif/elif` 互斥单选（`write_maven_baseline()` 第 36–106 行本身是通用逻辑，不是硬编码 yudao——纠正此前一版猜测）：

```bash
if [[ -f "$ROOT/pom.xml" ]]; then
  echo "build:"; echo "  - mvn -q -DskipTests package"; echo "test:"; echo "  - mvn -q test"
elif [[ -f "$ROOT/package.json" ]]; then
  echo "build:"; echo "  - npm run build"; echo "test:"; echo "  - npm test"
elif [[ -f "$ROOT/build.gradle" || -f "$ROOT/build.gradle.kts" ]]; then
  echo "build:"; echo "  - ./gradlew assemble"; echo "test:"; echo "  - ./gradlew test"
else
  echo "build: unknown"; echo "test: unknown"
fi
```

本次压测 workspace 根目录有 `pom.xml`，但 `package.json` 在子目录 `wmp-be-frontend/`——现有结构无法同时识别两者，这才是「只测了后端」的根因。`OnboardRepoScript.java` 只是调用该脚本，不必改 Java，改 shell 即可。

- **建议修复（累加而非短路，扫根目录 + 一层子目录）：**

```bash
build_lines=(); test_lines=()
detect_dir() {
  local dir="$1" prefix="$2"
  if [[ -f "$dir/pom.xml" ]]; then
    build_lines+=("  - ${prefix}mvn -q -DskipTests package"); test_lines+=("  - ${prefix}mvn -q test")
  elif [[ -f "$dir/package.json" ]]; then
    build_lines+=("  - ${prefix}npm run build"); test_lines+=("  - ${prefix}npm test")
  elif [[ -f "$dir/build.gradle" || -f "$dir/build.gradle.kts" ]]; then
    build_lines+=("  - ${prefix}./gradlew assemble"); test_lines+=("  - ${prefix}./gradlew test")
  fi
}
detect_dir "$ROOT" ""
for sub in "$ROOT"/*/; do
  sub="${sub%/}"
  [[ "$(basename "$sub")" == "target" || "$(basename "$sub")" == "node_modules" ]] && continue
  detect_dir "$sub" "cd $(basename "$sub") && "
done
if [[ ${#build_lines[@]} -eq 0 ]]; then
  echo "build: unknown"; echo "test: unknown"
else
  echo "build:"; printf '%s\n' "${build_lines[@]}"
  echo "test:"; printf '%s\n' "${test_lines[@]}"
fi
```

> 这段逻辑**替换**第 5 节 P0#3「只让消费侧支持多条 verify 命令」的表述——两边都要：若 onboard 不生成前端命令，消费侧多命令也无输入。落地时按目标仓实际目录名调整 `target` / `node_modules` 跳过列表。

- **验证标准：** 对本 workspace（根 `pom.xml` + `wmp-be-frontend/package.json`）重跑脚本后，`entries.yaml` 的 `test:` 应同时出现 `mvn -q test` 与 `cd wmp-be-frontend && npm test`。

### 7.3 【澄清，纠正第 3 节表述】Verification 的 PASS/FAIL 判据是单一退出码，不解析测试报告

`verdict_basis=customer_entry_exit_code`——Bug#3 把「11/11 实际通过」误判为 FAIL，正是因为只看进程退出码、不看测试报告内容。退出码表示「命令是否失败」，不等于「测试本身是否通过」（例如覆盖率插件配置错误导致 `mvn test` 非零退出也会被误判）。

- **建议：** `VerificationControl` 支持可选的结构化报告解析（先做 Surefire / Failsafe XML），退出码降级为兜底信号。

### 7.4 【具体代码修复】Adapter 层的 IO 级瞬时故障没有重试，直接打断整条流程

操作失误（忘记 export `AI4SE_CLAUDE_BIN`）触发：`Claude CLI IO error: Cannot run program "claude" ... CreateProcess error=2`。这类「进程根本没启动起来」的 IO 异常，与「进程正常返回但 Verify FAIL」本质不同。

当前 `PathwayRunner` / Adapter 对两者一视同仁：直接 STOPPED（注：无 Adapter 重试；Control 拥有恢复权）。恢复路径变成人工发现 + 手动重跑整条环，与无人值守目标相悖。

- **建议：** 区分两类失败。Adapter 抛出的 IO / 进程启动异常，由 Control 层有限次自动重试（例如 3 次指数退避）；Adapter 正常返回的业务级 FAIL 维持现状（交给 V4 回环，不重试）。

### 7.5 【具体代码修复，成本很低】缺一道 preflight 自检，环境问题会浪费真实 API 调用

11 次尝试里，Bug#3（WSL 占位程序）与操作失误（`claude` 命令找不到）本质都是「环境没配好」，却要等到真正调用 Claude CLI 才发现——越晚失败，浪费的 API 调用越多（Bug#3 在 Verify 才爆；找不到 `claude` 在 Analysis 就爆）。

- **建议：** 在 `PathwayRunner.run()` / 驱动入口加一道轻量 preflight：
  1. `resolveBashExecutable()` 结果确实可执行，且不是 WSL 占位程序；
  2. 配置的 `claude` 可执行路径跑通 `--version`；
  3. `entries.yaml` 里每条命令的可执行文件能在 `PATH` 中找到。

全部通过才进入 Analysis；否则提前报错退出，不消耗真实 API 调用。

---

## 8. 历史补充清单（二次复核编号 · 已被 §0 取代）

> **勿按本节单独施工。** 与 §5 合并映射到 §0：P0-4→Wave C；P0-5→Wave D；P0-6→Wave A3；P1-7→Wave E；P1-8→Wave A4；P1-9→Wave C3。

| 编号 | 内容 | 优先级 | 类型 |
|---|---|---|---|
| **P0-4** | `DevPackageBuilder` 补 `slices/acceptance.md` + `slices/gap-ref.md`；`ContextPackagePrompt` 给 Development 补 `roleExtra`（见 7.1） | P0 | 契约设计调整 |
| **P0-5** | `onboard-repo.sh`（+ `OnboardRepoScript.java`）支持扫根目录 + 一层子目录；多构建系统累加而非互斥（见 7.2；**替换**原 P0#3「只让消费侧支持多命令」表述） | P0 | 具体代码修复（根因） |
| **P0-6** | 增加 preflight 自检闸：进 Analysis 前校验 bash / claude / `entries.yaml` 命令可执行性，避免浪费真实 API（见 7.5） | P0 | 具体代码修复，成本低、回报高 |
| **P1-7** | `VerificationControl` 支持结构化测试报告解析（先做 Surefire / Failsafe XML）；退出码降级为兜底信号（见 7.3） | P1 | 具体代码修复 |
| **P1-8** | Adapter IO / 进程启动异常与业务 FAIL 分流；前者有限次重试（见 7.4） | P1 | 具体代码修复 |
| **P1-9** | Review Adapter 输出结构（对应原 P0#2）应带明确的 `AC1..ACn → 证据` 映射字段，不只是「通过/不通过」——呼应 `wmp-ai-loop-replacement-gap-analysis.md` 优先级 #5 的 AC 可追溯性检查。当前全仓搜 `acceptance.criteria` / `AC[0-9]` / `traceab` 等命中情况需落地时再核 | P1 | 具体代码修复（可追溯性） |

---

## 附录：与前序文档关系

- 前序：`wmp-ai-loop-replacement-gap-analysis.md`（建议项 #3：真实历史需求试跑对照）
- 本文：该试跑的结果报告 + 修复/完善/优化落地计划（完整修复手册）
- 相关水位：`current-support-status.md`、`build-pathway-playbook.md`、仓库根 `PATHWAY-VERIFICATION-HANDBOOK.md`

---

本文档基于 2026-08-05 一次真实压测运行的文件/代码证据生成，未修改客户仓库任何文件，未 push 任何内容。**落地施工跟 [second-run-one-shot-repair-playbook.md](./second-run-one-shot-repair-playbook.md)。** 每完成一个 Must，回写 `current-support-status.md` 与本文 §3——不要把本文当成永不失效的长期结论。
