# 客户模型工具接入 Runbook v1

> 面向实施者与宿主模型，不是日常工程师操作手册。日常入口见[客户仓日常使用指南](../00-product/customer-host-bridge-day-one-guide.md)。

## 目标与边界

AI4SE 在客户仓内维护控制面和可复核制品；客户批准的模型 CLI 是执行器。它不上传客户内容、不代替业务决策、不 push，也不把未知历史测试或外部服务当作首次摸底的默认阻塞。

```text
用户业务语言
  → Host Skill / Command
  → AI4SE（状态、Package、约束、Probe、审计）
  → 客户已批准的 Adapter
  → 客户仓业务代码 + 本地交付提交
```

## 宿主模型执行约定

宿主仅接受下列四种用户意图：接入客户项目、新需求、继续、验收。它在后台使用 Bundle 的 `ai4se-flow` 与 `ai4se` 命令；不得要求用户记住命令、文件路径、Actor 名称、测试框架参数或 Git checkpoint。

选择 Adapter 的优先级：当前客户工具已批准的 CLI → 用户已指定的受控 Adapter → 仅在无法判断时问一次。不得无提示换模型；切换时仅继承落地制品，不假设共享聊天记忆。

## 首次接入协议

1. 验证客户仓可读取且没有未授权业务改动。
2. 运行 `ai4se-flow bootstrap --workspace <repo> --adapter <approved>`。
3. `onboard` 写确定性事实和验证能力地图；不得执行全仓历史测试。
4. `discover` 只读取受限 Package 与必要源码，产出带 `Evidence`、`Working Boundary`、`Unknowns`、`source_paths` 的候选。
5. `promote-knowledge` 将满足来源契约的候选提升为 `working`，不创建客户业务 commit。

接入成功不是“全仓测试已通过”。它的正确声明是：事实已扫描、working 知识已建立、未知项可见、目标验证能力已盘点。

### 自动归档为能力状态

`npm test` 缺失、POM 跳测、没有统一测试命令、历史云集成测试缺凭据、或某模块本地不能启动，都写入 `verification-capabilities.yaml`，不要求用户选择处理方案。只有当前 Story 碰到相同外部边界，才成为该卡风险。

## Story 协议

1. `intake` 冻结用户原话、附件路径和 hash。
2. Specification 检索命中的 `working/verified` 知识，并对其 source paths 做最小当前代码复读。
3. 有充分依据时自动冻结规格；缺少业务语义时才输出编号问题、依据、影响及选项。
4. Answer 作为下一次模型调用的结构化消息进入 Specification/Analysis；不手改状态文件。
5. Planning 生成一份受门禁校验的 `plan.md`：其中包含 Design、Allowed Files、Change Map、Test Strategy、Impact Assessment；有 API/Data 影响时另生成 `api-contract.md` / `data-change.md`。每 AC 生成并冻结一个 Probe。
6. 展示一次交付就绪摘要并取得用户的单一“开始无人值守”决定。该决定覆盖已列出的业务答案、风险授权、Allowed Files、影响场景与 Probe；后续无人值守运行 Development → Verification → 有界修复 → Review → local commit。

破坏性数据操作、公共 API 不兼容、支付/退款/权限安全、外部付费/凭据等高风险必须在第 6 步完成授权。执行期发现未冻结的新业务事实、范围突破、非 PASS Review 或验证失败预算耗尽时，一律安全停止；它们不是夜间再向人提问的项目。

## 验证与交付判据

- 仓库级测试命令只有在客户明确配置为适用时才执行；
- 没有仓库级测试命令时，冻结 AC Probe 可以作为 Story 的验证入口；
- `PROVEN` 需要对应 Probe 的命令、退出码 0、文件 hash 一致；
- 全部 AC `PROVEN`、Review `PASS`、write scope 合规，才能 local delivery commit；
- 构建成功、模型口头完成或未执行的历史测试，均不能代替验收证据。

## 知识维护

知识索引状态：`working`（证据化初稿）、`verified`（严格人工批准）、`stale`（受交付 diff 影响）、`retired`（被新版本替代）。交付后只记录失效候选与学习材料，绝不把模型猜测自动回写为新事实。下一张卡命中 stale 知识时必须复读受影响源文件；验收后再生成可追溯的更新候选。

## 串行与并行

当前可靠能力是同一工作树的串行 Story：前卡的冻结证据和已 settled 状态不会阻塞后卡，未 settled 卡和任何业务脏改动会阻塞。多卡并行只在文件、数据迁移、API 和部署资源都明确不重叠时才可引入独立工作树调度；当前不宣称已自动具备。

## 严格审计模式

客户若要求签字治理，可使用 `approve-knowledge`、`checkpoint-knowledge`、人工规格冻结与 Plan 批准。它比正常模式多出人工记录，但不应成为普通客户仓接入的默认门槛。
