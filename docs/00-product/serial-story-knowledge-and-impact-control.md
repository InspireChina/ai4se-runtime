# 串行 Story 的知识演进与影响面控制

> 目标：允许一组已经澄清、冻结并获准执行的 Story 在客户仓内串行无人值守地交付，
> 同时不把未验收的模型结论写成项目事实，也不因卡 A 的改动遗漏卡 B 的间接行为风险。

## 当前实施状态

- **已实现并有回归测试：** Delivery 只写 Story-owned stale evidence，不修改 verified
  index；完成的前卡证据不再阻塞后卡 clean-worktree gate；后卡 Analysis 将命中的 stale
  条目作为 P1 刷新义务，并要求直接读取当前源码。
- **已实现并有回归测试：** Planning 中任何 `PRESENT` 影响都必须给出场景 ID、现存源码
  Evidence 路径和可执行入口测试/AC Probe 的 verification 引用；否则不能形成执行产物。
- **尚未实现，不能宣称已经具备：** Delivery 后自动调用模型生成知识 Candidate、知识
  revision 的审批晋升、队列的父子依赖字段及自动拒绝祖先阻断。这些仍按第 6 节拆分，
  必须先以真实串行卡验证现有两项改动的收益后再加入。

## 1. 先明确两个真相来源

| 内容 | 当前真相来源 | 是否可由模型自动改写 |
| --- | --- | --- |
| 当前代码、配置、测试 | 当前客户仓 `HEAD` 与实际命令输出 | 否；模型只能在批准范围内修改代码，Control 记录 diff |
| 已验证项目知识 | `.ai4se/knowledge/` + `knowledge.yaml` 的已批准 revision | 否 |
| 新知识候选 | `.ai4se/knowledge-candidates/` | 可生成，但不能当作事实 |
| 业务决策 | 冻结 Requirement 的 Decision / 规格答案 | 否；只能由人确认或改写规格 |

知识库是检索加速器和约束材料，不是当前源码的替代品。只要知识条目的
`source_paths` 被改变，当前 `HEAD` 就优先于该知识正文。

## 2. 知识状态与串行运行规则

知识条目必须区分三种状态：

```text
VERIFIED  人工批准、有来源 commit 与 Evidence 的可用知识
STALE     其来源路径已被后续 Delivery 改动；不能再作为当前事实
CANDIDATE 新卡/缺陷卡产生、尚未批准的建议性更新
```

卡 A 的 Delivery 成功后，Control 必须做以下确定性动作：

1. 以业务 commit 的 changed files 与每条 `source_paths` 比较；命中的 `VERIFIED`
   条目改为 `STALE`，并写入 `.story/<A>/lifecycle/knowledge-stale.md`。
2. 在不改写 verified knowledge 的前提下，调度一次有上限的
   `knowledge-refresh candidate`。它只能写
   `.ai4se/knowledge-candidates/refresh-<A>/`；失败或 Adapter 不可用只记录为
   `refresh=deferred`，不得使已成功的 Delivery 失效。
3. 候选必须带上 Delivery commit、当前 source digest、Probe/回归证据与被替代的
   knowledge revision。

这一步不等待最终业务验收。候选不是事实，因此不会污染后续卡；它只是为下一卡
准备经过当前代码重新阅读的导航材料。

### 卡 B 如何在卡 A 尚未人工验收时继续

卡 B 启动前必须将其基线钉在卡 A 的本地 Delivery commit。它针对每个命中的知识条目
获得下列输入：

```text
VERIFIED              → 可作为 P1 摘要，按需读取证据正文
STALE                 → 仅作历史材料；P1 必须包含当前源码切片与 A 之后的相关 diff
CANDIDATE             → 仅作定位提示；不得作为业务或技术事实引用
```

因此未批准的 Candidate 不会阻塞卡 B，也不会被模型当成真相。模型应先阅读当前源码，
再决定是否需要澄清或刷新。只有无法从当前代码、冻结决策和允许的运行证据确定的
**业务语义**，才触发 human interrupt。

## 3. 队列依赖规则

每张 Story 在进入串行队列时必须声明与前卡的关系：

```text
dependency=NONE | BATCH_APPROVED_PARENT | REQUIRES_ACCEPTED_PARENT
```

| 关系 | 夜间可否继续下一卡 | 说明 |
| --- | --- | --- |
| `NONE` | 可以 | 卡 B 不依赖卡 A 的业务语义、API、数据迁移或状态变化 |
| `BATCH_APPROVED_PARENT` | 可以 | 人已在白天批准该批卡共享的设计前提；每卡仍有独立 AC 与验证 |
| `REQUIRES_ACCEPTED_PARENT` | 不可以 | 卡 B 依赖卡 A 的业务决策或对外行为；必须等 A 被 accept |

若卡 A 最终被 reject，所有将 A 作为父项的未完成卡必须标为
`BLOCKED_BY_REJECTED_ANCESTOR`。已交付的后代卡不能被悄悄继续；需要人决定回退、
重写规格或以新的修复卡处理。

## 4. A 改了却间接影响 B：不能只靠模型判断

“没有直接调用”不等于“不会受影响”。典型间接关系包括：

- 同一数据库状态、共享缓存、事务边界或异步消息；
- 同一 API 字段被多个前端流程/第三方使用；
- 权限、拦截器、特性开关、配置默认值；
- 同一状态机的前序操作改变后续可执行动作；
- 并发、幂等、排序、分页、时间与金额等跨流程不变量。

任何工具都不能证明所有运行时行为都没有影响；反射、配置、远程调用、脏数据和
未建模业务规则都会留下盲区。目标不是承诺 100%，而是把风险从“模型忘了想”变为
“有证据的影响假设 + 对应验证 + 明确 Unknown”。

### 影响面包（Impact Package）

Planning 前由确定性扫描与模型的受限阅读共同生成：

```text
.story/<id>/planning/impact/
├── impact-report.md
├── impact-index.properties
├── direct-references.md
├── behavioral-scenarios.md
├── regression-selection.md
└── unknowns.md
```

`impact-index.properties` 是门禁读取的机器合同：

```properties
direct_callers=...
direct_callees=...
api_consumers=...
data_entities=...
events_or_jobs=...
config_or_permissions=...
behavioral_scenarios=...
unknown_count=0
required_regression_probes=...
```

其中不能凭模型填满：

- **确定性扫描**：Java 符号引用、Controller/Service/Repository、DTO 字段、表名、
  消息 topic、配置 key、前端 API 调用、现有测试和构建入口。
- **模型分析**：将扫描结果与需求/状态变化组合，提出“先做 A 再做 B”的业务场景，
  找出状态机、权限和兼容性风险，并把无法证明的事项写为 Unknown。
- **人工决策**：仅处理真正的业务语义、外部系统行为或测试环境无法确认的问题。

模型不得说“未发现影响，因此无影响”。它只能说：

```text
扫描已覆盖的关系：……
已由测试/Probe 证明的行为：……
尚未覆盖的动态边：……
结论：风险已验证 / 仍未知，及其原因。
```

### 对应测试规则

每一项 `behavioral_scenario` 至少映射为以下之一：

1. 现有回归测试；
2. 新增单元/模块测试；
3. API、数据库或 UI 的可执行 Probe；
4. 无法自动验证时，明确标注未覆盖原因；该风险未被批准前阻断 Delivery。

若影响分析声明了 API、数据、权限、状态机或配置影响，Planning 必须同时产生对应
`api-contract.md`、`data-change.md` 或场景 Probe；不能以“入口测试已绿”跳过。

## 5. 模型上下文：按职责取证，不塞整库

| 阶段 | 必须输入（P1） | 按需输入（P2） | 明确禁止当事实 |
| --- | --- | --- | --- |
| Analysis | 冻结需求、Decision、当前 HEAD、命中知识状态、影响索引 | 当前源码、相关 diff、附件 | STALE/CANDIDATE 的结论正文 |
| Planning | Analysis 结论、Impact Package、Constraint Bundle、Allowed Files | 调用链、邻近测试、API/表定义 | 全仓 dump、无关历史卡 |
| Development | Requirement/AC、批准 Plan、Change Map、Constraint Bundle、回归选择 | 目标源码和测试 | 未批准业务决策 |
| Defect 修复 | 前述冻结材料 + 当前 Defect/失败日志/已改 diff | 相关调用方与回归测试 | 用新 defect 替换原规格 |
| Verification | 冻结 probes、实际 diff、入口测试、质量门禁、回归选择 | 必要日志 | 模型自述“应该通过” |

Skill 的作用是固定模型的工作步骤和输出合同，例如“读取 Impact Package 后生成场景矩阵”。
Skill 不能发现运行时不存在于代码/测试/资料中的依赖，因此不能替代扫描、证据与验证。

## 6. 需要实施的最小改造（按顺序）

### M1：不阻塞串行卡的知识刷新

1. 在 Delivery 后增加受限 `refresh-knowledge` 阶段，允许一次模型调用生成 Candidate；
   记录 `generated | deferred | refused`，不重试循环。
2. 扩展索引状态为 `VERIFIED / STALE / CANDIDATE`，并支持 `supersedes` 与 revision。
3. 新增 `KnowledgeRefreshPackageBuilder`：为下一卡命中的 stale 知识输出旧条目、当前
   源码切片、从 `source_commit` 到当前 HEAD 的 diff。
4. Queue 增加 dependency 与 ancestor 状态；禁止被 reject 祖先的后代继续。

**验收：** 两张无依赖卡可在一个夜间批次连续本地交付；卡 A 修改知识来源后，卡 B
的 Analysis 审计明确显示当前源码和 diff，且未将 Candidate 当事实。

### M2：影响面合同与回归选择

1. Onboard 扩展确定性 source inventory，至少产出模块、符号、API、配置、表/实体、
   消息和现有测试的索引；不先建通用 Graph Engine。
2. Planning 引入 `ImpactPackageBuilder` 与上述文件合同；模型必须对每项场景给出证据或
   Unknown。
3. `approve-plan` 校验有影响项时存在相应测试/Probe 映射。
4. Verification 执行 `regression-selection.md` 中已经批准的命令/Probe，并把未覆盖项
   写入报告；不伪装为全量回归。

**验收：** 选择一张“改状态后影响后续操作”的真实卡；报告能列出直接引用、状态
场景、选中的回归测试、未知动态边；至少一个非直接影响场景被 Probe 或现有测试证明。

### M3：缺陷闭环与知识修订

1. 增加 Defect Intake/Analysis 合同，固化现象、期望、复现证据、关联 Story/commit。
2. Defect Delivery 后沿用 M1 的知识刷新流程；纯实现 bug 仅新增回归测试，不制造
   领域知识噪声。
3. `approve-knowledge` 以 revision 方式晋升 Candidate，保留旧版本及 `supersedes`。

**验收：** 一张验收后缺陷卡能留下可复现 Defect、回归验证、知识 stale 记录与一个
人工批准的 revision，而不是覆盖历史知识。

## 7. 不做什么

- 不自动把模型总结写入 verified knowledge；
- 不因每个 stale 条目都阻塞整条串行队列；
- 不承诺静态扫描或模型能证明零影响；
- 不在 M1/M2 引入 Neo4j、GraphRAG、向量库或通用 Policy Engine；
- 不让“全量测试绿”替代对影响场景的明确验证。

先用 M1 和 M2 在真实客户卡验证命中率、额外耗时和漏验情况。只有轻量索引已经成为
检索瓶颈，才评估图存储或语义检索。
