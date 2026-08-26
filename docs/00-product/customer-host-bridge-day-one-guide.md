# AI4SE 客户仓第一天使用指南

> 适用对象：你已在客户电脑或云桌面打开客户仓，并且客户已允许使用 Cursor、Claude、Codex、OMP
> 或其它可执行本地命令的模型工具。本文是操作顺序，不是把模型会话替换成 AI4SE。

## 先记住三件事

1. **AI4SE 是客户仓旁边的交付控制面**：保存阶段状态、冻结输入、验证和证据；模型是可替换的执行器。
2. **白天的摸底和业务澄清在你已打开的模型会话中做**；AI4SE 不会偷偷启动第二个聊天模型。
3. **无人值守从 Plan 获得真实批准后开始**。需求含义、原型理解、范围和最终验收仍由人负责。

## 0. 一次性准备：把 Bundle 放到客户允许的工具目录

在可信的 AI4SE Runtime 源码目录构建 Bundle：

```bash
cd /path/to/ai4se-runtime
./scripts/build-host-bundle.sh /tmp/ai4se-host-bundle
```

将 `/tmp/ai4se-host-bundle` 移到客户批准的工具目录（示例 `/opt/ai4se`）。它包含 Jar、Host
脚本和本指南；不会安装模型 CLI、不会修改客户业务代码。

```bash
export AI4SE=/opt/ai4se
export AI4SE_JAR="$AI4SE/lib/ai4se-runtime.jar"
java -jar "$AI4SE_JAR" --help
```

### 最简日常入口：一条受控流程命令

安装 Bundle 后，日常交付优先使用 `ai4se-flow full`，而不是在模型对话中逐阶段复制提示词：

```bash
"$AI4SE/bin/ai4se-flow" full \
  --workspace /path/to/customer-repo --runtime-root /path/to/ai4se-runtime \
  --adapter codex --candidate initial-repository --knowledge-owner <name> \
  --story ORD-102 --request-file /approved-input/ORD-102.md \
  --write-scope <customer-relative-source-path> \
  --write-scope <customer-relative-test-path> \
  --product-owner <name> --plan-owner <name> --acceptance-owner <name>
```

它调用被选择的受控 Adapter 执行模型工作，并只在以下位置停在终端等你的**显式**回复：
知识批准、规格冻结、业务澄清、Plan 批准和最终验收。验证失败、Review 非 PASS、越界或模型 CLI 异常会
保留证据并停止，绝不自动点“继续”。运行 `"$AI4SE/bin/ai4se-flow" --help` 查看完整参数。

Bundle 也附带 `skills/ai4se-customer-delivery/SKILL.md`。Codex/OMP 等支持项目 Skill 的工具可以安装或
引用它；其唯一职责是让模型调用同一条 `ai4se-flow` 命令，不让模型自己重写流程。

首卡完成并已批准知识后，后续卡使用同一命令加 `--existing-knowledge`；它会跳过安装、确定性摸底和
初次知识批准，直接从新需求卡的 Specification 开始。若卡 2 依赖卡 1，可再添加
`--queue-dependency requires_accepted_parent --queue-parent <card-1-id>`。

首次进入客户项目时安装 Host 指令并建立确定性事实：

```bash
cd /path/to/customer-repo
java -jar "$AI4SE_JAR" install --workspace "$PWD" --host terminal-host --runtime-jar "$AI4SE_JAR"
java -jar "$AI4SE_JAR" onboard --workspace "$PWD" --runtime-root /path/to/ai4se-runtime
```

先由工程师核对 `.ai4se/repository/entries.yaml`：构建、测试、启动命令必须真的适合这个客户仓。
若“test”实际跳过了测试，必须修正为真实的测试入口，不能继续假装基线已验证。

## 1. 首次摸底：确定性扫描 + 当前模型的证据化解读

`onboard` 产生的 `.ai4se/repository/` 是机器可复查事实：技术栈、模块、构建/测试入口、基线和模块地图。
它不猜业务含义。随后在当前模型（Cursor / Claude / Codex / OMP）会话中发送以下文字：

```text
请严格按 .ai4se/host/AI4SE-HOST.md 执行首次项目摸底。
先执行 bridge prepare-discovery；仅阅读输出 package/model-input.md 所列 P1 与必要源码；
只生成带 source paths 和 Unknowns 的 knowledge candidate，不修改业务源码和 verified knowledge。
完成后执行 bridge submit-discovery，并把 next 状态告诉我。
```

模型会执行：

```bash
java -jar "$AI4SE_JAR" bridge prepare-discovery \
  --workspace "$PWD" --candidate initial-repository --scope repository
# 模型只写 .ai4se/knowledge-candidates/initial-repository/ 后：
java -jar "$AI4SE_JAR" bridge submit-discovery \
  --workspace "$PWD" --candidate initial-repository --scope repository
```

你审阅候选文档中的 Evidence、Working Boundary、Unknowns 和 source paths。确认没有把推测写成事实后，
由真实知识负责人批准并建立本地知识基线：

```bash
java -jar "$AI4SE_JAR" approve-knowledge \
  --workspace "$PWD" --candidate initial-repository --actor <real-knowledge-owner>
java -jar "$AI4SE_JAR" checkpoint-knowledge \
  --workspace "$PWD" --candidate initial-repository
```

这里的 checkpoint 只创建本地 Git 提交，绝不 push。后续需求会按标签和 source path 检索已批准知识，
而不是让模型反复盲扫整仓。

## 2. 一张需求卡：先规格，再分析，再无人值守交付

把需求、截图、PDF、接口说明保存为客户仓允许访问的本地文件。对当前模型说：

```text
我要做 Story <story-id>。原始需求和附件已在本地。
请用 bridge prepare-specification 读取受控 Package，根据代码证据产出 candidate requirement；
若业务含义有歧义，输出编号问题、为什么影响实现、可选项和代码依据。不得写业务源码，不得自行冻结规格。
```

操作者先创建输入：

```bash
java -jar "$AI4SE_JAR" intake \
  --workspace "$PWD" --story <story-id> \
  --request-file /approved-input/request.md \
  --attachment /approved-input/wireframe.png
java -jar "$AI4SE_JAR" bridge prepare-specification --workspace "$PWD" --story <story-id>
```

模型读 `.story/<story-id>/packages/specification/model-input.md`，只写 specification 候选；随后：

```bash
java -jar "$AI4SE_JAR" bridge submit-specification --workspace "$PWD" --story <story-id>
```

若输出 `HUMAN_SPEC_CLARIFICATION`，你在当前模型会话或终端真实回答。回答是业务决定，不能由模型代填：

```bash
java -jar "$AI4SE_JAR" answer-spec \
  --workspace "$PWD" --story <story-id> \
  --answer "<你的业务决定>" --actor <real-product-owner>
```

让模型再次 prepare/submit，直到候选规格清晰。你审阅 `goal`、`in_scope`、`out_of_scope`、决策和
每条 AC 后，才冻结：

```bash
java -jar "$AI4SE_JAR" freeze-spec --workspace "$PWD" --story <story-id>
```

## 3. Analysis 与 Plan：仍会停下来问真正的业务问题

启动受控主链：

```bash
java -jar "$AI4SE_JAR" run \
  --workspace "$PWD" --story <story-id> \
  --requirement ".story/<story-id>/requirement.md" \
  --write-scope <允许修改的目录或文件> \
  --adapter codex --max-dev-rounds 3 --timeout-minutes 10
```

`--adapter` 可改为客户已批准且已注册的 `cursor` 或 `claude`；切换 Adapter 不继承聊天记忆，只继承
`.story` 中冻结的规格和阶段制品。Analysis 出现 `BLOCKED` 或需要真实业务决定时，系统停止并留下带
编号的问题；回答后用 `answer` 和 `resume`，而不是手改状态文件。

Plan 形成后，审阅这些文件：

```text
.story/<story-id>/planning/plan.md
.story/<story-id>/planning/change-map.md
.story/<story-id>/planning/effective-constraints.md
.story/<story-id>/planning/test-strategy.md
.story/<story-id>/planning/api-contract.md       # 有接口影响才存在
.story/<story-id>/planning/data-change.md        # 有数据库影响才存在
.ai4se/acceptance-probes/<story-id>/              # 每条 AC 的候选可执行探针
```

确认后冻结探针和批准 Plan（命令和字段以随 Bundle 交付的正式 Runbook 为准）。从这一刻起，
Development → Verification → 缺陷修复（最多 `max-dev-rounds`）→ Review → **本地** commit 才可无人值守。

## 4. 交付前后看什么

不要只看模型说“完成”。至少检查：

- `.story/<story-id>/verification/report-round-*.md`：正式入口和每条冻结 AC probe 的命令、exit code、SHA、`PROVEN`；
- `.story/<story-id>/review/review-result.properties`：必须 `decision=PASS`、`review_source=adapter`；
- `.story/<story-id>/delivery/`：本地 delivery commit 与 allowed-files / write-scope 检查；
- `.story/<story-id>/run/state.properties`、`workflow-state.properties`：结算状态，而不是模型口头结论。

最后由真实验收人操作 `accept` 或 `reject`。AI4SE 只做 local commit；提交、合并、UAT、部署和回滚
按客户已有工程流程执行。

## 5. 多张卡的实际使用法

当前能力是**串行队列**：卡 1 完成并被验收后，才允许依赖卡 1 的卡 2 进入执行。可先为多张卡完成
摸底、规格和澄清，但不要同时让多个无人值守 Adapter 写同一个工作树。并行开发需要明确文件/数据库/
接口资源不重叠的调度能力；当前不把它伪称为已经具备。

完整命令契约、停止规则和附件处理见 [客户模型工具接入 Runbook](../90-status/customer-host-bridge-runbook-v1.md)。
