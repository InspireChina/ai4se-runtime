# AI Delivery Orchestrator

**定义 AI 如何交付软件** —— 可移植到客户仓的模型交付控制器。
**八个能力域** = 产品抽屉；主链无人值守跑到本地 Commit，等人验收。

仓库：https://github.com/InspireChina/ai4se-runtime

## 最简上手（现场终端）

前置：Java 8+、已登录的 `cursor` / `codex` / `claude` CLI 之一。从本仓：

```bash
./scripts/bootstrap-customer.sh /绝对路径/客户仓 cursor
./scripts/new-story.sh /绝对路径/客户仓 story-001 /tmp/req.md cursor
./scripts/story-ctl.sh status /绝对路径/客户仓 story-001
./scripts/story-ctl.sh start  /绝对路径/客户仓 story-001 --actor 你的名字
./scripts/story-ctl.sh report /绝对路径/客户仓 story-001
```

完整说明与 Skill 入口：[QUICKSTART.md](./QUICKSTART.md)。更细的现场契约：[客户现场使用指南](./docs/90-status/portable-customer-delivery-guide.md)。

## Vision

> 真正缺失的是稳定的软件交付标准。
> 核心价值：**Context Engineering** —— 有限 Token 下最大有效信息。
> 成功 = 新客户 + 新 Story → 主链 → Commit → 人验收；稳定、可验证、与模型无关。

- 宪法：[docs/00-product/capability-map.md](./docs/00-product/capability-map.md)
- **客户现场入口：** [QUICKSTART.md](./QUICKSTART.md) · [客户现场使用指南](./docs/90-status/portable-customer-delivery-guide.md)
- 工程结构：[ARCHITECTURE.md](./ARCHITECTURE.md)
- 宿主：[docs/00-product/asset-hosting.md](./docs/00-product/asset-hosting.md)

## 八域（一览）

| # | 域 | 一句话 |
|---|----|--------|
| 01 | Repository Intelligence | 仓数字化，不思考 |
| 02 | Context Engineering | 有限 Token 最大有效信息 |
| 03 | Delivery Orchestration | Workflow + Control |
| 04 | AI Execution | 只 Adapter，不控流 |
| 05 | Verification | 客户验证面 + Defect |
| 06 | Knowledge Lifecycle | 只管理，不生产 |
| 07 | Runtime Foundation | Frozen |
| 08 | Infrastructure | 启动/配置，非垃圾桶 |

## 文档

| 入口 | 说明 |
|------|------|
| [QUICKSTART.md](./QUICKSTART.md) | **终端三步：摸底 · 开卡 · 验收** |
| [客户现场使用指南](./docs/90-status/portable-customer-delivery-guide.md) | Skill 入口 · 产物位置 · 故障边界 |
| [真实客户需求卡验证手册](./docs/90-status/m1-real-customer-story-runbook-v1.md) | 冻结输入 · 受控运行 · 证据冻结 · 人工接收 |
| [客户仓建库与首卡 Runbook](./docs/90-status/customer-repository-discovery-runbook.md) | 历史手工摸底步骤（优先用 bootstrap-customer） |
| [客户模型工具接入 Runbook](./docs/90-status/customer-host-bridge-runbook-v1.md) | 在 Claude / Cursor / Codex / OMP 中挂 Host Bridge |
| [docs/](./docs/README.md) | 文档索引 |
| [templates/](./templates/README.md) | 标准物 |

## 开发与低层调试

日常现场用上方脚本或 Skill；下列命令供工程回归与排障。

```bash
mvn -pl ai4se-context,ai4se-execution,ai4se-orchestration -am test
mvn -pl ai4se-demo -am package -DskipTests
./scripts/build-host-bundle.sh /tmp/ai4se-host-bundle
java -jar ai4se-demo/target/ai4se-runtime.jar --help
```

`java -jar … run …` 是单次生产主链的低层接口；日常新卡走 `deliver`（由 `new-story.sh` / Skill 调用）。要求：**Java 8** · Maven 3.9+ · 客户仓工作树在启动无人值守前干净。运行只做本地 Commit，不会 push。

## 实现模块（过渡期，≠ 产品定义）

当前 Maven 模块仍是历史实现；归属见 [ARCHITECTURE.md](./ARCHITECTURE.md)。
**产品叙事以八域为准，不以 worker-api / kernel 为准。**

## 运行边界

- 仅允许已注册的 `cursor-cli`、`codex-cli`、`claude-cli` Adapter。
- 启动前展示并冻结 requirement、acceptance probes、Plan Allowed Files 与源码写入上限；由工程师确认就绪摘要，执行期模型不能改这些输入。
- 运行在本地 Delivery commit 后停止，必须由人工接收；不会自动 push、合并或伪造人工确认。
- 当前使用步骤、证据目录和停止规则见[客户现场使用指南](./docs/90-status/portable-customer-delivery-guide.md)。

工程回归：

```bash
mvn -pl ai4se-context,ai4se-execution,ai4se-orchestration -am test
```
