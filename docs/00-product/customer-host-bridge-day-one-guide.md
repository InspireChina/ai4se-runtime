# AI4SE 客户仓日常使用指南

AI4SE 不是另一个聊天 Agent，也不是要求工程师背命令的流程产品。它是放在客户仓旁边的**交付控制面**：把确定性扫描、受限模型阅读、冻结需求、验证探针、阶段状态和交付证据保存成可复用的工程材料；客户已经批准的 Codex、Cursor、Claude 或 OMP 是模型执行器。

## 你实际怎么用

### 第一次到客户现场

在 AI4SE 项目（或已注册该 Skill 的客户工具）里说：

```text
$ai4se-customer-delivery 接入客户项目
```

系统只询问客户仓绝对路径；仅当无法从当前工具确定执行器时，再询问一次 `codex`、`cursor` 或 `claude`。之后自动完成：

```text
客户仓
  ├─ .ai4se/repository/        确定性事实与验证能力地图
  ├─ .ai4se/knowledge/         来源可追溯的 working 知识
  ├─ .ai4se/index/             知识索引、状态、来源文件与 SHA
  ├─ .ai4se/host/              本地 Host 安装记录
  └─ .story/                   每张需求卡的冻结输入、过程和交付证据
```

这一步不改业务源码、不推送代码、不跑陌生仓库的全量历史测试。完成后只给你一页摘要与文件路径，马上可以发第一张卡。

### 每张需求卡

```text
$ai4se-customer-delivery 新需求：后台订单支持批量确认收货；原型见附件
```

如果材料和代码足以确定语义，流程自动进入开发。只有真正不能从现有材料判断的业务选择才会打断，例如：批次上限、部分失败策略、是否允许跨店铺。问题必须编号，并附为什么影响实现、代码/原型依据和可选项。你回复：

```text
$ai4se-customer-delivery 继续：Q1 选 B，批次上限 200，允许部分成功并返回失败明细
```

之后自动执行：规格冻结 → Plan/约束/变更地图 → 开发 → 每 AC 验证 → 有界修 Bug → Review → 本地业务 commit。最终你只需要看交付摘要，再说：

```text
$ai4se-customer-delivery 验收：通过
```

AI4SE 从不 push；合并、UAT、部署和回滚仍走客户已有流程。

## 首次摸底究竟有哪些内容

| 产物 | 产生者 | 能说明什么 | 不能说明什么 |
|---|---|---|---|
| `repository/facts.md`、`module-map.md` | 确定性扫描 | 构建描述符、模块、源码根、Git 快照、文件数量 | 业务语义、职责、接口规则 |
| `verification-capabilities.yaml` | 确定性扫描 | 现有测试资产、是否存在脚本、是否声明跳测 | “项目测试已通过”或功能正确 |
| `knowledge/*.md` | 当前模型受限阅读 | 带来源路径的模块边界、调用/数据线索、显式 Unknowns | 无来源的业务事实 |
| `index/knowledge.yaml` | 控制面 | 文档状态：`working/verified/stale/retired`、来源 commit/SHA | 替代当前代码阅读 |

`working` 是首次摸底产生的证据化工作知识，能用于后续检索，但不是人工确认的业务真理。每张卡仍会对命中的来源文件做最小必要的当前代码复读。代码改动后，相关知识先标为 `stale` 候选而非自动篡改正文；验收后可生成更新候选，避免卡 2 把卡 1 的旧结论当新事实。

## 为什么不会再因为历史测试或云服务卡住

首次接入生成的是**验证能力地图**，不是“全仓测试门禁”。以下是普通仓库事实，系统自动记录并继续：

- 前端没有 `npm test`；
- POM 默认跳过测试；
- 七牛、腾讯云、消息队列等历史集成测试在本地没有凭据；
- 某些模块需特定环境才可启动。

只有当当前 Story 确实触及该外部能力时，才把它纳入该卡的风险与验证。交付判定由每条 AC 对应的**冻结 Probe**给出：命令、文件哈希、退出码和 `PROVEN/FAILED` 都写在 `.story/<id>/verification/`。构建绿不能替代 Probe；没有全仓测试入口也不阻止一张有充分 Story Probe 的卡交付。

## 正常模式与严格审计模式

正常客户交付模式自动提升 source-cited Discovery 为 `working` 知识、自动冻结已清晰规格、自动批准低风险 Plan。它只为业务歧义与实质风险打断。

如果客户要求审计签字，可改用严格模式：人工批准知识、冻结规格与 Plan，并建立本地知识 checkpoint。这是附加治理，不是正常使用的前置条件。

## 客户工具怎么接入

将 Bundle 中的 `skills/ai4se-customer-delivery/SKILL.md` 按客户工具允许的方式注册为项目 Skill/Command/Rule。不同工具的安装位置不同，AI4SE 不猜测也不强行修改客户配置。工具不支持项目 Skill 时，模型仍可在后台执行 Bundle 的 `ai4se-flow bootstrap/full`；工程师不需要手敲这些内部命令。

详细的终端契约和受控停止规则见 [客户模型工具接入 Runbook](../90-status/customer-host-bridge-runbook-v1.md)。
