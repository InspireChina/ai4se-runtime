# 客户仓首次建库与首卡交付 Runbook

适用：把 AI4SE 用在一个真实客户 Git 仓，而非另建“验证仓”。所有 AI4SE 过程材料都写入该客户仓的 `.ai4se/` 与 `.story/`；业务代码也只在该客户仓变更。不会 push。

关联的产品模型见：[customer-repository-operating-model.md](../00-product/customer-repository-operating-model.md)。

## 0. 前置条件

- 业务仓已 clone，工作树干净；先分出自己的本地开发分支。
- AI4SE runtime 是指定、干净、已构建的 commit；记录 jar SHA。
- 已配置一个可用且受控的 Adapter（`codex`、`cursor` 或 `claude`）；不要把配额错误当成业务/流程错误。
- 构建、测试、数据库和外部服务仅使用客户允许的本地/专用环境；任何 destructive SQL 先经客户规则确认。

```bash
git -C /path/customer-repo status --short
git -C /path/customer-repo switch -c ai4se/customer-first-cards
cd /path/ai4se-runtime
mvn clean test
mvn -pl ai4se-demo -am package
```

## 1. 确定性 Onboard（不调用模型）

```bash
java -jar ai4se-demo/target/ai4se-runtime.jar onboard \
  --workspace /path/customer-repo --runtime-root /path/ai4se-runtime
```

审阅 `.ai4se/repository/facts.md`、`module-map.md`、`baseline.md`、`entries.yaml`。确认 build/test 命令真实可执行；Unknown 继续保持 Unknown，不能为了开跑编造环境结论。然后将该基线提交：

```bash
git -C /path/customer-repo add .ai4se/repository .ai4se/index
git -C /path/customer-repo commit -m "docs(ai4se): add deterministic repository baseline"
```

**通过条件：** 事实文件有来源，测试入口没有把 `skip` 伪装成测试；工作树重新干净。

## 2. 模型辅助初版知识候选（不改业务代码）

```bash
java -jar /path/ai4se-runtime/ai4se-demo/target/ai4se-runtime.jar discover \
  --workspace /path/customer-repo --scope repository \
  --candidate initial-repository --adapter codex --timeout-minutes 15
```

预期只出现：`.ai4se/knowledge-candidates/initial-repository/`。逐份审阅 `candidate.yaml` 与 `documents/*.md`：

- 每个正文有 `#` 标题、`## Evidence`、`## Unknowns`；
- `source_paths` 是存在的相对路径，且候选的 `source_commit` 等于当前 HEAD；
- 不采纳臆测、过长代码复述、无来源“架构建议”；
- 确认模型没有改业务文件、`.story/`、`.ai4se/knowledge/` 或 index。

发现错误时删除/保留候选作为失败证据均可，但不要直接改为 verified。修正范围后重新用新 candidate id 跑一次。

## 3. 人工批准知识基线

```bash
java -jar /path/ai4se-runtime/ai4se-demo/target/ai4se-runtime.jar approve-knowledge \
  --workspace /path/customer-repo --candidate initial-repository --actor <tech-lead>

java -jar /path/ai4se-runtime/ai4se-demo/target/ai4se-runtime.jar knowledge status \
  --workspace /path/customer-repo

git -C /path/customer-repo add .ai4se/knowledge .ai4se/index .ai4se/knowledge-candidates/initial-repository
git -C /path/customer-repo commit -m "docs(ai4se): verify initial repository knowledge"
```

**通过条件：** index 中是 `status: verified`，不是 candidate；每条有来源 commit/SHA；工作树干净。此后 Story Package 才会检索它。

## 4. 首张卡：先澄清，后冻结

将用户文本与截图/原型收进 Story：

```bash
JAR=/path/ai4se-runtime/ai4se-demo/target/ai4se-runtime.jar
java -jar "$JAR" intake --workspace /path/customer-repo --story order-action-001 \
  --request-file /path/order-action-001-request.md --attachment /path/mockup.png
java -jar "$JAR" specify --workspace /path/customer-repo --story order-action-001 --adapter codex
```

若输出 `CLARIFICATION_REQUIRED`，先审 `.story/order-action-001/specification/clarification.questions.md`，用明确的业务答案继续：

```bash
java -jar "$JAR" answer-spec --workspace /path/customer-repo --story order-action-001 \
  --answer "...明确的业务选择..." --actor product-owner
java -jar "$JAR" specify --workspace /path/customer-repo --story order-action-001 --adapter codex
```

当 `CANDIDATE` 时，人工审 `candidate-requirement.md` 并冻结：

```bash
java -jar "$JAR" freeze-spec --workspace /path/customer-repo --story order-action-001
```

冻结需求必须包含：Goal、in/out scope、Allowed Files、3–5 个可执行 AC、附件声明。不要把“模型应该自己判断”写成 AC。

## 5. Analysis interrupt/resume 与 Plan/probes

启动首段（`--interactive` 只处理 Analysis 的具体问题；绝不替人批准 Plan）：

```bash
java -jar "$JAR" run --interactive --workspace /path/customer-repo --story order-action-001 \
  --requirement /path/customer-repo/.story/order-action-001/requirement.md \
  --write-scope litemall-admin-api/src/main/java \
  --write-scope litemall-admin-api/src/test/java \
  --adapter codex --max-dev-rounds 3
```

Analysis 若给出 `clarification.questions.md`，终端显示问题；回答后会作为下一次 Analysis 的 P1 并重评。若不使用交互模式：执行 `answer`，再执行同参数 `resume`。出现 `ASSUMABLE`、环境错误、范围扩大或附件不可读时停下人工处理，不能伪造 ack。

Planning 产出后，人工审 Change Map、Impact Assessment、API/Data 影响、每条 AC 的 probe candidate；然后冻结 probes 并批准 Plan：

```bash
java -jar "$JAR" freeze-probes --workspace /path/customer-repo --story order-action-001
java -jar "$JAR" approve-plan --workspace /path/customer-repo --story order-action-001 \
  --note "scope, API/data impact and probes reviewed" --actor tech-lead
```

## 6. 无人值守开发到本地交付

```bash
java -jar "$JAR" resume --workspace /path/customer-repo --story order-action-001 \
  --write-scope litemall-admin-api/src/main/java \
  --write-scope litemall-admin-api/src/test/java --adapter codex --max-dev-rounds 3
```

期望流程：Development（最多 3 轮）→ 入口验证 → 每条冻结 probe → Defect Package（失败时）→ Review → `PASS` 才创建本地 commit。不是命令绿就交付：每条 AC 都要 `PROVEN`、write scope 合规、Review=PASS。

检查：

```bash
java -jar "$JAR" status --workspace /path/customer-repo --story order-action-001
git -C /path/customer-repo show --stat HEAD
git -C /path/customer-repo status --short
```

`AWAITING_HUMAN_ACCEPTANCE` 表示本地 commit 已成，仍未 push。业务方验收后才执行 `accept` 或 `reject`；拒绝创建后续 Story，不回写或覆盖原证据。

## 7. 后续卡与知识维护

Delivery 如触及某 verified 文档的 `source_paths`，index 自动标为 `stale` 并写 `.story/<id>/lifecycle/knowledge-stale.md`。审阅后提交这份状态变化。下一张涉及同模块的卡先运行：

```bash
java -jar "$JAR" knowledge status --workspace /path/customer-repo
java -jar "$JAR" discover --workspace /path/customer-repo --scope module:<module-id> \
  --candidate refresh-<module-id>-001 --adapter codex
```

再次走人工批准，不自动覆盖已有知识。多张卡默认 `queue add` 后串行；只有明确证明写入范围、迁移和 API 影响互不相交，并且准备独立 worktree/合并策略，才能并行。
