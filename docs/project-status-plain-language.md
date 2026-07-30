# AI4SE Runtime 现状全景分析（大白话版）

> **写给谁看：** 想一次性把「蓝图画了什么、代码做到哪、现在能干啥、还不能干啥」摸清楚的人。  
> **截止版本：** **S0–S9 最小台阶已通**（2026-07-30）；交付通路在准真仓上有连续 DoD 证据；**不开** S8b/c / S10+。  
> **怎么读：** 先看第 1、2 章建立直觉，再按需翻后面细节。  
> **水位真源（冲突时以它们为准）：**  
> 1. `docs/current-support-status.md`（有/没有什么）  
> 2. `docs/build-pathway-playbook.md`（台阶 / DoD / Now）  
> 3. `docs/adr/0016-engine-as-scheduler-v0.md`（谁推进状态机）  
> **本文只做大白话说明书，不排期、不取代 Frozen。**

---

## 1. 先用一个比喻把整张图说清楚

把最终蓝图想成一座「无人值守软件工厂」：

| 工厂零件（蓝图里的词） | 人话 |
|------------------------|------|
| **Task** | 一张工单：要干什么、在哪个仓库、预算多少 |
| **Scheduler** | 车间调度员：排队、拆活、重试、派工 |
| **WorkItem** | 工单拆出来的每一道工序单 |
| **Worker** | 真正动手的工人（跑命令、改文件……） |
| **ExecutionContext** | 这张工单开工时允许看到的「工具箱快照」 |
| **Artifact** | 合格入库的半成品/成品 |
| **Checkpoint** | 存档点：崩了以后从哪一页接着干 |
| **Trace** | 监控录像 |
| **Capability / Plugin / Workflow / Graph / Knowledge…** | 外围设施（**今天明确不做产品**） |

**蓝图的完整梦想**：人丢工单 → 调度员拆工序 → 找工人 → 过权限 → 写存档 → 录像 → 交成品。

**今天实际建成的是什么？**

不是整座工厂，而是一条 **「闸门 Runtime + 交付旁路」**：

> Engine 串行推进窄通路 Task → 阶段门禁（StageGate）/ 人闸（澄清·批准）/ 摸底命中集 → Shell 或 FileEdit 工人动手 → 产物入库 → **能写** Checkpoint → 返回带 checkpointId 的结果。  
> Demo 另有 Analysis Bundle + 串行 Delivery（可种子门禁 kinds）；**≠** 独立 Scheduler / StageRunner。

用进度条粗算（主观沟通用，**不是**精确度量）：

```text
蓝图完整平台愿景（Scheduler/Resume/Claude/知识…）  ████████░░░░░░░░░░░░░░░░░░░░  约 20%～30%
「闸门 Runtime」S0–S9 最小台阶                     ██████████████████████████░░  约 90%～95%（最小）
准真仓连续交付通路（timeout / REST 等夹具）         ████████████████████████░░░░  已有证据；≠ 外部生产多仓
```

关键一句：

> **不是「只会画图」；也不是「工厂已营业」。**  
> **S0–S9「最小」已通电且可测；没有独立 Scheduler；Checkpoint 能写不能 Resume；没有 Claude 主路径；外部客户真仓仍未当作已交付。**

---

## 2. 三句话掌握「现在到哪一步」

1. **能：** `Runtime.submit` 跑通生命周期；Engine 内有 **最小** 人闸 / StageGate / 计划双产物 / 按 plan 验测 / Map+Search 摸底 / 双 Profile；Demo 可跑准真仓连续交付与 Analysis 停闸。  
2. **不能：** 独立 Scheduler / WorkItem 队列、Checkpoint **Resume**、Claude 编码工人、Graph / 知识引擎、外部生产多仓接入、产品化 UI / Workflow DSL。  
3. **定位：** **闸门优先的工程 Runtime（最小闭环）**，不是 Coding Agent，也不是完整无人值守平台。

---

## 3. 蓝图承诺了什么？代码兑现了什么？

对照冻结对象模型与 ADR-0015 / **ADR-0016**。

### 3.1 对照表（最重要的一张）

| 蓝图里的东西 | 文档状态 | 代码状态 | 你现在可以指望它吗？ |
|--------------|----------|----------|----------------------|
| Task 工单 + 状态机 | 冻结 | **有**；主路径子集 + `BLOCKED_POLICY` 最小人闸 | ✅ 能用（子集） |
| Artifact 产物 | 冻结 | **有**；内存；可 Commit/Abandon | ✅ 能用 |
| ExecutionContext | 冻结 | **有三态**；视图仍瘦 | ✅ 能用（瘦版） |
| Worker | SPI | Shell / FileEdit / Noop / Mock 等；不在 Kernel | ✅ 能用 |
| **Scheduler** | 蓝图中枢 | **无独立模块**；**Engine 暂代**（ADR-0016） | ❌ 不能说「有 Scheduler」 |
| WorkItem 队列 | 蓝图 | **无** | ❌ |
| Checkpoint | 关联 | **能写、能查；不能 Resume** | ⚠️ 写档 ≠ 续跑 |
| Trace | 关联 | 有；简陋 | ✅ |
| StageGate / 计划双产物 / Verify 接 plan | 手册 S5–S7 | **最小**已进 Engine | ✅ 最小；≠ 产品 UI |
| Discovery Map+Search | 手册 S8a | **最小**已进 Engine | ✅ 最小；≠ Graph |
| ProjectProfile（双栈） | 手册 S9 | **最小**文本 Profile | ✅ 最小；≠ 外部多仓 |
| Capability / Plugin / Workflow DSL | 外围 | **无** | ❌ |
| Graph / Knowledge Engine | 外围 | **无**（刻意不做） | ❌ |
| Claude / AI 编码工人 | 远期 | **无** | ❌ |
| 数据库持久化 | 常见设想 | **无**（内存） | ❌ |

### 3.2 「五大对象」今天实际长什么样？

```text
已落地核心：  Task · Artifact · ExecutionContext · Checkpoint(写) · Trace
执行面：      Worker SPI（Shell / FileEdit / …）
编排者：      Runtime Engine（= ADR-0016 的 v0「调度员职位」）
门禁（最小）： StageGate · 澄清/批准 Artifact · discovery.hit-set · plan 双产物
缺席产品：    Scheduler 服务 · WorkItem 队列 · Resume · Claude · Graph
```

**人话：** 调度员职位空缺已用 ADR 写死「工长（Engine）暂代」；**不要再口头说有 Scheduler。**

---

## 4. 现在这条流水线怎么转？

### 4.1 Engine 成功路径（窄通路）

```text
Runtime.submit(请求)
  → 建 Task / Context / Trace
  →（可选）StageGate：缺必要 Artifact ⇒ FAILED，不调 Worker
  →（摸底）DISCOVERY Map+Search → discovery.hit-set；缺口可进澄清 → BLOCKED_POLICY
  →（计划）可串行 PLAN_DESIGN → PLAN_TEST；EXECUTION 需 plan.approved 等
  → Worker（Shell / FileEdit …）
  → Artifact 入库 → Checkpoint 写入 → SUCCEEDED
  → RuntimeResult（含 checkpointId / workerId / goalType / durationMs …）
```

### 4.2 交付旁路（Demo，≠ StageRunner）

```text
Analysis Bundle（Facts→Context→Gap→Clarification→条件 Plan）
  或 sample-input / 准真仓串行：
DISCOVERY → PLAN → EXECUTION → VERIFICATION → REVIEW → DELIVERY
```

- 可与 Engine 门禁用 `DemoGateBundle` **种子** `discovery.hit-set` / `plan.approved` / `plan.test-strategy`。  
- **诚实边界：** 种子旁路 ≠ Engine 自己摸底；sample-input 跳过 Analysis ≠ §2.1 连续 DoD。

### 4.3 失败时

- Worker 失败 → 产物可 ABANDONED；工单 FAILED。  
- 缺门禁 Artifact → **不调 Worker** 直接 FAILED（S5）。  
- VERIFY 红测 / skip 不当成功（S7）。  
- Analysis：UNKNOWN / 模糊答 → **BLOCKED**，禁止写正式 Plan。

---

## 5. 它「已经可以支撑」到哪一步？

按建造手册台阶（**「✅ 最小」≠ 产品完工**）：

| 台阶 | 状态 | 人话 |
|------|------|------|
| S0 ADR-0016 | ✅ | 说清：Engine 推进；无 Scheduler；写 Checkpoint ≠ Resume |
| S1 Walking Skeleton | ✅ | 假工人闭环 |
| S2 真副作用 | ✅ | Shell + FileEdit；Production jar |
| S3 可观测 | ✅ 部分 | Result 含 checkpointId 等；Trace sink 仍弱 |
| S4 人闸 | ✅ 最小 | `BLOCKED_POLICY` + clarify / approve API |
| S5 StageGate | ✅ 最小 | 缺 hit-set 拒 PLAN |
| S6 计划双产物 | ✅ 最小 | design→test；无 approve 拒编码 |
| S7 Verify 接 plan | ✅ 最小 | 按 test-strategy；红测失败 |
| S8a 按需摸底 | ✅ 最小 | Map+Search；非 Graph |
| S8b/c | ❌ 锁定 | 无痛点不开 |
| S9 第二 Profile | ✅ 最小 | 两栈同 Runtime；**非**外部生产多仓 |
| S10+ Resume / Claude… | ❌ 锁定 | 未解锁 |

**交付通路（§2.1，与台阶并列）：**  
准真仓（如 `first-delivery-workspace`、REST 应力仓）上有 **连续 DoD 证据**；压测见 `docs/s0-s9-pressure-test-evidence.md`（Agent 申报；Reviewer 可抽检）。  
**仍不算：** 外部客户真需求线上压测。

**一句话：**

> **已经支撑到：闸门 Runtime 最小闭环 + 准真仓交付证明。**  
> **还撑不住：调度产品、Resume、AI 编码工人、知识/图谱、外部多仓营业。**

---

## 6. 仓库里都有啥？

```text
ai4se-common          公共 ID、错误分类、工人只读视图
ai4se-worker-api      WorkRequest / WorkResult / Worker
ai4se-kernel          Task / Artifact / Context / Checkpoint / Trace
ai4se-runtime-engine  Runtime + StageGate / Discovery / 人闸 API + 集成测试
ai4se-workers         Shell / FileEdit / Noop / Mock …
ai4se-demo            交付 Demo、Analysis Pipeline、准真仓 / 应力仓
ai4se-review-tools    评审包工具（不进 Kernel）
docs/                 手册 · 支持状态 · ADR · 证据 · Frozen
```

依赖规则不变：**引擎不许直接依赖具体工人实现**（ArchUnit）。

---

## 7. 工人家族

| 工人 | 人话 |
|------|------|
| **ShellWorker** | 白名单本地命令（含测/探活用例，仍非任意 shell） |
| **FileEditWorker** | 窄范围改文件（交付 Execution 常用） |
| **Noop / Mock** | 测编排 |
| **CommandWorker** | 兼容壳 → Shell |

**不是**通用 Shell，**不是** Claude。

---

## 8. 怎么长到今天（压缩时间线）

| 阶段 | 贡献 |
|------|------|
| 早期冻结 | 宪法 / 对象模型 |
| Sprint-6/7 | Walking Skeleton → Shell 真命令 |
| Production Input / FPD | jar + 外置 input 驱动 Runtime |
| Analysis Pilot | Facts≠Context；BLOCKED 禁 Plan |
| 通路验证 | DoD / 回退 / Stop；修 UNKNOWN·模糊答·残留 plan |
| S0–S9 最小 | ADR-0016 + Engine 门禁阶梯进测试 |
| 压测（不开新关） | 三形态准真需求 + Engine 复跑 |

仓库：https://github.com/InspireChina/ai4se-runtime

---

## 9. 不变式：代码在守 vs 墙上标语

### 9.1 主路径上代码在守的（含新增最小门禁）

- 做事有 Task；产物登记；Context 终态冻结  
- 状态跳转走合法表；领域改态经 Lifecycle（ArchUnit）  
- 副作用经 Worker  
- **StageGate**：缺约定 Artifact 不调 Worker  
- **人闸**：澄清/批准走 Artifact API + `BLOCKED_POLICY`  
- **VERIFY** 可按 plan 拒绝假成功  

### 9.2 仍未产品兑现的

- 独立 Scheduler / WorkItem 队列  
- Checkpoint Resume  
- Capability / Rule 平台、幂等键产品路径  
- Graph / Knowledge / Claude  
- Context 里知识/规则「分区视图」物化  

---

## 10. 可以说什么？不该说什么？

### ✅ 可以说

- Java 8 多模块闸门 Runtime；Kernel 边界干净。  
- **S0–S9 最小台阶有集成测试证据。**  
- Engine **暂代**调度（ADR-0016）；**没有** Scheduler 产品。  
- 准真仓上交付通路有连续证据；Analysis 能诚实 BLOCKED。  
- 刻意不做 Graph / Claude / Resume（未解锁）。

### ❌ 不该说

- 「已经有完整 Scheduler / Workflow / StageRunner」  
- 「Checkpoint 等于断点续跑」  
- 「S9 完成 = 已接外部生产多仓」  
- 「可以无人值守跑任意工程 / 已接 Claude 改仓」  
- 「sample-input 六阶段绿 = Requirement→Delivery 连续 DoD 已通」  
- 「✅ 最小 = 产品完工」

---

## 11. 技术债（更新后仍成立的）

1. **双轨交付：** Demo 串行 + `DemoGateBundle` 种子 vs Engine 真摸底/真门禁——旁路已标明，但认知成本仍在。  
2. **状态机枚举仍宽于产品路径：** 有名字 ≠ 都已产品化（Resume 等仍锁）。  
3. **「最小」叙事膨胀风险：** 对内要用支持状态页；对外禁止把最小说成营业。  
4. **大白话本文曾滞后：** 以 `current-support-status.md` 为准；本文已按 2026-07-30 水位重对齐。

~~旧债「RuntimeResult 无 checkpointId」~~ → **已缓解（S3 部分）**。  
~~旧债「谁调度说不清」~~ → **ADR-0016 已对齐契约（仍无 Scheduler 模块）**。

---

## 12. 最短上手验证

```bash
cd /Users/peng.lv/IdeaProjects/ai4se-runtime
export JAVA_HOME="$HOME/Library/Java/JavaVirtualMachines/corretto-1.8.0_502/Contents/Home"

mvn clean test
mvn -pl ai4se-demo -am package
java -jar ai4se-demo/target/ai4se-runtime.jar \
  --workspace ai4se-demo/sample-workspace \
  --input ai4se-demo/sample-input

# 准真仓连续 DoD（示例）
mvn -pl ai4se-demo exec:java -Ddemo.mainClass=com.ai4se.runtime.demo.delivery.FirstProductionDeliveryMain

# Analysis 门禁
mvn -pl ai4se-demo exec:java -Ddemo.mainClass=com.ai4se.runtime.demo.analysis.PathwayVerificationMain
```

建议阅读顺序：

1. 本文（直觉）  
2. **`docs/current-support-status.md`（防假完成）**  
3. `docs/build-pathway-playbook.md`（台阶 / Now）  
4. `docs/adr/0016-engine-as-scheduler-v0.md`  
5. Frozen：`docs/architecture/`；`docs/_archive/` 当博物馆  

---

## 13. 总判断（收束）

```text
┌──────────────────────────────────────────────────────────┐
│ 蓝图：无人值守工厂（Scheduler·Resume·Claude·Graph·知识…） │
└──────────────────────────────────────────────────────────┘
                         ▲ 仍远；未解锁不开
                         │
┌──────────────────────────────────────────────────────────┐
│ 现在：闸门 Runtime S0–S9「最小」+ 准真仓交付证据           │
│ Engine 推进 · StageGate/人闸/摸底/双 Profile · 写 Checkpoint│
│ Demo 旁路互补 · 无 Scheduler 产品 · 无 Resume · 无 Claude  │
└──────────────────────────────────────────────────────────┘
```

### 最终大白话结论

1. **蓝图仍大**；实现按手册把 **最小闸门台阶** 钉到可测。  
2. **最高已证点：** S0–S9 最小 + 准真仓连续交付证据；Analysis 停闸可靠。  
3. **正确心理模型：**

> **「宪法已写、闸门 Runtime 最小闭环已通电；工厂尚未对外营业；下一刀优先外部真仓压测，不优先开 S8b/c / Resume / Claude。」**

4. **Now（与手册一致）：** 停在 S0–S9 压测 / Reviewer 抽检；**不开新关**，除非真需求反复打出可复现痛点。

---

## 文档信息

| 项 | 值 |
|----|----|
| 标题 | AI4SE Runtime 现状全景分析（大白话版） |
| 对应代码水位 | **S0–S9 最小**（对齐 `current-support-status.md`） |
| 真源同伴 | `current-support-status.md` · `build-pathway-playbook.md` · ADR-0016 |
| 维护建议 | 「能做什么」变化时先改支持状态页，再改本文第 2、5、10 章；**勿另开平行现状文** |
| 上次对齐 | 2026-07-30（消除 Sprint-7 过期叙事） |
