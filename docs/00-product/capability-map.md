# Capability Map · 产品宪法

> **一年内一级能力域不增不改名。**  
> 域是抽屉标签（东西放哪、别越权），不是八个要做满的子系统。  
> 北极星：[vision.md](./vision.md) · 哲学：[philosophy.md](./philosophy.md) · 宿主：[asset-hosting.md](./asset-hosting.md)

---

## 日常只盯这条主链

```text
客户仓建槽(01脚本)
  → 开 Story
  → 每步拼 Context Package(02)
  → 按阶段跑(03 Workflow)
  → 调模型/命令(04)
  → 调客户测试；失败出 Defect(05)
  → 回环或往下(03 Control)
  → Commit，等人验
  → 改客户仓 md/yaml(06)

底座：Runtime 别动(07) · 能启动就行(08)
```

施工与验证顺序（车站 / Wave）见仓库根 **[PATHWAY-VERIFICATION-HANDBOOK.md](../../PATHWAY-VERIFICATION-HANDBOOK.md)**。  
Analysis 段含 Discovery|skip → Gap → Clarification?；Delivery 之后是 **人验收**，再触发 06。

开会 / 写代码 / 加功能先问：**挡主链哪一截？**  
不问：哪个概念还不够优雅？

---

## 八个能力域

### Band A · 交付产品域


| #      | 域                                                                  | 负责（仅此）                                                      | 禁止                         | 成功标准                                      |
| ------ | ------------------------------------------------------------------ | ----------------------------------------------------------- | -------------------------- | ----------------------------------------- |
| **01** | [Repository Intelligence](../10-repository-intelligence/README.md) | 仓数字化：Scan / Facts / Map / 依赖摘要 / 架构要点 / 风险面 / Build·Test 入口 | AI 分析、需求理解、改码建议            | 客户仓有可引用基线 + 测试/构建入口清单；**无思考结论**           |
| **02** | [Context Engineering](../20-context-engineering/README.md)         | **有限 Token 下装入最大有效信息** → Context Package                    | 流程跳转、业务知识中枢、自己「想需求」        | 阶段 P1 在包内、禁止项不在；缺 P1 拒跑                   |
| **03** | [Delivery Orchestration](../30-delivery-orchestration/README.md)   | **Workflow** 阶段顺序；**Control** 继续/停/批/回环/Resume              | 拼包细节、验绿、写业务知识              | Story 走到 Commit 或明确 Stop；FAIL 有去向         |
| **04** | [AI Execution](../40-ai-execution/README.md)                       | 调用 Claude/Cursor/Shell/Git/MCP…                             | 业务知识、Retry/熔断等控制流          | Package 可提交执行；失败原样交回 03                   |
| **05** | [Verification](../50-verification/README.md)                       | 调客户已有验证；Report；FAIL→Defect Package                          | 自建测试云；把验证只塞进「pipeline 顺手测」 | Acceptance Pass/Fail 可复查；Fail 带结构化 Defect |
| **06** | [Knowledge Lifecycle](../60-knowledge-lifecycle/README.md)         | **只管理**客户仓知识：增删改汰、索引、晋升                                     | 不「生产」洞察；不做 Repo Scan       | 结束后该更新的文件/index 有变更或明确无需更新                |


### Band B · 底座


| #      | 域                                                        | 负责                                                          | 禁止        | 成功标准              |
| ------ | -------------------------------------------------------- | ----------------------------------------------------------- | --------- | ----------------- |
| **07** | [Runtime Foundation](../70-runtime-foundation/README.md) | Task / Artifact / Checkpoint / Trace / Worker / Decision 骨头 | 任何交付方法论   | 能调度与恢复；**无新业务语义** |
| **08** | [Infrastructure](../80-infrastructure/README.md)         | Config / 日志 / 启动 / 打包上桌面                                    | 垃圾桶、新业务能力 | 云桌面一条命令能起编排并指向客户仓 |


### 横切（非能力域）


| 名                                       | 说明                                             |
| --------------------------------------- | ---------------------------------------------- |
| [templates/](../../templates/README.md) | 标准物真源（Story / Rule / Skill / Defect / Review…） |
| Stage Contracts                         | 挂在 03 Workflow 下，不是一级域                         |


### 明确不做（一级）

- 独立 Decision / Policy / Graph / Ranking / Learning Engine  
- 一年内新增第 9 个一级能力域

Decision = **03 Control** 里的 if/else。Runtime 的 Decision 对象只是门闸骨头。

---

## 03 概念拆分（不拆模块）

```text
03 Delivery Orchestration
├── Workflow   Analysis → Discovery|skip → Plan → Dev → Verify(05) → Review → Deliver → 人验收 → Lifecycle触发(06)
└── Control    Approval / Loop / Stop / Resume决定 / Retry（Budget·Parallel 后置）
```

---

## 02 内部分层（文件夹约定，非四个引擎）

```text
Sources → Retrieval → Assembly → Optimization
```

现网等价：读哪些文件 → 裁一下 → 写成 Package → 交给 04。

---

## 归属速查


| 问题                 | 归属          |
| ------------------ | ----------- |
| 下一阶段是谁             | 03 Control  |
| 这步 Package 有什么     | 02          |
| Token 爆了先丢什么       | 02；触顶停跑     |
| Resume 同 Session？  | 03 决定；如何重建包 |
| Worker 怎么调 Adapter | 07 → 04     |
| 测什么 / Defect 长什么样  | 05          |
| 知识文件怎么改            | 06          |
| 仓扫描 / 入口清单         | 01          |


---

## 现网优先级（反理想化）

施工与验证以仓库根 **[PATHWAY-VERIFICATION-HANDBOOK.md](../../PATHWAY-VERIFICATION-HANDBOOK.md)** 的 **车站 S0–S6 + Wave** 为准（贴合下方主链，不为验证点目录服务）。

1. **P0** 08 启动 + 07 Frozen（S0）
2. **P0** 01 建槽 + 开 Story（S1–S2）
3. **P0** 02 按阶段装包（S3，先 Analysis，缺 P1 拒跑）
4. **P0** 03 状态机骨架 + Adapter（S4 + 04）
5. **P0** Gap/Discovery|skip/Allowed/Dev 挂进状态机 → 05 Verify+Defect 回环 → Review → 本地 Commit（S4a–S4f）
6. **P1** Session/压缩审计；人验收；06 最小回写
7. **后验** 换 Adapter、Rule 触顶、Discovery 加深、材料加深等（手册附录 A）

后置且勿空验：完整依赖图、向量召回、并行、Dashboard、Policy Engine。

---

## 旧「十二能力域」映射


| 旧                                          | 新                                                                              |
| ------------------------------------------ | ------------------------------------------------------------------------------ |
| 01 Onboarding                              | 01 + [onboarding-runbook](../10-repository-intelligence/onboarding-runbook.md) |
| 02 Knowledge / Rule / Skill / Repo Context | 01 Facts · 02 materials · 06 Lifecycle                                         |
| 03 Context Engineering                     | 02                                                                             |
| 04–06, 09–10 Analysis…Delivery             | 03 Workflow stages                                                             |
| 07–08 Verification / Defect                | 05                                                                             |
| 11 Learning                                | 06                                                                             |
| 12 Orchestration                           | 03 Control                                                                     |
| 90 Engine                                  | 07                                                                             |
| 99 Status                                  | [90-status](../90-status/README.md)                                            |


旧蓝图归档：[legacy blueprint](../archive/legacy-twelve-domains/ai-delivery-orchestrator-blueprint.md)