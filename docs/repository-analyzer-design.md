# Repository Analyzer Design（Sprint-8 · 不实现 Graph）

> **Status:** 设计契约 · 非实现  
> **禁令：** 不改 Runtime / Worker SPI；不新增 Scheduler / Workflow / Knowledge / Graph Engine  
> **位置：** Requirement Analysis / Discovery 侧能力 —— **不是** Kernel Object  
> **同伴：** `requirement-analysis-contract.md` · `engineering-delivery-contract.md` · `repository-context-contract.md`  
> **注意：** Analyzer **只输出 Facts**；**Context 由 Analysis Pipeline 构建**（见 Context 契约）。不在本文实现。

本文回答：

> **如何抽象「看懂仓库现状」，而不先造 Repository Graph？**

---

## A. RepositoryAnalyzer 的职责

**一句话：**  
在给定工作区上，产出 **只读、可审计的仓库事实**，供 Discovery / Gap 使用。

| 负责 | 不负责 |
|------|--------|
| 回答「项目现在是什么样」 | 回答「应该怎么实现」 |
| 路径/模块/依赖/符号的**可复查证据** | Planning、补丁、编码 |
| 在预算内做检索与摘要 | 全仓永久知识库、学习、进化 |
| 输出稳定 **Analysis Result** 形状 | Task 生命周期、Artifact 提交、Checkpoint |
| 可被 CLI / Analysis Pipeline 调用 | 成为 Runtime 内置阶段引擎 |

**非职责（写死）：** 不是 Scheduler、不是 Workflow、不是 Knowledge Engine、不是 Graph Engine。

---

## B. 输入 / 输出

### 输入（最小）

| 字段 | 必须 | 说明 |
|------|------|------|
| `workspaceRoot` | 是 | 仓库根路径 |
| `query` / `hint` | 否 | 来自 Requirement Spec 的关键词、模块名、路径暗示 |
| `budget` | 否 | 时间/文件数/输出体积上限（防全仓扫爆） |
| `mode` | 否 | `map` \| `search` \| `skip-probe`（默认 map+search） |

不输入：Plan、补丁、Approval（那些属于后续阶段）。

### 输出（Analysis Result · Delivery 层文档/结构，非新 Kernel 类型）

| 产物 | 含义 | 对应 Discovery 契约 |
|------|------|---------------------|
| `repo.map` | 粗粒度地图：顶层模块/主要源码根/构建文件位置 | 「现在什么样」 |
| `hit.set` | 与 hint 相关的路径/符号 TopK + 短摘录 | discovery.hit-set |
| `facts` | 可引用事实列表（如「存在 pom.xml」「包 com.example.api」） | discovery.report 主体 |
| `gaps.hints` | Analyzer **不能断定**、建议交给 Gap 的疑点（不是实现建议） | 流入 Gap Unknown |
| `meta` | 所用策略、耗时、是否截断、工具版本 | 审计 |

**禁止出现在输出中的内容：** 设计方案、任务拆解、推荐 API、补丁正文。

---

## C. 与 Runtime / Delivery / Worker 的边界

```text
Requirement Analysis Pipeline
        │
        ▼
RepositoryAnalyzer   ← 本抽象（Delivery/Analysis 库或进程）
        │  可选：通过现有 Worker 跑只读命令（pwd/git/未来 allowlist）
        │  或：Analyzer 进程内只读扫盘（不经 Runtime）
        ▼
Discovery / Gap 产物
        │
        ▼
Planning → Approval → Delivery Bundle
        │
        ▼
Runtime.submit × N → Worker（Execution/Verify 等）
```

| 组件 | 关系 |
|------|------|
| **Runtime** | **不拥有** Analyzer；不新增 Kernel 对象；不把 Analyzer 嵌进 `submit` 生命周期 |
| **Delivery / Analysis Pipeline** | **拥有并调用** Analyzer；消费其输出写 Discovery/Gap |
| **Worker** | **可选执行器**：只读 shell（如 `git status`）；**不**扩展 SPI；Analyzer ≠ 新 Worker 种类也可（纯库扫盘） |
| **Artifact** | Analyzer 输出先是 Delivery 文件；若某步经 Runtime 跑命令，则另有 Runtime Artifact（stdout），语义仍属 Discovery |

**边界检验句：**  
Runtime 永远可以不知道 RepositoryAnalyzer 的存在；只要 Delivery 喂给它的 Bundle 合法即可。

---

## D. 第一版最小实现应该是什么？为什么不是 Graph？

### v0（建议）：**Map + Search（无图）**

1. **Repo Map：** 列举约定根（`src/`、`**/pom.xml`、`**/package.json` 等）→ 模块粗表。  
2. **Search：** 按 Requirement hint 做路径/文件名/全文关键词 TopK（可用 JDK 遍历 + 简单匹配；或调用已 allowlist 的只读命令）。  
3. **Facts + gap.hints：** 从 map/search 生成事实与「仍不清」列表。  
4. **Budget：** 文件数/字节数封顶，截断时 `meta.truncated=true`。

### 为什么不是 Graph？

| 理由 | 说明 |
|------|------|
| **无痛点证据** | 当前 Delivery 失败点是 Gap/人闸/Bundle，不是「缺图查询」 |
| **难验证** | Graph「做完」难证伪；Map+Search 的 TopK/截断可测 |
| **过早平台** | Graph Engine ≈ 新子系统，违反本 Sprint 禁令与「先 YAML/检索」解锁规则 |
| **Discovery 契约** | 只要「现状证据」；图是一种**后端**，不是第一张脸 |
| **成本** | 建边、增量更新、失效比 Map+Search 高一个数量级 |

**Graph 未解锁前：** 任何「先上 Neo4j/自研图」都视为 Architecture Drift。

---

## E. Graph 在未来属于 Analyzer 的哪个能力？

Graph **不是**并列的第四系统，而是：

> **RepositoryAnalyzer 的一种可选后端 / 能力档位：`structure.query`**

```text
RepositoryAnalyzer
  ├─ capability: map          ← v0
  ├─ capability: search       ← v0
  ├─ capability: facts        ← v0
  └─ capability: structure.query  ← 未来；可用 Graph（或等价索引）实现
         仅当：Map+Search 可测地失败（召回/影响面分析反复错）
         且：有最小证明 Graph 比 Search 降失败率
```

即：**Graph ⊆ Analyzer 的进阶能力**，不是 Runtime 的新 Domain，也不是独立 Knowledge 平台。

---

## F. Architecture Drift 检查

| 检查 | 本设计 |
|------|--------|
| 是否新增平台？ | **否**（若实现为 Delivery 侧库/CLI 步骤）；**是**若做成独立 Graph/Knowledge 服务却无解锁 |
| 是否新增 Kernel？ | **否** · 禁止 |
| 是否提前抽象？ | **Analyzer 接口** = 正当收口（多入口同一 Discovery 事实源）；**Graph** = 提前抽象 · 本 Sprint 不做 |
| 是否改 Runtime / Worker SPI？ | **否** |
| 是否 StageRunner/Workflow？ | **否** |

---

## G. Next Recommendation（仅一个）

**在 Delivery/Analysis 层实现 RepositoryAnalyzer v0（Map + Search + Budget），并挂到 Discovery：输出写入 Bundle，供 Gap 消费。**

- **为什么：** 统一「现状事实」来源，服务方案 B（合法 Bundle），且满足 Discovery「只描述现在」。  
- **为什么不是 Graph / Knowledge / Runtime 改造：** 无解锁证据；违反本 Sprint 与 Boundary。

---

## 文档信息

| 项 | 值 |
|----|-----|
| 文件 | `docs/repository-analyzer-design.md` |
| Sprint | 8 · 不实现 Repository Graph |
| 下一步 | 仅当采纳 G 时才编码；编码仍不进 Kernel |
