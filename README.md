# AI Delivery Orchestrator

**定义 AI 如何交付软件** —— 不是开发工具，不是 Runtime，不是 Claude Wrapper。
**八个能力域** = 产品抽屉；主链无人值守跑到本地 Commit，等人验收。

仓库：https://github.com/InspireChina/ai4se-runtime

## Vision

> 真正缺失的是稳定的软件交付标准。
> 核心价值：**Context Engineering** —— 有限 Token 下最大有效信息。
> 成功 = 新客户 + 新 Story → 主链 → Commit → 人验收；稳定、可验证、与模型无关。

- 宪法：[docs/00-product/capability-map.md](./docs/00-product/capability-map.md)
- **客户真仓运行：** [真实客户需求卡验证手册](./docs/90-status/m1-real-customer-story-runbook-v1.md)
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
| [真实客户需求卡验证手册](./docs/90-status/m1-real-customer-story-runbook-v1.md) | **冻结输入 · 受控运行 · 证据冻结 · 人工接收** |
| [docs/](./docs/README.md) | 文档索引 |
| [templates/](./templates/README.md) | 标准物 |

## 快速开始

```bash
export JAVA_HOME="$HOME/Library/Java/JavaVirtualMachines/corretto-1.8.0_502/Contents/Home"
mvn clean test
mvn -pl ai4se-demo -am package -DskipTests
java -jar ai4se-demo/target/ai4se-runtime.jar --help
java -jar ai4se-demo/target/ai4se-runtime.jar run \
  --workspace /path/to/customer-repo \
  --story story-123 \
  --requirement /tmp/story-123.md \
  --write-scope src/main/java \
  --write-scope src/test/java \
  --max-dev-rounds 3
```

正式入口是 Story 主链（`run`）：受控 Adapter 完成 Analysis、Planning、Development、Verification、Review；只有 Review PASS 且验收探针全部证明后才会本地 Commit 并进入 `AWAITING_HUMAN_ACCEPTANCE`。运行不会 push。

要求：**Java 8** · Maven 3.9+ · 客户仓已 onboard（`.ai4se/`）且工作树干净。

客户仓建槽（01）：

```bash
./scripts/onboard-repo.sh /path/to/customer-repo
```

## 实现模块（过渡期，≠ 产品定义）

当前 Maven 模块仍是历史实现；归属见 [ARCHITECTURE.md](./ARCHITECTURE.md)。
**产品叙事以八域为准，不以 worker-api / kernel 为准。**

## 运行边界

- 仅允许已注册的 `cursor-cli`、`codex-cli`、`claude-cli` Adapter。
- requirement、acceptance probes 与 write scope 必须由 operator 在启动前冻结；模型不能修改它们。
- 运行在本地 Delivery commit 后停止，必须由人工接收；不会自动 push、合并或伪造人工确认。
- 完整操作步骤、证据目录和停止规则见[真实客户需求卡验证手册](./docs/90-status/m1-real-customer-story-runbook-v1.md)。

工程回归：

```bash
mvn -pl ai4se-context,ai4se-execution,ai4se-orchestration -am test
```
