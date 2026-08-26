# Host Bridge 客户仓验证记录 · v1

## 目的与结论

验证 AI4SE 作为客户已有模型工具的**控制面与可移植能力包**，而不是替代客户工具启动
另一个聊天模型。结论：`terminal-host` Profile 已在 Litemall 的独立客户仓克隆中走通
“模型化知识候选 → 人工知识批准 → 受控知识 checkpoint → 原始需求采集 → 带代码证据的
澄清 interrupt → 答复注入 → 候选规格 → 人工冻结”的前半链路。

这不是一次业务代码交付重跑。Development → Verify → Review → local commit 仍由现有受控
Production Pathway 承担，并已有 Litemall R-004 的真仓交付验证；本次验证的新增目标是让客户
模型工具能在不复制完整聊天上下文的前提下，可靠地进入那个主链。

## 受控环境

- 客户仓基线：Litemall 克隆，初始提交 `67cf365f0042b1e193ad36d8a257261a0f94df95`；
- 验证目录：`/private/tmp/ai4se-host-bridge-litemall-r2`（临时、未推送）；
- Runtime：本仓 `scripts/build-host-bundle.sh` 构建的 `terminal-host` Bundle；
- Story：`promotion-stack-policy-r005`；
- 人类知识批准与澄清答复使用 `ai4se-experiment-simulated-*` 身份，**只为流程验证，不能作为生产授权**；
- 未修改原客户种子仓，未 push，未启动业务开发 Adapter。

## 结果

| 阶段 | 结果 | 关键证据 |
|---|---|---|
| 安装 Host Profile | 通过 | `.ai4se/host/installation.properties` 与 `AI4SE-HOST.md`，仅这两项可被专门识别 |
| Discovery Package | 通过 | `litemall-host-r001` 限定候选写入路径 |
| 候选知识校验 | 通过 | `checkout-promotion-boundary` 引用 `WxCartController`、`WxCouponController`、`CouponVerifyService` |
| 人工批准与 checkpoint | 通过 | 本地提交 `e6853f0c55aade6b3cde439158020a0132db5dda`；checkpoint 只允许候选、知识正文和索引 |
| 含糊需求的澄清 | 通过 | 三条 Q1–Q3，分别要求叠加顺序、响应兼容、资格校验权威，并均有仓内源码证据 |
| 澄清答复注入 | 通过 | 生成 `clarification.resolved.md` 后重新 prepare Specification |
| 规格冻结 | 通过 | 候选包含 `raw/goal/in_scope/out_of_scope/decisions/acceptance`，共 AC1–AC6；`requirement.md` 与冻结清单已生成 |

## 暴露并修复的真实问题

首次演练在知识批准后先创建了 Story input，再试图 checkpoint；控制面拒绝，因为 checkpoint
不能把未冻结的 Story 输入混进知识基线。这是正确保护，但 Runbook 原先没有把 checkpoint 写成
显式操作，容易让用户走错顺序。

已新增：

```bash
java -jar "$AI4SE_JAR" checkpoint-knowledge \
  --workspace "$PWD" --candidate <approved-candidate>
```

它要求 `approved.md`，只本地提交候选目录、已验证知识和索引；任何业务源码、其它候选或未知
文件都会返回 `FAILED_POLICY`，绝不自动提交或 push。

## 仍然成立的边界

- 当前是通用 `terminal-host`，不是 Cursor/Claude/Codex/OMP 的原生插件；这些工具只要能执行
  客户批准的本地命令即可接入。原生 Profile 是后续便利性工作，不影响控制面契约。
- Bridge 不读取 URL、Cookie 或 Browser Relay；客户工具先在授权范围内取得本地快照，`intake`
  冻结该文件后才可供模型使用。
- 人工批准、产品澄清、Plan 批准、最终验收仍不可自动化；Plan 后的开发、测试、缺陷修复、Review
  和本地提交才是无人值守范围。
- 当前 serial queue 只做受控选择，不执行并发写入。并行执行需要按资源/文件冲突分析另行验证。
