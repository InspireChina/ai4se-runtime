# Context Package Execution Handbook · 阶段上下文执行手册

> **状态：目标执行 Contract。** 本文把“每个阶段模型该看什么、以什么形式看、超预算如何处理”落成 Builder 的执行规则。它服务于 [客户仓交付操作模型](../00-product/customer-repo-delivery-operating-model.md)，不替代其中的 Workflow、审批或 Verification Contract。
>
> 上位规范：[Context Engineering Specification](./context-engineering-spec.md) · [Context Builder Contract](./context-builder-contract.md) · [阶段合同](../30-delivery-orchestration/workflow/stages/README.md)

---

## 1. 目标：完整性不等于把全文塞给模型

一次模型调用要同时满足三件事：

1. **不偏移**：需求、验收、范围、工程规则和业务不变量不能在开发或修 Bug 时消失；
2. **够局部**：模型只读本轮任务、相关代码、相关测试和最小失败证据；
3. **可重建**：每段文字能追溯到客户仓的文件、行区间和 SHA，换模型/重启后能得到同一工作包。

因此，Builder 不能把“原冻结需求 + 全量 Plan + 全量规则 + 全部日志”原样拼接。它应当输出：

```text
不可变源文件（完整、带 SHA、供审计）
        │
        ▼
确定性提取 / 已批准的精简卡片
        │
        ▼
本阶段最小 Context Package（模型真正看到的内容）
        │
        └── manifest.yaml 指回所有完整源文件
```

完整源文件保证不丢语义；精简卡片保证模型不被冗余文字和旧日志淹没。**模型永远不能以“上下文太长”为理由自行改写 Acceptance、规则或范围。**

---

## 2. 包的统一形状

每次 Adapter 调用都写出一个只读 Package。目录名可实现演进，以下语义和编号不可省略：

```text
.story/<story-id>/packages/<stage>/<attempt-id>/
├── manifest.yaml                 # 来源、SHA、预算、选择/裁剪理由
├── 00-role-output-contract.md    # 本角色只允许做什么、必须产出什么
├── 10-control-card.md            # 不可变目标、AC、范围、规则和完成定义
├── 20-task-card.md               # 本次唯一任务：分析/设计/实现/修复/评审
├── 30-workspace-slices/          # 相关代码、接口、测试的带路径/行号切片
├── 40-evidence/                  # 最小命令、probe 摘要、错误/Defect 证据
└── 90-p2-index.md                # 可选材料目录；只有被选择的正文才会装入
```

`manifest.yaml` 是机器审计源，至少包含：

```yaml
story_id: ORD-102
stage: development
attempt: defect-02
baseline_commit: <git-sha>
input_sources:
  - path: requirement.md
    sha256: <sha256>
    use: control-card.acceptance
  - path: planning/effective-constraints.properties
    sha256: <sha256>
    use: control-card.rules
selection:
  - source: litemall-admin-api/.../AdminOrderService.java
    lines: 42-138
    reason: changed symbol and defect stack frame
budget:
  soft_tokens: 18000
  hard_tokens: 24000
  included_tokens: 0
  truncation: []
```

模型读到的是实际装入的卡片与切片，不是仅有路径的索引。路径、行区间和 SHA 用于人和系统复核，并防止“恢复后拿到不同版本代码”。

---

## 3. 四类上下文：稳定控制与局部工作分离

| 类别 | 形态 | 是否每次写代码都提供 | 典型内容 |
|---|---|---|---|
| 不可变控制 | `10-control-card.md` | 是，P1 | 目标、精确 AC、已解决澄清、允许/禁止范围、有效规则、完成定义 |
| 本次任务 | `20-task-card.md` | 是，P1 | 当前要实现的一步，或当前 Defect 的期望/实际/复现 |
| 局部工作区 | `30-workspace-slices/` | 是，P1 | 目标类/组件、直接调用者、接口、邻近测试、可信范例 |
| 补充证据 | `40-evidence/` 和 `90-p2-index.md` | 按需 | 最小日志、知识正文、架构补充、相邻领域说明 |

### 3.1 `10-control-card.md`：短而绝不丢失

它不是 requirement、plan 和规则的全文拼接，而是受 schema 约束的“执行宪法”。建议控制在约 **1,000–2,000 tokens**；若超过，应该拆 Story、拆模块规则或修正规格，而不是静默截断。

```markdown
# Development Control Card · ORD-102 · <bundle-sha>

## Mission
<一到两句业务目标；引用 requirement SHA>

## Acceptance (exact)
- AC1: ...
- AC2: ...

## Frozen decisions
- Q-01 = A：管理员可执行确认收货。

## Allowed / forbidden
- allowed: path/a, path/b
- forbidden: acceptance probes, .ai4se/rules, schema migration

## Effective constraints
- R-12 [blocker]：写操作必须沿用现有管理员鉴权；范例：path:line
- R-18 [blocker]：Service 不直接调用跨层 Repository。
- required checks: <commands>

## Definition of done
仅当所有 AC probe、入口验证和范围检查通过，才可宣称实现完成。
```

这里保留所有 AC 的精确表述，因为它们是行为边界；保留命中的 blocker/required 规则和范例指针，而不是把整个规则库塞入。`Effective Constraint Bundle` 的完整 YAML/Markdown 仍带 SHA 存在上游，供 Review 和审计复查。

### 3.2 `20-task-card.md`：每次只给一个可完成任务

首次 Development 任务来自已批准 Plan 的一个最小实现单元，例如“在现有订单详情 DTO 投影已允许操作；不实现状态变更 API”。一个 Task Card 必须说明：

- 本轮要达成的 AC 子集和不可触碰的 AC；
- 可改的具体符号/文件及计划步骤；
- 本轮应运行哪些命令；
- 明确的非目标与停止条件。

一轮只允许一个清晰目标。若 Plan 把四个独立子域塞进同一轮，Builder 应拆 Task，而不是让模型在一个超大回合里“顺便做完”。

### 3.3 Defect Task Card：增量，不替代控制卡

Defect 修复的 `20-task-card.md` 只增加当前失败的最小可复现事实：

```markdown
# Defect D-02

## Failed acceptance
AC4

## Reproduce
<冻结 probe 命令>

## Expected / actual
非法操作应被拒绝并保持状态；实际服务接受了请求。

## Evidence
<首个错误、关键栈帧、输入数据、相关路径/行号>

## Repair boundary
可修改：<从 Change Map 选出的路径>；
不得修改：AC、probe、规则、requirement、批准范围。
```

它与**同一份** `10-control-card.md`、相同版本的 `Effective Constraint Bundle`、相同的冻结 AC 一起发送。这里的“同一份”指同一版本的**精简控制卡**，不是重复发送全量原始 Plan 和所有历史日志。

### 3.4 `30-workspace-slices/`：给证据和范例，不给整仓

切片选择应首先使用确定性信号，而非让模型先漫游仓库：

1. `change-map.yaml` 的目标文件与声明符号；
2. 当前 Defect 的栈帧、失败测试、probe 调用路径；
3. 目标符号的接口、直接调用者/被调用者和相邻测试；
4. 同模块中经人工/规则标明的可信范例；
5. 必要时才加入最小跨模块协议或数据模型。

每个切片必须带路径、提交 SHA、行区间和选择理由。优先保留方法/类签名、控制分支、异常处理、接口契约和邻近测试；删除重复 import、无关大方法、整段重复生成代码和格式化噪声。若靠这些切片仍无法正确判断，停止并要求补 Facts/Knowledge/Clarification，不开放全仓漫游作为默认补救。

### 3.5 `40-evidence/`：日志只保留诊断最小集

日志必须先被非 AI 的规则裁剪：保留执行命令、exit code、首个根因、关键栈帧、期望/实际、相关输入、失败 probe ID；删除进度条、重复重试、无关模块日志、密钥和长堆栈重复段。复杂失败可附完整日志的 SHA/路径供 operator 查看，但默认不进入模型包。

---

## 4. 阶段装包矩阵

| 阶段 | `control-card` | `task-card` | 工作区/证据 | P2 | 输出 |
|---|---|---|---|---|---|
| Analysis | 冻结 goal/AC、附件清单、适用领域不变量、baseline | “识别影响面和未知，不给实现方案” | Facts、模块图、相关 API/数据/测试摘录 | 匹配知识正文 | Context、Gap、结构化问题 |
| Clarification | 需求/AC、问题 ID、不可变边界 | “只处理这些人答复并重判 Unknown” | 人答复原文、问题证据 | 相关知识 | Resolution、更新 Context、Gap |
| Planning | 精确 AC、澄清结论、规则、baseline | “形成可批准的最小变更方案” | 候选代码/测试/范例 | 架构/业务知识 | Plan、Change Map、Test Strategy、Constraint Bundle |
| Development | 完整 Control Card | 一个实现单元 | 目标代码、接口、直接调用链、邻近测试、probe 调用契约 | 最小领域知识 | 受限 diff、局部测试结果 |
| Defect repair | **与 Development 相同的 Control Card** | 一个 Defect | 失败符号及相关局部代码、最小失败证据 | 相关范例 | 受限 diff、重跑结果或无进展 |
| Verification | AC/probe、正式入口、范围/规则版本 | “执行并记录，不自评” | 冻结 probe、命令输出 | 无 | 验收矩阵、Defect |
| Review | Control Card、Plan、AC 矩阵、范围 | “判断规范/架构/风险，不改代码” | 最终 diff、检查结果 | 相关范例 | 结构化 PASS/CONDITIONAL/REJECT |

---

## 5. 预算、裁剪与失败策略

### 5.1 预算是分区预算，不是一条总长度

不同 Adapter、语言和任务需要不同上限，不能把某个模型的上下文窗口当成质量目标。第一版以可配置的软/硬预算开始，建议 Coding 阶段采用以下**占比和目标**，并以真实卡的完成率/耗时校正：

| 分区 | 建议目标 | 裁剪权 |
|---|---:|---|
| Control Card | 1k–2k tokens | 不可裁剪；过长则拒跑/拆分 |
| Task Card | 0.5k–1.5k tokens | 不可裁剪；任务过大则拆分 |
| 代码/测试切片 | 6k–12k tokens | 可按调用距离、符号和范例优先级裁剪 |
| 最小失败证据 | ≤2k tokens | 可去重，不可丢根因/期望实际 |
| P2 补充 | 总包剩余的 ≤20% | 首先删除 |

可把单轮 Coding Package 的初始软上限设为约 18k tokens、硬上限约 24k tokens；这只是起始配置，不是产品常量。模型快、代码少时可更小；真实卡证明需要更大调用链时可调整。重要的是每个分区独立计量、manifest 可审计，不能以“模型支持 128k”就无上限装入。

### 5.2 固定裁剪顺序

1. 删除未被本轮选择的 P2 正文，仅保留索引；
2. 删除重复日志、重复代码和历史轮次流水账；
3. 将代码切片缩到目标符号、直接接口、直接调用链和邻近测试；
4. 用已批准的 Execution Card 取代 Plan/Rule 全文，但保留精确 AC、命中规则、范围和 SHA 引用；
5. 若 Control/Task Card 仍无法容纳，停止为 `CONTEXT_CONTRACT_TOO_LARGE`：要求拆 Story、拆任务、补清晰规则或重新批准 Plan。

**不得**裁剪精确 AC、当前 Defect 的期望/实际、允许/禁止范围、命中 blocker 规则、冻结澄清结论、必跑命令或来源 SHA。

### 5.3 何时重新装包

| 事件 | 是否复用旧 Package | 动作 |
|---|---|---|
| 同一开发轮次内的工具调用 | 是 | 固定 manifest；只追加工具输出摘要 |
| 新 Defect | 否 | 复用同一 Control Card，重建 Task/Evidence/代码切片 |
| 人回答澄清 | 否 | 新建 Clarification Package，重判 Context/Gap |
| Plan 或规则版本改变 | 否 | 人重新批准后生成新 Constraint Bundle；旧包只读保留 |
| Adapter 切换或隔天恢复 | 否 | 由同一冻结源和选择规则重建；比较 manifest SHA/差异 |

---

## 6. 防漂移执行门禁

每次 Development 或 Defect 调用前，Builder 必须校验：

1. Requirement、AC、澄清、Plan、Change Map、Constraint Bundle 指向同一 Story/baseline/version；
2. Control Card 的 AC/范围/规则 ID 与源文件逐项一致；
3. 目标代码切片来自当前 worktree，未越过允许范围；
4. probe 和规则目录不是可写目标；
5. Package 满足分区预算，且裁剪记录完整；
6. 模型输出契约只允许本阶段行为，Development 不得自评 Verification PASS。

调用后必须校验：实际 diff 是否在 Change Map 内；必跑命令是否真的执行；验收矩阵是否由独立 probe 写入；Review 是否使用同一 Constraint Bundle。任一不一致都停止，不尝试靠下一轮模型“补一补”。

---

## 7. 真仓调优方法

首三张卡不追求自动学习和复杂检索，而记录每个 Package 的：总 tokens、各分区 tokens、模型耗时、开发轮次、首次 AC 完成率、Defect 原因、模型引用过的切片、人工发现的遗漏。

每张卡只针对一个可证实问题调整：

- 模型误改无关文件：缩小 Change Map 或强化目标模块范例；
- 模型反复违反架构：把相应规则提升为 Control Card blocker，并增加自动/Review check；
- 模型看不懂失败：改进 Defect Card 的期望/实际和最小复现，不倾倒全量日志；
- 模型缺少业务判断：补可引用的 Knowledge 或进入 Clarification，不加更长提示词；
- 模型在复杂调用链漏改：扩大到直接协议切片并记录理由，而不是开放全仓。

一个调整必须在后续真实卡上证明改善，才晋升为平台模板或默认预算。

---

## 8. 实施验收

本手册落地时，至少应有以下自动化/集成测试：

1. 缺失任一 P1 源或源 SHA 不一致，Builder 拒绝调用 Adapter；
2. Defect Package 重建后，Control Card 与首次 Development 的版本、AC、范围、规则一致，只有 Task/Evidence/切片可变化；
3. P2/重复日志先被裁剪；不可裁剪字段超预算时停止而非静默丢失；
4. 每个切片都有来源、行区间、SHA 和选择理由；
5. 模型尝试修改 rule/probe/范围外文件时，Diff Gate 拒绝 Delivery；
6. Adapter 切换、进程重启后，能从冻结源重建语义等价 Package；
7. 至少一张真实客户卡的 Defect 回环证明：修复后原 AC、架构约束和范围仍成立。

达到这些条件，Context Engineering 才不是“积累很多文档”，而是给模型一份短、准、可执行、不可漂移的阶段工作说明书。
