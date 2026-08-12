# M1 · 单 Story 真实无人值守闭环施工方案

> 目标：把现有控制面、Context Package、Adapter、Verification 零件收敛成一个真实产品入口。  
> 本方案不是扩建八域；八域继续作为能力归属地图，施工只围绕一条纵向主链。

## 1. 唯一目标

一条正式命令在真实 Git 仓库中完成：

```text
Story requirement
  → Analysis Adapter
  → Planning Adapter
  → deterministic plan gate
  → Development Adapter
  → customer verification
      FAIL → Defect Package → fresh Development round → verify again
      PASS → Review Adapter
  → local commit (never push)
  → AWAITING_HUMAN_ACCEPTANCE
```

M1 不承诺多 Story 队列、并行 agent、远程 sandbox、自动合并、知识自动晋升或分布式恢复。

## 2. 参考开源项目的规则：借机制，不搬架构

任何外部设计进入本仓前，必须填写一条 `ReferenceDecision`：

| 字段 | 含义 |
|---|---|
| `observed_problem` | 本仓至少一次真实运行中出现的可复现问题 |
| `borrowed_mechanism` | 借用的最小机制，不写“参考某项目整体架构” |
| `local_owner` | 归属 01–08 哪个现有能力域 |
| `rejected_parts` | 明确没有借哪些部分 |
| `acceptance_test` | 什么测试或真实证据证明机制有效 |
| `removal_condition` | 无效或重复时何时删除 |
| `source_and_license` | 来源、版本/commit、许可证；复制代码时必须保留归属 |

没有 `observed_problem` 和 `acceptance_test`，只允许记录到调研文档，不得进入代码。

当前允许借鉴：

| 来源 | 只借什么 | 不借什么 |
|---|---|---|
| Ralph | 文件/Git 作为跨轮次状态；每个修复轮次使用新鲜 agent context | PRD 产品模型、无限 shell loop、DONE 文本作为完成证明 |
| Oh My Pi | harness 质量决定成功率；精简输入、结构化工具、减少编辑重试 | 在本仓重造 hashline、LSP、DAP、browser、subagent runtime |
| OpenHands | headless/结构化事件输出适合自动化 | always-approve 直接运行在宿主机 |
| Open SWE | 每任务隔离、异步状态、完成后产出 PR/commit 的产品形态 | Slack/Linear/GitHub App、LangGraph Cloud、并行 subagent（M1 不需要） |

结论：AI4SE 的原创边界是 **Stage Contract + Context Package + deterministic Control + evidence-gated Verification**；agent harness、sandbox、Git 和模型调用尽量复用，不自行重造。

## 3. 蓝图能力判定

### 3.1 M1 可以真正做到

| 能力 | 当前基础 | M1 动作 |
|---|---|---|
| Repository onboarding | 已能生成 `.ai4se/`、`.story/`、entries | 保留最小能力；只修真实仓入口识别问题 |
| Story + Acceptance | 已有 opener、reader、拒跑门禁 | 成为正式 CLI 的必需输入 |
| Context Package | Analysis/Plan/Dev/Verify/Review Builder 已有 | 统一由生产主链调用，禁止绕过 |
| Deterministic workflow | 状态机和多数 gate 已有 | 收敛为一个 production facade |
| Model execution | Cursor/Claude Adapter 已有 | M1 只启用 Cursor 生产适配，其他保留但不扩建 |
| Allowed/write scope | Plan schema、DiffScopeGuard 已有 | 区分 operator write scope 与模型 Plan Allowed |
| Customer verification | 多 entry 合取、ENV_FAIL 区分、Defect 已有 | 改成不预判 PASS/FAIL 的有界循环 |
| Review | Review Package + Adapter 已有 | production 必须用 Adapter；无 fixture fallback |
| Local delivery | local commit 已有 | 正式终点；强制 no push |
| Human acceptance | 记录能力已有 | M1 运行结束只报告 awaiting，不伪造 human acceptance |

### 3.2 无人值守仍缺失

| 缺口 | 风险 | 优先级 |
|---|---|---|
| 唯一生产 CLI | README 跑的是历史预制 patch pipeline | P0 |
| strict production profile | fixture/seeded/runner-prepared 可混入生产路径 | P0 |
| 通用有界 defect loop | 当前 V3/V4 预设首次 PASS/FAIL，真实首轮 PASS 会被当错误 | P0 |
| 机器可读终态与退出码 | 调度器无法区分 clarification、env、verify、budget | P0 |
| stage-boundary resume | 进程重启后不能按现有产物确定性继续 | P1 |
| budget/重复失败停止 | agent 可能重复消耗 token 或反复产生相同失败 | P1 |
| workspace isolation | 无人值守直接写用户工作树，事故面过大 | P1，M2 |
| run-level event log | 目前证据分散，外部 supervisor 不易消费 | P1 |
| Context Package 效果评测 | 无法证明比 agent 自主探索更有效 | P0（产品验证） |
| 多 Story lease/queue | 进程竞争和重复执行 | P2，真实单 Story 稳定后再做 |

### 3.3 现在冻结

- Runtime Kernel、Scheduler、Graph、Ranking、向量检索。
- Knowledge 自动写回/晋升；M1 一律 `lifecycle=SKIP`。
- 多 adapter 同时优化；M1 production 只用 Cursor。
- subagent、并行 Story、GitHub/Slack/Linear 触发。
- Dashboard、远程部署、自动 push/merge。
- 新能力域、新 Maven module。

## 4. 目标代码结构（不新增 Maven 模块）

```text
ai4se-demo
└── .../cli/Ai4seMain.java                 # 临时产品宿主；替换 jar 默认 Main

ai4se-orchestration
├── .../production/ProductionRunRequest.java
├── .../production/ProductionRunResult.java
├── .../production/ProductionPathway.java  # strict facade；唯一生产装配点
├── .../control/BoundedDeliveryLoop.java   # 不预判 PASS/FAIL
├── .../control/RunStopReason.java
└── .../events/RunEventWriter.java          # JSONL，后置到 PR3 也可
```

`PathwayRunner` 暂时保留作为已有测试/fixture harness，但产品 CLI 禁止直接构建其宽松 `Config`。等 M1 稳定后再拆分 1000 行大类，第一批不要同时大重构。

## 5. 分批施工

不要让 Cursor 一次执行全部工作。每批独立提交、独立测试；上一批不通过，不进入下一批。

### PR1 · 唯一正式入口 + strict production facade

#### 修改

1. 新增 `ProductionRunRequest`，只允许这些字段：
   - `workspace`
   - `storyId`
   - `seedRequirement`（Story 未打开时必需）
   - `writeScope`（operator 授权上限，至少一条相对路径/目录）
   - `adapterTimeout`
   - `maxDevelopmentRounds`（默认 3，PR1 可先存而不执行多轮）
   - role model overrides（可选）

2. 新增 `ProductionPathway`：
   - 同一个真实 `CursorCliAdapter` 注入 Analysis/Planning/Development/Review。
   - 禁止 `FunctionalModelCliAdapter`。
   - 禁止 `DevMutation`、`allowReviewFixture`、seeded fail、runner discovery/plan/gap 代写。
   - `AssumablePolicy.REQUIRE_ACK`。
   - Delivery 只允许 `LOCAL_COMMIT`。
   - Lifecycle 固定 `SKIP`。
   - 完成后返回 `AWAITING_HUMAN_ACCEPTANCE`，不得写 fixture/human acceptance。

3. 新增 `Ai4seMain`，M1 命令面：

```bash
java -jar ai4se-runtime.jar run \
  --workspace /repo \
  --story story-123 \
  --requirement /tmp/story-123.md \
  --write-scope src/main/java \
  --write-scope src/test/java \
  --max-dev-rounds 3
```

4. 修改 `ai4se-demo/pom.xml` shade main 为 `Ai4seMain`。
5. `ProductionRuntimeMain` 保留为 `legacy-fixture` 子命令或只能通过类名显式运行。
6. 修改 README：快速开始只能展示 Story 主链；历史 demo 移到 `docs/archive` 或明确标 legacy。

#### strict gate

- workspace 必须是 Git repo，且运行前工作树干净；否则拒跑。
- `.ai4se/repository/entries.yaml` 至少一条可用 test entry。
- requirement 必须有可判定 Acceptance。
- `writeScope` 是人工授权边界；Planning 产生的 Allowed 必须是其子集。
- 禁止绝对路径、`..`、`.git/`、`.ai4se/`、`.story/` 被当作业务写范围。
- 禁止 push；不得修改 remote。

#### 新测试

- `ProductionPathwayStrictConfigTest`
- `Ai4seMainArgumentTest`
- `ProductionPathwayRejectsFunctionalAdapterTest`
- `ProductionPathwayRejectsDirtyWorkspaceTest`
- `ProductionPathwayRequiresAllRealRoleAdaptersTest`
- `PlanAllowedMustBeSubsetOfOperatorWriteScopeTest`

#### 验收

```bash
mvn test
mvn -pl ai4se-demo -am package -DskipTests
java -jar ai4se-demo/target/ai4se-runtime.jar --help
```

`--help` 中不得出现 `suite`、`wave`、`fixture`、`seeded`、`hybrid`、`devMutation`。

### PR2 · 把 V3/V4 改成真实有界循环

#### 当前错误模型

```text
V3: 默认预期第一次 PASS
V4: 强制预期第一次 FAIL，然后只允许再做一次
```

这只能测试控制接线，不能处理真实结果。

#### 目标算法

```text
round = 1
while round <= maxDevelopmentRounds:
    build fresh Dev Package(round, latestDefect)
    invoke fresh Development adapter turn
    record diff and enforce Allowed ⊆ writeScope
    build fresh Verify Package(round)
    run all customer test entries

    if PASS:
        proceed to Review
        break

    if ENV_FAIL:
        stop FAILED_ENVIRONMENT immediately

    write/retain Defect Package
    if same failure fingerprint repeated twice with no meaningful diff:
        stop FAILED_NO_PROGRESS
    if round == maxDevelopmentRounds:
        stop FAILED_VERIFICATION_BUDGET
    round++
```

#### 关键约束

- 不读取模型输出中的 `DONE` 决定完成。
- PASS 只来自 Verification Contract。
- 每轮必须新建 Dev/Verify Package，Defect 为下一轮 P1。
- 每轮 agent 使用新 session/新调用；跨轮记忆只来自 Git、`.story`、Package。
- `maxDevelopmentRounds` 是硬上限。
- 失败 fingerprint 最小可用：失败 entry + exit code + 归一化日志摘要 hash。
- meaningful diff：相邻轮次 Git diff hash 不同；完全相同则计 no-progress。

#### 兼容处理

- V3/V4 与 `Script` 暂留给 fixture 测试，但 `ProductionPathway` 不再暴露。
- 新生产 loop 独立实现后，逐步把现有 V3/V4 测试改成：
  - first-pass 场景
  - fail-then-pass 场景
  - repeated-fail-stop 场景

#### 新测试

- `BoundedDeliveryLoopFirstPassTest`
- `BoundedDeliveryLoopFailThenPassTest`
- `BoundedDeliveryLoopMaxRoundsTest`
- `BoundedDeliveryLoopNoProgressTest`
- `BoundedDeliveryLoopEnvFailDoesNotCreateDefectTest`
- `EveryRepairRoundBuildsFreshPackagesTest`

### PR3 · 可恢复终态 + JSONL 事件

#### 机器终态

```text
AWAITING_HUMAN_ACCEPTANCE     exit 0
STOPPED_NEEDS_CLARIFICATION   exit 20
STOPPED_NEEDS_PLAN_APPROVAL   exit 21
FAILED_ENVIRONMENT            exit 30
FAILED_ADAPTER                exit 31
FAILED_VERIFICATION_BUDGET    exit 40
FAILED_NO_PROGRESS            exit 41
FAILED_POLICY                 exit 50
```

#### 状态文件

在客户仓 `.story/<id>/run/` 下维护：

```text
state.properties       # 当前 stage/round/status/last event sequence
events.jsonl           # append-only 生命周期事件
failure-fingerprint    # 最近失败摘要
```

每个 stage 只在产物校验通过后写 `stage_completed`。进程恢复时：

1. 读取状态；
2. 校验相应产物和 Git 状态；
3. 从最后一个完整 stage boundary 继续；
4. 不恢复半次 Adapter 调用；半次调用视为失败并重新开启该 stage 的 fresh turn；
5. local commit 已存在时不得重复 commit。

新增：

```bash
ai4se status --workspace /repo --story story-123
ai4se resume --workspace /repo --story story-123
```

M1 的恢复只承诺单进程、单 Story、stage boundary；不实现分布式 lease。

#### 新测试

- `ResumeAfterAnalysisBoundaryTest`
- `ResumeAfterFailedVerificationTest`
- `ResumeDoesNotDuplicateCommitTest`
- `CorruptStateRefusesResumeTest`
- `RunEventsAreAppendOnlyTest`

### PR4 · 真实 Story 签收 + Context Engineering A/B

功能代码暂停；只跑真实验证和修复被真实证据触发的问题。

#### 样本

- 同一 Java/Maven 技术栈。
- 10 个历史、低到中风险 Story。
- Acceptance 明确且客户测试可执行。
- 排除数据库迁移、生产凭证、跨仓大重构。

#### 两组

| 组 | 做法 |
|---|---|
| A | Cursor agent 获得 requirement + repo，自主探索 |
| B | 相同模型/预算，使用 AI4SE 分阶段 Context Package |

#### 每个 Story 记录

- 是否达到 `AWAITING_HUMAN_ACCEPTANCE`。
- 人工介入次数和原因。
- 每角色请求次数、输入/输出 token（能取则取）。
- 总 wall time。
- read/search/tool call 数（能取则取）。
- 修改越出 write scope 次数。
- 漏掉 Acceptance 条目数。
- Verification 轮数。
- 最终 diff 人工接受/需小修/拒绝。
- Package 总字节数和 P1/P2 构成。

#### M1 签收阈值

- 10 个 Story 至少 7 个无需中途人工聊天，到达 awaiting acceptance。
- 0 次越出 write scope 并成功 commit。
- 0 次测试未通过却进入 Review/Commit。
- 所有停止都有机器终态和可复查证据。
- B 组相对 A 组至少在成功率、token、工具调用或人工修正量中有两项明确改善；否则不得继续扩建 Context 域，先调整 Package。

## 6. M1 后才允许解锁的能力

| 解锁条件 | 才能建设 |
|---|---|
| 真实发生宿主污染或并发依赖冲突 | worktree/container sandbox |
| 单 Story stage resume 连续稳定 | 多 Story queue + lease |
| 10 个真实 Story 中检索不足反复出现 | Repository Map/更深 retrieval |
| 同类规则至少重复出现 3 次 | Knowledge/Learning 写回与晋升 |
| 单 agent 明确成为吞吐瓶颈 | subagent/并行 |
| 需要团队异步入口 | GitHub/Slack/Linear trigger |

## 7. Cursor 执行纪律

每个 PR 的 Cursor prompt 都必须附加：

```text
只实现本 PR 范围。不要新增 Maven 模块、能力域、Scheduler、Graph、Knowledge 自动化、
新 Adapter、并行 agent 或 UI。不要删除历史 fixture 测试，除非本 PR 明确要求迁移。
先读 capability-map、PATHWAY-VERIFICATION-HANDBOOK 和本施工方案。
实现后运行 mvn test。保持工作树中用户原有改动，不 push。
如果现有设计与本 PR 冲突，先报告冲突，不要扩大重构范围。
```

## 8. PR1 可直接复制给 Cursor 的任务

```text
执行 docs/90-status/m1-production-loop-execution-plan.md 的 PR1：
“唯一正式入口 + strict production facade”。

开始前先审阅：
- README.md
- ai4se-demo/pom.xml
- ProductionRuntimeMain.java
- FieldPathwayMain.java（只读，禁止复制其宽 CLI）
- PathwayRunner.java
- CursorCliAdapter.java
- current-support-status.md

具体要求：
1. 不新增 Maven 模块。
2. 在 ai4se-orchestration 新增 production facade 和窄 RunRequest/RunResult。
3. production 必须使用真实 CursorCliAdapter 覆盖 Analysis/Planning/Development/Review；
   禁止 Functional adapter、fixture review、DevMutation、seeded fail、runner 代写分析/计划/gap。
4. 在 ai4se-demo 增加 Ai4seMain 并设为 shaded jar 默认入口。
5. 正式 CLI 只暴露 workspace/story/requirement/write-scope/max-dev-rounds/model/timeout 等必要参数；
   不暴露 suite/wave/fixture/seeded/hybrid。
6. 运行前要求 Git repo、clean worktree、有效 test entries、有效 acceptance。
7. write-scope 是 operator 的授权上限；Plan Allowed 必须是其子集。
8. local commit 后返回 AWAITING_HUMAN_ACCEPTANCE；不得伪造 human acceptance，Lifecycle 固定 SKIP；不得 push。
9. 旧 ProductionRuntimeMain 保留为显式 legacy fixture，不再作为默认入口。
10. 按方案补齐 PR1 测试，更新 README 和 current-support-status。

不要在本 PR 实现有界 defect loop 或 resume；只把字段和扩展点留好。
完成后执行 mvn test 和 jar --help，把结果与改动文件列表报告出来。
```

## 9. 完成定义

M1 完成不是“所有单测绿”，而是留下一个可复跑证据：

```text
真实仓库
+ 新 Story
+ 真实 Cursor Adapter 四角色执行
+ 非预置代码修改
+ 客户测试真实 PASS（允许先 FAIL 后修）
+ Review Adapter 非 fixture
+ 本地 commit
+ 无 push
+ AWAITING_HUMAN_ACCEPTANCE
+ 进程中断后可从 stage boundary 恢复
```

任何一项出现 `fixture`、`seeded`、`FunctionalModelCliAdapter` 或 runner 代写业务产物，都不能作为 M1 签收证据。
