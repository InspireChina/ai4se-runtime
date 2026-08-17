# M1 真实客户需求卡验证手册 v1

> 目标不是继续证明 Runtime 的单个门禁，而是在真实项目中验证：AI4SE 能否根据一张冻结的需求卡生成业务代码、保留全过程文档、以独立验收证据决定是否本地交付，并用结果改进下一张卡。
>
> 本手册替代“首次交付资格验证”的执行方式；不替代、不修改历史 `pair-json-*` A/B 证据和操作手册。历史 A/B 用于比较研究，本手册用于评估可交付性。

## 1. 验证范围与阶段

| 阶段 | 宿主 | 目的 | 通过后才进入 |
|---|---|---|---|
| R-001 | JSON-java（客户模拟） | 用低风险、真实 Java/Maven 仓验证完整自动交付 | 一套业务系统 |
| R-002–R-004 | 真实业务系统的隔离 worktree | 验证领域需求、边界与过程稳定性 | 多仓/多技术栈评估 |
| 复盘 | 每张卡后 | 以证据挑一个最高价值优化 | 下一张卡 |

R-001 不再复用既有 `json-pointer-space-002`：该题及其实现方向已被知晓，只保留为历史诊断证据。必须选择一张新的、未查阅后续修复答案的 JSON-java 需求卡。

业务系统不必限定为电商；优先选择有稳定自动测试、可隔离运行、无真实外部副作用的仓储/订单/后台/API 系统。电商或 WMS 是合适的第二阶段候选，因为它们能提供状态变更、权限、库存/订单边界等更接近客户场景的需求，但不应在 R-001 前引入数据库迁移、支付、消息发送或生产凭证。

## 2. 本轮固定边界

- Runtime 基线：`788be12812fd4e32223955d0303a18f103277a9f` 或其经审阅的直接后继。
- Adapter：`codex`；未指定 `--model` 时记录 `model_selection=cli-default`。
- 每次只运行一个 B-only Story；首轮不做 A/B、不执行 `resume`、不 retry、不写人工 ack、不 push。
- 使用新 LAB、新浅克隆、新 worktree、新 story id；不覆盖历史 evidence。
- Runtime 只在真实运行发现 P1（误交付、不可运行、数据/范围安全问题）时修复。P2 进入 backlog，不阻塞下一张需求卡。
- 成功只表示 `AWAITING_HUMAN_ACCEPTANCE`，不是自动合并或自动推送。

## 3. 一张可验证需求卡的最低结构

需求卡由操作者冻结，模型只读取。建议位置：LAB evidence 根的 `requirement.md`。

```markdown
# R-001: <短标题>

## goal
<用户可感知的业务目标>

## in_scope
- <允许修改的业务文件 1>
- <允许修改的业务文件 2>

## out_of_scope
- 数据库迁移、生产凭证、远程写操作等

## acceptance
- AC1: <可观察的行为与精确预期>
- AC2: <异常/边界行为>
- AC3: <回归或兼容行为>
- AC4: <正式入口测试命令必须成功>
```

选择卡片时满足以下条件：

- 2–6 个业务文件的预期改动面；3–7 条 AC；至少一条边界或失败路径。
- 仓库基线测试可稳定运行；没有未解释的原始失败。
- 每条功能 AC 都能由独立 probe 判定，不以模型本轮新写的测试作为唯一证据。
- 有明确回滚方式：丢弃隔离 worktree 或不 cherry-pick 本地 Delivery commit。

## 4. 冻结验收探针

在客户 worktree 的共同 baseline 中提交：

```text
.ai4se/acceptance-probes/<story-id>/
├── probes.properties
├── ac1.sh
├── ac2.sh
└── ...
```

`probes.properties` 必须覆盖 requirement 中的每一条 Acceptance，按顺序声明 path、SHA-256 与执行命令。格式见 [verification-acceptance-probes.md](../30-delivery-orchestration/workflow/stages/verification-acceptance-probes.md)。

探针由操作者在运行前编写并提交，不能位于 `--write-scope` 内，不能依赖模型本轮可修改的测试文件。对于 Java 项目，优先让探针调用构建产物或自带的只读测试 harness；探针本身可运行正式构建/测试，但其业务断言必须独立存在。

## 5. 运行前检查清单

1. 新建 LAB；不复用历史 JSON-java attempt/worktree/evidence。
2. 浅克隆客户项目的固定 tag 或 commit，在 source clone 上执行 baseline `mvn clean test`（或客户正式入口）。
3. 执行 onboarding，事实校正 `entries.yaml`，确保正式 `test` entry 与 AC 中的命令一致。
4. 在 source clone 写入并提交 `.ai4se/`、`.story/README.md`、冻结 probes；得到共同 `baseline_commit`。
5. 从该 commit 创建一个新的 B worktree；确认 `git status --porcelain` 为空。
6. 将 requirement 写入 evidence 根，写入 SHA-256，设置只读；在启动前再次校验 SHA。
7. 构建并记录 Runtime jar SHA；记录 Codex binary、Runtime commit、上游 commit、baseline commit 与 adapter/model 选择。
8. 确认 write scope 只包含业务文件，不包含 `.ai4se/`、`.story/`、`.git/` 或宽泛仓库根目录。

任一前置失败都停止并记录为 `ENV_BASELINE_FAIL` 或 `FAILED_POLICY`；不得启动一次“试试看”的正式 run 后再就地修复重跑。

## 6. 正式 B-only 运行

以下为命令骨架；所有路径、story id、scope 与 requirement 必须按本卡替换：

```bash
"$AI4SE_ROOT/scripts/pr4-background-run-capture.sh" \
  "$EVIDENCE_ROOT/arm-b/run.properties" -- \
  env AI4SE_CODEX_BIN="$CODEX_BIN" \
  java -jar "$RUNTIME_JAR" run \
    --workspace "$ARM_B_REPO" \
    --story "$STORY_ID" \
    --requirement "$REQUIREMENT_FILE" \
    --write-scope '<business-file-1>' \
    --write-scope '<business-file-2>' \
    --adapter codex \
    --max-dev-rounds 3 \
    --timeout-minutes 10 \
    > "$EVIDENCE_ROOT/arm-b/runtime.stdout.txt" \
    2> "$EVIDENCE_ROOT/arm-b/runtime.stderr.txt"
```

运行期间不补充模型消息、不手工编辑客户 worktree、不更换 adapter、不执行 resume。Runtime 自己的 `FAILED_*`、`STOPPED_*` 与超时都是有效结果，必须冻结而非覆盖。

## 7. 终态判定

| 结果 | 含义 | 后续动作 |
|---|---|---|
| `AWAITING_HUMAN_ACCEPTANCE` / exit 0 | 全部 AC probes 为 `PROVEN`、Review PASS、已产生本地 Delivery commit | 审阅代码和证据；决定是否人工接收/cherry-pick |
| `STOPPED_NEEDS_CLARIFICATION` / exit 20 | Gap、Plan 或 Review 尚不能安全放行 | 冻结；判断是需求/探针不足还是 Runtime 问题 |
| `FAILED_VERIFICATION_BUDGET` / exit 40 | 三轮内未通过独立验证 | 复盘实现/上下文/卡片粒度；不续跑 |
| `FAILED_NO_PROGRESS` / exit 41 | 连续修复没有有效推进 | 检查 Defect package 与需求可诊断性 |
| `FAILED_POLICY` / exit 50 | 输入、探针、范围、adapter 或工作树门禁失败 | 修正流程或 Runtime；全新 LAB 才能再测 |

## 8. 必须冻结的过程文档与代码证据

终态出现后，先调用可复用的收集器；它只读客户 worktree，拒绝覆盖既有 artifact：

```bash
"$AI4SE_ROOT/scripts/real-story-collect-evidence.sh" \
  "$EVIDENCE_ROOT" "$ARM_B_REPO" "$STORY_ID" "$BASELINE_COMMIT" \
  "$RUNTIME_JAR" "real-story-$STORY_ID" "cli-default" \
  0 0 accept
```

最后三个值是**运行后审阅事实**：运行内人工介入数、漏验收数、diff 审阅结论；不确定时应停止收集并由审阅者先判定，不能填造。收集器会产出 `status.txt`、`scorecard.txt`、`scorecard.csv`、`RUN-SUMMARY.md`、`REVIEW-REQUEST.md` 和完整的哈希清单。它不改原始 run，也不允许对已生成的 evidence 覆盖写入。

从客户 worktree 只读复制到 evidence 根的最小结果是：

```text
arm-b/
├── runtime.stdout.txt / runtime.stderr.txt / run.properties
├── status.txt / scorecard.txt / scorecard.csv
├── RUN-SUMMARY.md / REVIEW-REQUEST.md
├── status.txt / scorecard.txt
├── baseline..HEAD.patch
├── changed-files.txt / final-git-status.txt / git-log.txt
└── story/                       # 完整 .story/<story-id>/ 快照
    ├── analysis/
    ├── planning/
    ├── development/
    ├── verification/            # 每条 AC 的 probe verdict/command/SHA
    ├── review/
    ├── delivery/
    ├── run/
    └── workflow-state.properties
```

另存 baseline 测试、requirement SHA、每个 probe SHA、runtime jar SHA、环境与 git commit。冻结后不修改原始 evidence；审阅修正另写 correction 文件。

## 9. 质量度量与复盘

每张卡输出以下事实，不用“测试绿了”代替功能结论：

| 指标 | 计算/来源 |
|---|---|
| 功能完成率 | `PROVEN AC 数 / AC 总数`；可另记业务权重版 |
| 首次交付 | 是否首次运行达到 `AWAITING_HUMAN_ACCEPTANCE` |
| 独立缺陷 | FAILED probe、范围外修改、独立人工复测发现的缺陷数 |
| 逃逸缺陷 | Delivery 后、未被冻结 probes 捕获的问题数 |
| 过程阻断点 | 首个终止的阶段与机器终态 |
| 成本 | wall time、Development rounds、可获得时的 token/tool call |
| 人工介入 | 运行中的介入必须为 0；运行后的最终接收另记 |

单张卡不能给出有统计意义的“bug rate”。R-001 只用于发现第一类缺陷；R-002 至 R-004 后再统计：

```text
首次交付率 = 首次达到 awaiting 的卡数 / 总卡数
独立缺陷率 = 独立发现的缺陷数 / 已交付卡数
逃逸缺陷率 = 交付后发现的缺陷数 / 已交付卡数
```

每次复盘只允许选择一个最高价值改进：优先处理误交付、不可运行、范围安全与重复性阻断；不因单个 P2 扩展 Scheduler、并发、多 Agent、知识自动晋升或新领域。

## 10. 多批次复盘与工程改进闭环

每个 LAB 的原始 evidence 是不可变事实；不得在原始日志、report、patch 或 `.story` 快照上补写结论。跨批次结论另存到验证计划根目录：

```text
real-story-validation/
├── scorecard.csv                 # 每次运行追加一行，不回写历史行
├── R-001-retrospective.md        # 本卡的事实、归因与决定
├── R-002-retrospective.md
└── improvement-register.md       # 跨卡的开放问题与已验证改进
```

每张卡结束后必须写一份 retrospective，且只包含可定位到 evidence 的内容：

- 需求/探针：AC 是否可判定、probe 是否覆盖真正风险、需求粒度是否合适；
- 仓库/环境：基线、构建、依赖、测试稳定性；
- Adapter/模型：超时、拒绝、误解、范围外写入、模型选择；
- 控制/证据：Gate、状态机、审计、交付范围是否诚实；
- 实现质量：独立 probe 失败、人工复测缺陷、可维护性问题。

每个问题都记录 `evidence_link`、影响（交付/质量/成本/安全）、首次出现的 Story、出现次数、候选改进和决定。不要把模型猜测、聊天摘要或“感觉可行”写成事实。

改进晋升规则：

| 情况 | 处理 |
|---|---|
| 误交付、范围/数据安全、不可运行 | P1：暂停下一张卡，最小修复 + 回归测试 |
| 单次 P2，且不影响完成率或证据可信度 | 记录 backlog，不阻塞下一张卡 |
| 同类问题在 2 张卡重复，或明显降低完成率 | 在下一张卡前做一个最小流程/Runtime 改进 |
| 同类模式在 3 张卡重复 | 才提炼为通用能力、模板或新的控制策略 |

每次改进必须有“改进前的原始 evidence → 最小改动 → 回归测试 → 后续真实卡是否改善”的链条。没有后续真实卡证据的改进，只能标记为“已实现，未证实有效”。

## 11. 进入业务系统的门槛

R-001 不要求完美，但要满足以下条件才进入业务系统：

- 真实 Codex 调用完成，未出现人工中途干预；
- 所有终态、日志、过程文档可复查；
- probe 机制确实执行并能区分 `PROVEN`、`FAILED`、`UNPROVEN`；
- 若未交付，阻断原因可归类且有明确的唯一下一改进；
- 若交付，Delivery commit 范围合法，且至少完成一次独立人工复测。

业务系统阶段先选低副作用的只读/API/后台规则需求，随后才增加跨模块、数据库或异步任务复杂度。不要一开始选择支付、库存扣减、真实消息投递、迁移或需要线上凭证的 Story。
