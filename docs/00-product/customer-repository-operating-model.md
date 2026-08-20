# 客户仓交付操作模型

> AI4SE 是**客户仓的软件交付控制面**，不是一个自由行动的 Agent，也不是某个模型的 Prompt 集。
> 它把确定性事实、受控模型调用、人工业务决策、可复验测试和本地交付串成一条可恢复的链。

## 目标与边界

目标不是“让模型看完整个仓库后自行改代码”，而是让每张需求卡在客户仓内留下可对照的输入、决策、实现、验收和交付证据，并能在下一张卡继续利用经验证的知识。

| AI4SE 负责 | 不负责 |
| --- | --- |
| 阶段顺序、停止/恢复、上下文装配、写入范围、验收门禁、证据和本地 commit | 替业务方猜产品选择、替人批准、自动 push、把模型草稿当仓库事实 |
| 调可替换 Adapter（Codex/Cursor/Claude） | 把某个 CLI 或对话历史当工作流真相 |

模型是一个阶段 Worker：它得到有限且版本化的 Package，按输出合同产物；Control 校验产物并决定是否继续。模型失败、配额耗尽或产物不合格都只会停止在可解释的边界，不会被“再试一次”掩盖。

## 日常主链

```text
客户仓 clone
  → onboard（Java 确定性事实）
  → discover（模型候选知识，不能改业务代码）
  → 人工 approve-knowledge + 提交知识基线
  → intake → specify
       ├─ 信息不足：问题清单 → 人答 → specify 重评
       └─ 可执行：冻结 requirement / Plan / probes
  → 人批准 Plan
  → Development → Verification → Defect loop（有上限）→ Review
  → 本地 commit → 人验收
  → 仅标记受影响知识 stale → 人工 refresh/批准
```

### 第一次进入一个客户仓

```bash
java -jar ai4se-runtime.jar onboard \
  --workspace /path/customer-repo --runtime-root /path/ai4se-runtime

# 审阅并提交确定性扫描的 .ai4se/repository/ 与 .ai4se/index/ 槽位后：
java -jar ai4se-runtime.jar discover \
  --workspace /path/customer-repo --scope repository \
  --adapter codex --candidate first-repository-pass

# 人审 candidate.yaml 和 documents/，确认无误才晋升：
java -jar ai4se-runtime.jar approve-knowledge \
  --workspace /path/customer-repo --candidate first-repository-pass --actor tech-lead
git add .ai4se/knowledge .ai4se/index .ai4se/knowledge-candidates/first-repository-pass
git commit -m "docs(ai4se): establish verified repository knowledge"
```

`onboard` 不调用模型：它只扫描可复现事实（构建入口、模块、目录、依赖线索和 Unknown）。`discover` 才调用模型，但只接收这些事实和受限的代码读取权；它只能写 `.ai4se/knowledge-candidates/<id>/`。Control 比对开始/结束 HEAD 与工作区路径，任何越权写业务代码、Story、verified knowledge 或 index 都拒绝该候选。

## 初版知识库：不是长摘要，而是可检索的决策材料

初版建议保持 3–6 份文档，每份只覆盖一个稳定问题：

| kind | 回答的问题 | 必须包含 |
| --- | --- | --- |
| `system-context` | 系统边界、运行形态、外部依赖是什么 | Evidence、Unknowns |
| `module-boundary` | 模块职责、入口、调用边界和不可跨越点是什么 | Evidence、Unknowns、`refs` |
| `delivery-conventions` | Java/前端分层、命名、测试与提交约束是什么 | Evidence、Unknowns |
| `data-and-integration` | 数据表、消息、第三方集成的已知约束是什么 | Evidence、Unknowns |
| `testing-and-operations` | 哪些验证入口真实可用、何处存在环境限制 | Evidence、Unknowns |

正文放 `.ai4se/knowledge/<id>.md`；索引 `.ai4se/index/knowledge.yaml` 记录 `id/path/kind/tags/refs/source_paths/source_commit/source_sha256/status`。每一个结论必须能回指 `source_paths`；不确定性放 `## Unknowns`，不能伪装成结论。

`refs` 是轻量关系索引（模块、API、数据、Story 等命名边），用于检索、影响提示和冲突判断；它不是 Neo4j、GraphRAG 或 Graph Engine。图结构只有在跨文档关系查询已成为实际瓶颈时才升级。

## 一张 Story 的输入与落盘规格

| 产物 | 谁写 | 作用 |
| --- | --- | --- |
| `raw-request.md` + attachments | 用户/Intake | 不丢失原始表达和截图/原型 |
| `candidate-requirement.md` 或 `clarification.questions.md` | Specification 模型 | 形成可选项与推荐依据；不替人决策 |
| 冻结 `requirement.md` | 人确认 | Goal、in/out scope、Allowed、3–5 条可测 AC、附件声明 |
| `plan.md`、Change Map、Impact Assessment、API/Data change、probe candidate | Planning 模型 | 开发前可审的设计与验收映射 |
| 冻结 acceptance probes | 人确认 | 每条 AC 的独立、可执行判据；先在基线 RED/已有能力 GREEN 时披露 |
| `changed-files.md`、Verify report、Defect Package、Review sidecar、Delivery report | Control/模型各按合同 | 让实现、失败、修复、交付可以复查 |

需求含糊时，正确行为是停在 `clarification.questions.md`：每题给 2–4 个选项、推荐项、证据与不回答的影响。人答后，回答成为下一次 Analysis/Specification 的**显式 P1 输入**，模型必须重新判断，不是仅把一个 Markdown 文件“放在目录里”。

## 给模型什么上下文，才不会又慢又漂移

上下文不是越多越好。模型每阶段只得到“任务闭包”，由 Context Builder 形成版本化 Package：

| 层 | 内容 | 用法 |
| --- | --- | --- |
| P0 | manifest、SHA、预算、读取规则 | 审计与定位，不替代正文 |
| P1（必须读） | 冻结 Requirement/AC、约束包、当前阶段输入、命中且 verified 的知识摘要 | 直接嵌入 model-input，缺失即拒跑 |
| P2（按需） | 少量正文切片、直接调用者/被调用者、邻近测试、当前 diff | 有引用再读取；超预算先裁这一层 |
| 禁止层 | stale/candidate/retired 知识、全仓 dump、过往无关缺陷全文 | 不能作为当前事实 |

阶段闭包不同：Analysis 看需求、附件、事实地图、verified knowledge 和 Unknown；Planning 再看已解答澄清与影响面；Development 看冻结 AC、Plan/Change Map、Constraint Bundle、目标模块和邻近测试；Verification 看 probes 与实际 diff；Bug 修复只增加“当前 Defect + 相关 diff/日志”，**不替换**前述冻结约束。这样保留不变量，又避免每轮塞入完整历史。

跨卡一致性靠 machine-readable Contract（Allowed Files、Constraint Bundle、index 状态、冻结 SHA、probe manifest）和阶段重建 Package，而不靠模型记住长对话。每次交付后的代码变更会使引用了该源路径的 verified knowledge 标为 `stale`；它不会自动改写正文。下一张受影响 Story 只能检索 verified/active，需由人发起小范围 `discover --scope module:<id>`、审阅并批准新的知识版本。

## 并行与质量门

默认队列串行：一个客户仓在同一时间只自动写一张 Story。只有 Change Map、写入范围、数据库迁移/API 影响均被证明互不相交，且每张卡有独立 worktree、独立探针和可合并策略时，才允许并行开发；“模型数量足够”不是并行理由。

自动环只覆盖**已澄清并冻结之后**的 Development → Verify → Defect → Review → local commit，并受开发轮数、时间和无进展熔断约束。下列情况必须停给人：业务歧义、验证证据不足、范围扩大、越权写入、连续无进展、Review 非 PASS、或任何外部环境不可用。

质量不是“命令绿了”：Delivery 的最低条件是每一条冻结 AC 都有 `PROVEN` probe、入口验证成功、写入范围合规、Review=PASS。最终仍是本地 commit，等待人验收，永不自动 push。

## 演进判据

新能力先问：它是否消除了主链已观测到的失败，是否可用一张真实客户卡复验，是否缩短了人工澄清或降低了漏验率？答案不是两个“是”就只记录，不建新 Engine。当前明确不引入 Graph Engine、GraphRAG、向量库、全自动知识回写、自动 push 或按模型对话驱动的状态机。
