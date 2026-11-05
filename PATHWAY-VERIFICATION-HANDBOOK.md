# 通路验证手册 · Pathway Verification Handbook

> **按产品全局流程施工与验收。** 不是验证点百科。  
> 宪法：[docs/00-product/capability-map.md](./docs/00-product/capability-map.md) · 北极星：[docs/00-product/vision.md](./docs/00-product/vision.md)

**冲突时优先级：** capability-map 主链 + **本文车站 / Wave** > 阶段合同细节（字段/门禁真源）> `docs/90-status` 水位。  
阶段合同（如 Analysis）是 **字段与硬规则** 真源，**不是**第二套施工顺序。  
**编号勿混：** 本文 **S0–S6 = 交付车站**；Runtime/Engine 底座台阶见 playbook 的 **E0–E9**，二者不是同一套编号。

分级：

| 分级 | 含义 | 例子 |
|------|------|------|
| **必验** | 挡主链；不通过不能往下宣称 | 缺 P1 拒跑；BLOCKED 禁 Plan；Allowed 越权；AC 对照；Defect 回环；不 Push |
| **后验** | 主链通后加深 | Session 审计字段；压缩保留清单；换 Adapter；Rule 触顶；Lifecycle noop |
| **勿空验** | 有纪律价值，但不当通路 Gate | 见附录 B |

原则：先让「车站通」，再加深该站合同；**禁止**在 Adapter/回环未通前，用材料学/可观测学填满排期。

---

## 0. 产品要什么

**组织 AI 按同一套交付标准开发客户项目** —— 不替客户开发，不替代 Claude/Cursor。

**通路签收成功（对外宣称「通路通」）：**

> 新客户仓 + 新 Story → 默认无人值守主链 → **本地 Commit（或不 Push 的明确待人 commit）** → **人验收** →（可选）知识回写。  
> 稳定 · 可恢复 · 可验证 · 与模型无关。

**单 Story 合法终点（≠ 通路签收绿）：** 走到 Commit / awaiting，**或**带原因的明确 Stop（如 Clarification 熔断、预算耗尽）。Stop 可复查、可恢复，但 **不算** V3/V4 通路通。

**人闸白名单：** Clarification / Plan Approval / 人验收（及合同写明的高风险 Approval）。白名单外 **不得**靠聊天推进；默认自动往下走。

值钱层：**Context Engineering（每步装对 Package）**。  
Runtime / Worker / 某模型不是产品中心。

口语落点（防 Agent 叙事）：

| 你说的 | 产品里是 |
|--------|----------|
| Agent / 子 Agent | Workflow **阶段角色**；调度在 03，不在模型 |
| Session 切换 | 03 决定 Resume vs 重开；02 重建 Package |
| 压缩 / 清空 | 02 裁剪；清空聊天 ≠ 丢 `.story` |
| 测红再开发 | Defect → 回环 Dev；Defect 进 Dev Package P1 |
| 再回测试 | **要 Verify 新 Package**（AC+Diff+入口），不是 Dev 续聊，也不是空包裸测 |

---

## 1. 全局流程（唯一脊骨）

与 [capability-map](./docs/00-product/capability-map.md) 对齐：

```text
S0  08 启动（指向客户仓）+ 07 Frozen 不掺业务
S1  01 建槽（通常在主环外）
S2  开 Story（需求进 .story/<id>/）
S3  每个阶段：02 拼 Context Package → 04 Adapter 执行
S4  03 Workflow 推进阶段：
      Analysis（Spec → Discovery|skip → Gap → Clarification?）
      → Planning（Allowed Files + Approval?）
      → Development（限 Allowed；禁自评验绿）
      → Verification（05：客户测试；FAIL→Defect→回 Dev）
      → Review
      → Delivery（本地 Commit，不 Push）
S5  人验收
S6  06 知识回写（或明确无需）
```

**通路通** = 金样 Story 在 B 套仓跑完 S0–S5（S6 可后验），证据可复查，无假绿。  
明确 Stop 结束的 Story **不计入**通路通。

资产宿主（违反则整条通路无效）：

| 放哪 | 放什么 |
|------|--------|
| 客户仓 | `.ai4se/` · `.story/` · 源码 · 客户测试 · 业务知识 |
| 云桌面运行包 | 编排 · Builder · Adapter · Runtime · 模板副本 |
| 本仓 | 八域真源 · 模板 · 实现 · 文档 |

禁止：客户正文回流本仓；Push；Dev 自评验绿。

---

## 2. 建设 + 验证排序（Wave）

一次只推进一个 Wave。上一 Wave 的**必验**未过，不开下一 Wave。

| Wave | 打通车站 | 必验（过了才能往下） | 工作区 |
|------|----------|----------------------|--------|
| **W0** | 对齐 | 以本文 + capability-map 为准；附录 B 当纪律不当事 | — |
| **W1** | S0+S1 | 能启动并指向工作区；onboard 出槽位+入口（或显式 unknown）；Facts 无改码建议；kernel 无业务语义 | A |
| **W2** | S2+S3（仅 Analysis） | 能开 Story；Builder 为 Analysis 出 Package；**缺 Acceptance → 拒跑** | A |
| **W3** | S4 骨架 | 状态机能 Analysis→Planning（可停）；状态进 `.story`；**控制流不在模型口令** | A |
| **W4** | S3+S4 接 Adapter | 合法 Package 可交真实 Adapter；失败原样回 03；Adapter 内无跳阶段/Retry 控流 | A |
| **W5** | Analysis/Discovery/Gap/Plan 挂机 | Spec 有 AC；**有 Discovery 报告或合法 `discovery.skip`**；**BLOCKED 出不了正式 Plan**；Plan 含 **Allowed Files**；未批不得 Dev（可先人工闸，必须留记录） | A |
| **W6** | Dev | Diff 不越权；产物禁「测试已通过」；下一站只能 Verify | A |
| **W7** | Verify+回环 | **B 套**；命令∈入口；**仅编译≠PASS**；FAIL→Defect→回 Dev（Defect 进 P1）→再 Verify（**可审计的新 Verify Package 清单**）；Verify 不改业务码 | B |
| **W8** | Review+Delivery | 有 Review 结论（可先薄）；无 Verify 不得 Review；**Commit 不 Push**（或 awaiting_human_commit） | B |
| **W9** | Session / 压缩审计 | 角色切换默认重开 Session（决策字段可审计）；压缩不丢 AC/Allowed/Defect；不灌聊天流水 | B |
| **W10** | 人验收+S6 | 验收前 06 不写；回写或 noop；主环不每 Story 重 onboard | B |
| **W11+** | 附录 A | 换 Adapter、Rule 触顶、Lifecycle 加深、Resume 闪断、Discovery 合同加深… | A/B |

Wave 刻意顺序：

- 不开 W2 就「适用 Rule 永不丢 / 全材料合同」——那是 W11 后验。  
- **开 Story** 在 W2，与首包绑定。  
- Discovery/Gap/Allowed 放到 **状态机和 Adapter 能跑之后**（W5）。  
- Review 允许 **薄 Review**（结构化结论文件）。  
- W7 已要求「再测换 Verify Package」；**Session 决策字段 / 压缩保留清单** 留 W9，勿把 W7 理解成「同会话空跑以后再补」。  
- 真仓从 W7 才硬性需要。

---

## 3. 按车站必验（贴流程，不贴编号）

每站四栏：**产品职责 · 必验 · 揪偏 · 证据**。

### S0 · 启动 + Frozen

| | |
|--|--|
| **职责** | 一条命令起编排并指向客户/夹具仓；07 只提供调度骨头 |
| **必验** | 进程能起且 workspace 指向目标仓；`mvn test` 不引入交付方法论进 kernel |
| **揪偏** | Demo 绿宣称主链通；Checkpoint 能写就说已 Resume；Runtime 懂业务 |
| **证据** | 启动命令 + workspace 配置；Resume 水位如实标「未做」 |

### S1 · 建槽（01）

| | |
|--|--|
| **职责** | 数字化槽位 + 构建/测试入口；不思考 |
| **必验** | `.ai4se/`（含 index）+ `.story/`；入口有或显式 unknown；无 AI 改码建议 |
| **揪偏** | 空目录宣称完成；编造业务 Knowledge 当 Facts；客户正文进本仓；**主环每 Story 强制重 onboard** |
| **证据** | 目录树；entries；抽检 facts 无「应实现/建议改」 |

### S2 · 开 Story

| | |
|--|--|
| **职责** | 一条可验收需求进入 `.story/<id>/`，成为后续所有车站的锚 |
| **必验** | 有 story id；有 raw/goal/in_out_scope/**acceptance**（可先手写）；后续产物挂在此 id 下 |
| **揪偏** | 无 Story 直接改码；acceptance「看着办」仍往下走 |
| **证据** | `.story/<id>/` 初始需求文件 |

**与 Analysis Spec：** S2 = Story 锚与**种子**需求；Analysis = 正式 Spec。种子无 acceptance → W2 拒跑；Analysis **不得**用「看着办」盖过空验收。

### S3 · 每步装包（02）★

| | |
|--|--|
| **职责** | 有限 Token 下装最大有效信息；执行器只吃 Package |
| **必验** | 当前阶段 role 正确；P1 齐否则 **拒跑**；禁止项可审计；Knowledge **按 ID**（有则验，无 ID 体系前不得假装有智能召回） |
| **揪偏** | 缺 P1 仍开 CLI；全文灌仓；用超长 Prompt 绕过 Builder；让 CLI 自助全库检索当主路径 |
| **证据** | Package 清单 + 拒跑负例记录 |

**重复规则：** 每换阶段 / 每回环，都必须再走 S3，不是只在项目开头装一次。

### S4a · Analysis → Discovery → Gap →（Clarification）

| | |
|--|--|
| **职责** | 需求变可规划规格；摸底诚实；缺口诚实 |
| **必验** | Spec 含 acceptance；**有 `discovery.report` 或合法 `discovery.skip`（理由+批准）**；Gap 有状态；**BLOCKED 不能出正式 Plan**；本阶段不改业务源码 |
| **揪偏** | Spec 绿直接当可 Dev；无 skip 记录空转进 Plan；Blocking 改名 Assumable；Analysis 写 Patch |
| **证据** | Spec + Discovery/skip + Gap；BLOCKED 负例；`git status` 业务树干净 |

Discovery **深度合同**（命中集质量、何时必须摸底的细表）可 W11 加深；主链最低线是 **有报告或有 skip 记录**。字段真源：[analysis-contract](./docs/30-delivery-orchestration/workflow/stages/analysis-contract.md)。

### S4b · Planning → Approval

| | |
|--|--|
| **职责** | 可执行方案 + 文件面 +（可选）测试/风险 |
| **必验** | 有 **Allowed/Declared Files**；未 Approval 不得进 Dev（记录可先人工） |
| **揪偏** | 无文件面开工；Plan 内完整 patch 冒充已交付；跳过 Approval |
| **证据** | Plan + Allowed 列表 + 批准记录 |

### S4c · Development

| | |
|--|--|
| **职责** | 只在 Allowed 内改代码；不证明需求已满足 |
| **必验** | Diff ⊆ Allowed；无「测试已通过/可交付」；结束后交 Verify |
| **揪偏** | 越权改文件；自评验绿；修缺陷借机扩面 |
| **证据** | Diff 路径集；产物扫描 |

### S4d · Verification + Defect 回环（05 + 03 Control）

| | |
|--|--|
| **职责** | 调**客户**验证；FAIL 结构化回灌 Dev |
| **必验** | 命令∈入口清单；PASS/FAIL **对照 Acceptance**（仅编译≠PASS）；FAIL→Defect（原因/影响 AC/文件指针/建议与禁止范围/复现指针）→ Dev Package **含 Defect** → 再 Verify（**相对首轮可审计的新 Verify Package 清单**）；Verify **不改业务码** |
| **揪偏** | 空包裸测；Dev 续聊当再测；口头「修一下」无 Defect；Verify 顺手改码；跳过再测进 Delivery |
| **证据** | Report；Defect；两轮 Package 清单（须有可 diff 差异）；业务树 hash |

**回测要不要上下文？**  
要 —— 但是 **Verify 角色新 Package**（AC + 最新 Diff + 入口 + Defect 指针）。  
「直接验证」= 按该 Package 跑客户命令，不是零上下文。  
**Session 是否重开的审计字段** → W9；W7 不接受「同会话续聊冒充再测」。

合同：[verification-contract](./docs/50-verification/verification-contract.md) · [defect-package-contract](./docs/50-verification/defect-package-contract.md)

### S4e · Review

| | |
|--|--|
| **职责** | 交付前风险门；**不替代** Verification |
| **必验** | 有结论（通过/附条件/驳回）；无 Verify Report 不得进；不把 Review 当成重跑全量测试 |
| **揪偏** | 跳过 Review；Review 冒充验绿；与根目录 `review-package/` **架构审阅工具**叙事混为一谈（交付 Review 用 [templates/review](./templates/review/README.md)） |
| **证据** | Review Result（可先薄模板） |

### S4f · Delivery

| | |
|--|--|
| **职责** | 本地交付就绪；等人验收 |
| **必验** | 有 Review 结论；本地 Commit **或不 Push 的 awaiting**；确认无 Push |
| **揪偏** | Push；无 Verify/Review 交付；产物提交进本仓 |
| **证据** | commit sha 或 awaiting 声明；`git` 无 push |

### S5 · 人验收

| | |
|--|--|
| **职责** | 人确认可接受；触发或不触发 06 |
| **必验** | 有验收状态；验收前 06 无写 |
| **揪偏** | 交付瞬间抢跑回写 |
| **证据** | 验收记录 |

### S6 · Knowledge Lifecycle（06）

| | |
|--|--|
| **职责** | 只管理客户仓知识，不生产洞察 |
| **必验** | 有变更或 noop+原因；不做 Repo Scan |
| **揪偏** | AI 洞见当 Facts；客户知识回流本仓 |
| **证据** | index/文件 diff 或 noop |

---

## 4. 组合验证（仍按流程，不按概念）

| 组合 | 证明 | 失败长什么样 |
|------|------|--------------|
| 建槽→开 Story→装包 | 包读的是该 Story 槽位 | 无 Story 也能「装出」业务结论 |
| 装包→Adapter | 执行器只见 Package | Adapter 漫游知识全树 |
| 换阶段 | 必换包（W9 再验 Session 字段） | 同一聊天扛完全流程冒充编排 |
| Analysis→Plan | 有 Discovery 或 skip | 无摸底记录直接正式 Plan |
| Dev→Verify | 独立验；换 Verify Package | Dev 自评当绿 |
| Verify FAIL→Dev→Verify | Defect 进 P1；再测用新包 | 口头续改；空包再测 |
| Verify→Review→Delivery | 顺序不可跳 | 跳 Review / 未测交付 |

**金样主链（V3，B 套签收）：**  
S1（已有槽）→ S2 → Analysis/Discovery|skip/Gap → Plan+Allowed →（批）→ Dev → Verify → Review → Commit →（S5）  
禁止预置 patch 短路 Dev。

**金样回环（V4，与 V3 同等必要）：**  
Dev → Verify FAIL → Defect → Dev（含 Defect）→ Verify（新 Package）→ PASS → 才 Review。

A 套可预演；**对外宣称通路通必须 B 套 + V3 + V4**。Stop 收场的剧本不算签收绿。

---

## 5. 夹具 vs 真仓

| 时段 | 仓 | 说明 |
|------|----|------|
| W1–W6 | A（空仓/demo 夹具） | 负例可控 |
| W7 起 | **B 真仓或脱敏仓** | 客户真实测试入口；Commit 史 |
| 签收 | B | A 绿 ≠ B 通 |

真仓可在 W6 末再备；**不阻塞 W1 开工**。

---

## 6. 证据包（够复查即可，不堆目录学）

每次 V3/V4 留在客户仓（脱敏），本仓只留指针。  
形状说明：[templates/pathway-evidence](./templates/pathway-evidence/README.md)。字段可演进；下列为**最低集**。

```text
.story/<id>/pathway-evidence/
  meta.yaml           # A|B、Wave、Adapter、剧本、spine_mode、adapter_invoked、signoff_claim
  story/              # 需求与 acceptance
  packages/           # 各阶段清单+hash（尤其回环两轮；Verify P1 须嵌入 AC+Diff）
  gap-plan/           # Discovery|skip、Gap 状态、Allowed、批准
  verification/       # Report、命令、出口码、verdict_basis
  defects/            # 若有
  review/             # 结论
  delivery/           # sha 或 awaiting
  audit-host.md       # 无回流、无 Push；披露 spine
```

`spine_mode: fixture_control`（Runner 代写 Discovery/Plan/Approval、未调 Adapter）**不得**对外宣称 Adapter 挂机通路通。  
`spine_mode: hybrid_adapter_dev` + `adapter_invoked: true` = Development Adapter **脊骨已接线**。  
- `adapter_kind: functional_hook` → `signoff_claim: adapter_spine_wiring_functional`（预置 Dev，八问 Q3 不得勾非预置）  
- `adapter_kind: model_cli` → `signoff_claim: adapter_spine_wiring`（CLI 挂机候选，仍 ≠ 通路通）  
`spine_mode: adapter_driven` + 全角色 Adapter = 全站挂机签收候选。  
`verdict_basis: customer_entry_exit_code` = 客户测试入口是 AC 神谕；禁止把 exit code 说成「已逐条人工/模型核对」。

W9 后再加：`sessions/`、`compress/`（有了再验，不提前空目录）。

**签收必问（说不清则拒签）：**

1. 宿主是否干净（无回流 / 无 Push）？  
2. 是否 BLOCKED 仍进 Plan / 无 Allowed 仍 Dev / 无 Discovery|skip 仍 Plan？  
3. 是否预置 patch 冒充 Dev？  
4. 是否缺 P1 仍跑 / 乱喂全仓？  
5. 回测是否空包或 Dev 续聊？两轮 Package 清单能否 diff？  
6. Defect 是否结构化进了下一轮 Dev？  
7. 是否跳过 Review？仅编译是否当 PASS？  
8. 是否把未实现能力勾成已完成？是否把 Stop 收场算作通路通？

---

## 7. 防劣化（贴主链）

主链「好像能跑」后最常见劣化：Package 变长但 P1 变空；Defect 变日志墙；合同被 Prompt 稀释；跳站假绿。

合入下一 Wave 前最少做：

1. 复跑上一 Wave 必验负例（拒跑 / BLOCKED / 越权 等已有的）  
2. 同金样 Story 的 Package 体积：连续变大且 P1 覆盖变差 → 停  
3. 宿主扫描：本仓无客户正文  

为过验而改薄合同 → 熔断停工。

---

## 8. 签收口令

### 主链签收（完成 W8 + V3/V4 on B）

```text
[ ] S0 启动指向工作区；07 无业务；Resume 未宣称已完成
[ ] S1 槽位+入口诚实
[ ] S2 有 Story + acceptance（种子）
[ ] S3 缺 P1 拒跑；按阶段出包
[ ] S4a Spec+Discovery|skip；BLOCKED 禁 Plan；Analysis 不改码
[ ] S4b Allowed Files；未批不进 Dev
[ ] S4c Diff 不越权；不自评验绿
[ ] S4d B 套 AC 对照；Defect 回环；再测有新 Verify Package；Verify 不改码
[ ] S4e 有 Review；未跳过
[ ] S4f Commit 不 Push（或 awaiting）
[ ] V3 + V4 证据包齐；签收八问通过
[ ] 未把 Stop 收场或 A 套绿当成通路通
```

### 营业加固签收（W9–W10）

```text
[ ] 回 Verify = 新 Verify Package；角色切换 Session 决策可审计
[ ] 压缩不丢 AC/Allowed/Defect；不灌聊天
[ ] 人验收后才 06（或 noop）
[ ] 主环不强制重 onboard
```

---

## 附录 A · 后验清单（有价值，主链通后再做）

| 项 | 价值 | 建议时机 | 怎么验 |
|----|------|----------|--------|
| 阶段切换 Session 决策可审计 | 可恢复、可揪「长会话假编排」 | W9 | 每跳有 resume\|new |
| 闪断 Resume（同角色） | 稳定性 | W9+ | 杀进程后续跑，不丢 Allowed/Defect |
| 压缩保留清单自动化 | Context 质量 | W9 | 流水账不在下一 P1 |
| Discovery 合同加深 | 摸底质量 | W11 | 必须摸底场景无 skip 蒙混 |
| 适用 Rule 触顶不丢 | 硬门禁不被预算偷偷砍掉 | W11 | 极小 budget + 适用 Rule → FAIL/扩预算 |
| 同 Package 换 Adapter | 「与模型无关」 | W11 | 两 Adapter 输入契约同 |
| deprecated Knowledge 不进 P1 | 知识卫生 | W10+ | 标记后装包 |
| Facts≠Context 强制 schema | 防 Analyzer 变小 Planning | W5 加深 | 禁止字段注入 |
| Clarification / Approval 白名单 | 高风险不可 AI 拍板 | W5 加深 | schema 类 Decision 无人批则停 |
| COMMITTED Artifact 门禁 | 真可恢复 | 有 Trace/Artifact 产品后 | 未提交不得进下一站 |
| templates 关键产物对账 | 标准可执行 | 产物稳定后 | 对 Story/Defect/Package 先，勿一上来全量 |
| Learning→Rule 晋升人审 | 防假洞察变硬门禁 | 有 Learning 回写后 | 无审不得当 Rule |
| 薄→厚 Review | 残留风险质量 | W8 后 | 结论+风险字段 |

## 附录 B · 勿空验（纪律，不作为 Wave 完成项）

这些**值得遵守**，但单独「验通过」并不证明交付通路：

- 目录上证明「无第九域」  
- 「08 非垃圾桶」「Worker≠业务 Agent」的文档/PR 审计  
- Budget 仪表盘、并行扇出、多 Checkpoint 树  
- Skill 三层是否混装全集  
- Verification Report 是否含「回归范围建议」字段美化  
- 云桌面发布清单完整度（无桌面发布流水线前）  
- 向量 / Graph / Ranking / Dashboard / Policy Engine（默认不做）

违反附录 B → 纠偏；**不要**为了勾选它们推迟 S3/S4d。

---

## 附录 C · 与旧手册编号

旧 G0–G7、G2.5/G5.5、XP-* 族 **不再作为施工主键**。  
若文档外链仍写 Gx：G0≈S0，G1≈S1，G2≈S3，G3≈S4 骨架，G4≈Adapter，G5≈S4d，G5.5≈S4e，G6≈S4f，G7≈S6。  
冲突时以 **本文车站 + Wave** 为准。

---

## 附录 D · 改版纪要（非施工入口）

相对更早「Session/压缩/回环」口径，主链曾补齐：开 Story、每阶段重装包、人验收、明确 Stop、无人值守默认。  
施工主键从独立 Gate（G2.5 等）改回 **车站 Wave**。细节历史不挡正文阅读。

---

*维护：主链车站变更时改 §1–§2；后验项只进附录 A；勿把附录 B 抬升为必验。*
