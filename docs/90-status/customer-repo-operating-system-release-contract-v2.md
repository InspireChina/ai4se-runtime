# AI4SE 客户仓交付操作系统：Release Contract v2

> 本文是实现与验收的唯一产品基线。它不描述“模型能做什么”，而描述客户团队怎样安全、连续、可复核地使用 AI4SE 完成需求卡。任何命令、文档或宣传与本文冲突时，以代码可证明的行为为准。

## 1. 产品定义

AI4SE 是运行在**客户代码仓旁边**的软件交付控制面（delivery control plane）。

它不是 Agent 团队，不是聊天记录管理器，也不是让模型自由拥有仓库的自动编码器。它把人的需求、附件和业务决定变成冻结的阶段输入；把模型作为可替换的受限执行器；以工作流状态、写入范围、独立验证和人工验收决定是否能交付。

成功不等于模型回答得好，成功的最小定义是：

```text
客户仓事实可复查
→ 原始需求与附件可冻结
→ 不清楚时提出可回答的问题并保存停点
→ 人回答后由模型重新分析
→ 人批准可读的计划
→ 受限开发、独立验证、有限缺陷回环、Review
→ 只在本地 commit，等待人工验收
```

人负责业务选择、范围授权、计划批准和最终接收；AI4SE 负责过程与证据；模型负责受限的分析、设计、编码、修复与评审。

## 2. 目标用户旅程

### 2.1 客户仓一次性摸底

用户在 OMP/IDE 中选择客户仓并执行 `onboard`。控制面必须在客户仓产生可回查的：

- 已验证的 build/test/start 入口及最近结果；
- 技术栈、模块、主要入口和风险面的事实地图；
- 架构/业务知识草案和 Unknown 清单；
- 可由人确认后启用的规则与知识索引。

“创建空目录”不算摸底完成；“模型猜出的业务知识”也不算知识库。每个事实必须有文件、命令输出或源码路径来源。

### 2.2 一张卡的互动与无人值守边界

用户可粘贴文字、选择需求文件或附上截图/原型。入口将原始材料保存为不可变快照。若规格尚不能开发，Analysis 必须输出带编号的澄清问题、候选选项、推荐项、依据和未回答影响。人回答后，回答作为下一次 Analysis 的 P1 输入，重新得出 `CLEAR` 或新的问题；不能因存在回答文件而直接放行。

当需求清楚，系统展示计划、变化地图、影响声明、测试策略和每条 AC 的候选探针。人先审阅并冻结探针、再显式批准计划。从批准开始，Development → Verification → 有界缺陷修复 → Review → 本地提交可以无人值守。系统永不自动 push、合并、部署或伪造最终验收。

### 2.3 多卡行为

首个可用版本支持**串行队列**：多张卡可独立摸底/分析并进入待澄清或待批准状态；执行器一次只消费一张已批准且无依赖冲突的卡。批量模式遇到业务问题立即停该卡，不等待 stdin，也不猜答案。

并行开发不是默认能力。只有 Change Map、数据库/API 合约和共享测试资源均被判为不相交时，才作为后续版本的受控实验。

## 3. 什么叫“更好”：不可妥协的标准

| 维度 | 更好的含义 | 可验证标准 |
| --- | --- | --- |
| 需求忠实 | 代码实现的是已批准的业务意图 | 每条 AC 有独立 `PROVEN`/`FAILED`，不能用编译成功替代 |
| 可控性 | 模型不能悄悄扩大问题 | 改动路径是批准 Change Map 的子集；越权即停止 |
| 上下文质量 | 模型看到刚好足够、权威且可追溯的材料 | P1 含需求、AC、冻结决定、范围、规则和当前任务；P2 可裁；每项有来源/SHA |
| 工程健康 | 下一张卡不会被这一张卡拖坏 | 客户声明的验证入口通过；规则/Review 无阻断；没有无关 diff |
| 修复可靠 | 修 Bug 不丢前面的约束 | 每轮保留同一 requirement/AC/plan/constraint SHA；Defect 只是增量输入 |
| 可恢复 | 重启、换模型、隔天继续仍不改变事实 | ledger、状态、输入 SHA、adapter provenance 一致；恢复不重放已完成副作用 |
| 使用成本 | 人只在需要业务判断时介入 | 不要求手改 `.story`、伪造 ack 或整理模型上下文；界面/CLI 给出下一步 |

任何“优化”若只让 prompt 更长、测试数更多或输出文件更多，却不能改善上述至少一项指标，不能进入默认路径。

## 4. 模型交互 Contract

模型每次只接收一个阶段工作单，而不是历史对话全文：

```text
Role Contract
+ P1 Control Card: 目标、AC、已批准的决定、Allowed Files、强制规则、当前任务
+ P2 Evidence: 相关知识、代码/测试范例、直接依赖、当前 Defect
+ Read Scope: 可按需只读探索的模块
+ Output Contract: 必须写出的结构化产物与禁止事项
```

P1 缺失、被裁剪或 SHA 不一致时不调用模型。P2 超预算先裁日志和远端历史，不裁 AC、范围、阻断规则或 Defect 的根因。模型可在 Read Scope 内即时检索；它不能把工具读到的散文覆盖 P1，也不能写出 Write Scope 之外的文件。

附件必须保存 MIME、SHA、用途和所需角色。支持视觉输入的 Adapter 必须被明确要求读取并在分析中引用；不支持时必须提出文本澄清，不能把“附件索引存在”当作“已理解图片”。

## 5. 客户仓制品 Contract

`Markdown` 保存人/模型可读的解释；`.properties` 保存 Java 8 可稳定解析的状态；脚本/JUnit 保存可执行证明。一个结论只有一个权威源，其余制品只引用路径和 SHA。

| 位置 | 最小权威制品 | 责任 |
| --- | --- | --- |
| `.ai4se/repository/` | entries、facts、module-map、baseline | 可复查仓库事实 |
| `.ai4se/knowledge/` | business/architecture/domain/data 知识 | 有来源的长期语义 |
| `.ai4se/rules/` | 工程、领域、前端规则及适用范围 | 约束模型和 Review |
| `.story/<id>/input/` | raw request、frozen requirement、附件 manifest | 人发起的不可变输入 |
| `.story/<id>/analysis/` | discovery、gap、clarification request/answer/resolution | 是否可计划的事实 |
| `.story/<id>/planning/` | plan、change map、impact、test strategy、approval | 可批准的实现契约 |
| `.story/<id>/verification/` | 命令结果、AC matrix、probe 结果、defect | 独立正确性证明 |
| `.story/<id>/review/`、`delivery/` | decision、风险、commit、rollback、handoff | 交付与人工接收 |

API、数据、迁移、前端说明不是每卡必写的空模板。Planning 必须做出 `present` 或 `not_applicable` 的影响声明；前者才要求相应详细文档。

## 6. 发布验收门槛

以下每项都必须有自动化测试和至少一次真仓证据：

1. `onboard` 输出真实事实地图和明确 Unknown；空槽位不能宣称完成。
2. Story 输入、附件、baseline、AC 和 probe 在开发前冻结，且 probe 覆盖数与 AC 数一致；Plan 前不要求猜测 probe，Plan 后缺 probe 停在 Planning。
3. BLOCKED 必有具体问题；回答后必须经模型重新分析；支持多次停—答—继续。
4. Plan 在开发前明确 Change Map、测试策略、影响声明和批准记录。
5. 生产执行只用注册 adapter；恢复时 adapter、约束和预算连续。
6. 每个 AC 均由冻结 probe 或等价独立证据证明；命令成功本身不足以交付。
7. 失败最多修复设定轮次；重复失败指纹或范围越界诚实停止。
8. PASS Review + 全部 AC PROVEN 才能 local commit；无自动 push。
9. `accept`/`reject` 是显式人工动作；未接受的卡不可写回长期知识。
10. 串行队列不得让 BLOCKED 卡阻塞其它已批准卡，也不得并行修改有冲突的卡。

## 7. 当前基线的审阅结论

已证明：Java 状态机、ledger、adapter 隔离、范围门禁、冻结 probes、PASS-only delivery、计划批准后的无人交付，以及 R-003 真仓 local commit。

尚未达到本 Contract：真实仓库摸底知识生成、原始文本/图片 Story intake、结构化多问题澄清、影响声明/API-Data 文档门禁、人工 accept/reject 命令、串行队列，以及产品入口与 OMP 的桥接。

## 8. 实施顺序

1. 先完成客户仓事实/知识摸底与 Story intake；
2. 再完成澄清/计划/影响声明的交互控制面；
3. 再完成 accept/reject 与串行队列；
4. 每一步均用 Contract/Scenario tests；最后用新的客户真仓卡验证完整路径。

禁止为完成本文引入通用 Graph Engine、Agent swarm、向量数据库、消息队列或自动发布。它们没有直接提高本 Contract 的通过率。
