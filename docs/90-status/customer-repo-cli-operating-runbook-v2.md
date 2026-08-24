# AI4SE 客户仓日常使用手册 v2

> 适用对象：在 OMP/IDE 中打开客户 Git 仓、由 AI4SE 驱动单 Story 交付的研发团队。命令是控制面接口；OMP 只需调用命令并展示 `status` 的 `next=`，不保存模型长对话。

## 0. 这套系统做什么

AI4SE 是客户代码仓旁的交付控制面：把原始需求、人的决定、模型阶段产物和可执行验证保存为可追溯制品，受控调用模型完成开发。它不是模型，也不是会自行定义业务的 Agent。

人只做四类决定：业务澄清、候选规格确认、Plan/探针确认、最终接收。代码实现、验证失败后的有限修复、Review 和本地提交由系统无人值守完成。绝不自动 push、合并或部署。

## 1. 一次性客户仓接入

从构建出的 `ai4se-runtime.jar` 执行：

```bash
java -jar ai4se-runtime.jar onboard \
  --workspace /path/to/customer-repo \
  --runtime-root /path/to/ai4se-runtime
```

检查客户仓内以下文件：

- `.ai4se/repository/facts.md`：文件系统/Git 可观察事实和 Unknown；
- `.ai4se/repository/module-map.md`：模块及源码根路径；
- `.ai4se/repository/baseline.md`、`entries.yaml`：构建/测试入口；
- `.ai4se/repository/onboard-report.md`：明确说明 onboarding 没有执行测试、没有猜测业务。

由客户确认并实际执行 `entries.yaml` 声明的 baseline 命令，修正环境依赖与规则后，**一次性提交 `.ai4se/` 初始化配置**。这是客户仓配置基线，不是每张 Story 的手工步骤。

## 2. 一张自然语言需求卡

### 2.1 冻结原始输入

文字需求：

```bash
java -jar ai4se-runtime.jar intake \
  --workspace /path/to/customer-repo --story order-action-001 \
  --text '订单详情页需要展示当前操作能力，并禁止列表页读取明细字段'
```

带原型/截图：

```bash
java -jar ai4se-runtime.jar intake \
  --workspace /path/to/customer-repo --story order-action-001 \
  --request-file /tmp/request.md --attachment /tmp/order-detail.png
```

系统写入 `.story/<id>/input/raw-request.md`、附件副本及 SHA/MIME manifest。再次修改原始需求应开新卡或走澄清，不能覆盖该快照。

### 2.2 规格化与业务澄清

```bash
java -jar ai4se-runtime.jar specify \
  --workspace /path/to/customer-repo --story order-action-001 --adapter codex
```

结果只能是：

- `CANDIDATE`：阅读 `.story/<id>/specification/candidate-requirement.md`；
- `CLARIFICATION_REQUIRED`：OMP/CLI 展示 `.story/<id>/specification/clarification.questions.md`，人答：

`specify` 不是仅把自然语言改写成模板。它会先按需求中的业务词在既有模块图/仓库事实中定位入口，再只读检查该入口的直接 Controller、Service、实体/状态常量、UI 与邻近测试。它不全仓扫代码，也不额外制造一份“摸底报告”。每道待答问题必须写出真实仓内相对路径、字段/状态/接口等代码证据，并给 2–4 个选项、推荐与不回答的影响；模型不能替产品做选择。

```bash
java -jar ai4se-runtime.jar answer-spec \
  --workspace /path/to/customer-repo --story order-action-001 \
  --answer '仅管理员可见；只显示，不允许本卡修改订单状态。' --actor product-owner
java -jar ai4se-runtime.jar specify --workspace /path/to/customer-repo --story order-action-001 --adapter codex
```

候选规格被人确认后冻结：

```bash
java -jar ai4se-runtime.jar freeze-spec --workspace /path/to/customer-repo --story order-action-001
```

冻结规格至少含 `raw/goal/in_scope/out_of_scope/acceptance`。附件存在时必须逐项声明；如曾澄清，候选还必须有 `decisions`，把每个已答选择明确写入规格和 AC。此时才生成运行时认可的 `requirement.md`。冻结的是已确认的业务范围与验收口径，不是假装之后永远不会发现新事实。

## 3. Analysis、Plan 与验收探针

启动生产路径（只允许注册的 `cursor|codex|claude` adapter）：

```bash
java -jar ai4se-runtime.jar run \
  --workspace /path/to/customer-repo --story order-action-001 \
  --write-scope litemall-admin-api/src/main/java \
  --write-scope litemall-admin-api/src/test/java \
  --adapter codex --max-dev-rounds 3
```

第一次运行只到以下任一合法停点：

- Analysis `BLOCKED`：展示 `.story/<id>/analysis/clarification.questions.md`；回答并恢复：

```bash
java -jar ai4se-runtime.jar answer --workspace /path/to/customer-repo --story order-action-001 \
  --answer '使用既有管理员订单详情权限。' --actor product-owner
java -jar ai4se-runtime.jar resume --workspace /path/to/customer-repo --story order-action-001 --adapter codex
```

Analysis 不得重新问 Specification 已回答的业务选择。冻结后仍出现问题时，它必须标为 `NEW_FACT`、`CONTRADICTION` 或 `MISSED_DISCOVERY` 并附代码证据；最后一种是流程改进信号，说明定向摸底遗漏，而不是把遗漏悄悄包装成“需求变化”。回答被写入持久化澄清记录；`resume` 以该记录恢复，不依赖停止文案的字符串匹配。

- Analysis `CLEAR` 后生成 Plan 并停在 Planning：审阅 `.story/<id>/planning/plan.md`、`change-map.md`、`test-strategy.md`、`impact-assessment.md`，以及 API/Data 影响为 `PRESENT` 时的契约文件。

Planning 同时只在 `.story/<id>/planning/probe-candidate/` 生成候选探针。人审阅后执行：

```bash
java -jar ai4se-runtime.jar freeze-probes --workspace /path/to/customer-repo --story order-action-001
java -jar ai4se-runtime.jar approve-plan --workspace /path/to/customer-repo --story order-action-001 \
  --note '范围、影响和每条 AC 探针已审阅' --actor tech-lead
```

`freeze-probes` 会把候选复制为 `.ai4se/acceptance-probes/<id>/`，验证 SHA、命令和 AC 数。没有冻结探针时系统停在 Planning，绝不进入 Development。

## 4. 无人开发到本地交付

```bash
java -jar ai4se-runtime.jar resume \
  --workspace /path/to/customer-repo --story order-action-001 --adapter codex
```

系统最多进行 `max-dev-rounds` 次 Development→Verify。每轮给模型的 P1 固定包含：冻结 AC、Allowed Files、批准 Plan、Impact/API/Data 文档、Constraint Bundle；失败时额外加入当前 Defect，不能丢弃前述约束。所有 AC probe `PROVEN` 且 Review `PASS` 才会产生本地 commit。

检查：

```bash
java -jar ai4se-runtime.jar status --workspace /path/to/customer-repo --story order-action-001
```

交付完成时应显示 `AWAITING_HUMAN_ACCEPTANCE`；此时不会 push。客户验收：

```bash
java -jar ai4se-runtime.jar accept --workspace /path/to/customer-repo --story order-action-001 \
  --note 'UAT passed' --actor customer-reviewer
# 或：reject，保留本次证据并新开 follow-up Story
```

### 4.1 每阶段给模型什么，为什么不给完整聊天记录

AI4SE 不把历史对话和整个仓库塞进每一次模型调用。每阶段先给不可裁剪的 P1 工作单，再允许模型在受控范围内按需读取 P2；这样既不会丢掉业务约束，也不会被过期日志和无关源码稀释重点。

| 阶段 | 不可裁剪 P1 | 可按需读取 P2 | 必须产出 |
| --- | --- | --- | --- |
| Specification | 原始请求、附件清单、仓库事实、Unknown、上轮已答规格决策 | 目标入口与其直接依赖 | 候选规格或带代码证据的编号澄清问题 |
| Analysis | 冻结 requirement/AC、规格决策、仓库事实、模块图、baseline、附件索引、已回答澄清、适用规则 | 知识命中、目标模块与直接依赖 | 有来源的 discovery + `CLEAR/ASSUMABLE/BLOCKED` |
| Planning | requirement/AC、规格决策、Analysis 结论、规则、write-scope 上限、验证入口 | 目标代码/测试与直接调用链 | Plan、Change Map、影响声明、测试策略、探针候选 |
| Development | requirement/AC、规格决策、已批准 Plan、Allowed Files、Constraint Bundle、Impact/API/Data 文档 | 变更文件、直接调用方/被调用方、相邻测试 | 仅允许范围内的代码/测试变更 |
| Verify / Review | 冻结 AC/探针、规格决策、验证结果、业务 diff、范围结论 | 相关日志与失败指纹 | AC verdict / PASS、CONDITIONAL 或 REJECT |

进入缺陷修复时，P1 仍保留原冻结 requirement、AC、Plan、Change Map 和 Constraint Bundle；只额外附加本轮 Defect（失败命令、指纹、相关日志和怀疑范围）。这避免“修一个 bug 就忘了前面边界”，也避免把每轮所有历史输出重复塞入上下文。

## 5. 多卡串行

每张卡独立完成 `intake/specify` 后加入队列：

```bash
java -jar ai4se-runtime.jar queue add --workspace /path/to/customer-repo --story order-action-001
java -jar ai4se-runtime.jar queue add --workspace /path/to/customer-repo --story goods-write-002
java -jar ai4se-runtime.jar queue status --workspace /path/to/customer-repo
```

队列按串行方式选择 `READY_FOR_RUN`，其次选择 `READY_FOR_SPECIFICATION`；等待澄清的卡不会阻塞其它可执行卡。首版不自动并行写同一工作树。并行只能在后续对 Change Map、API/Data 契约和共享测试资源确认无交集后单独启用。

## 6. 证据与故障处理

所有 Story 制品在 `.story/<id>/`；业务代码之外的阶段产物可在验收后按客户仓策略归档。`status` 的 `next=` 是唯一下一步提示；不要根据模型文字猜下一命令。

允许当前 Story 的控制文件和当前 Story 冻结探针在同一条链中保持未提交，以支持 `answer/approve/resume`；任何业务代码、`.ai4se` 配置或其它 Story 的未提交变化仍会被拒绝。适配器失败、验证环境失败、范围越界、重复无进展、非 PASS Review 都会诚实停止并保留证据，不自动重试。
