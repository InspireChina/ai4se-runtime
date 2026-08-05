# AI Delivery Orchestrator

**定义 AI 如何交付软件** —— 不是开发工具，不是 Runtime，不是 Claude Wrapper。  
**八个能力域** = 产品抽屉；主链无人值守跑到本地 Commit，等人验收。

仓库：https://github.com/InspireChina/ai4se-runtime

## Vision

> 真正缺失的是稳定的软件交付标准。  
> 核心价值：**Context Engineering** —— 有限 Token 下最大有效信息。  
> 成功 = 新客户 + 新 Story → 主链 → Commit → 人验收；稳定、可验证、与模型无关。

- 宪法：[docs/00-product/capability-map.md](./docs/00-product/capability-map.md)  
- **施工与验证（最外层）：** [PATHWAY-VERIFICATION-HANDBOOK.md](./PATHWAY-VERIFICATION-HANDBOOK.md)  
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
| [PATHWAY-VERIFICATION-HANDBOOK.md](./PATHWAY-VERIFICATION-HANDBOOK.md) | **施工顺序 · 验证门禁 · 揪偏 · 完整通路** |
| [docs/](./docs/README.md) | 文档索引 |
| [templates/](./templates/README.md) | 标准物 |
| [90-status Now](./docs/90-status/build-pathway-playbook.md) | 水位快照 |

## 快速开始

```bash
export JAVA_HOME="$HOME/Library/Java/JavaVirtualMachines/corretto-1.8.0_502/Contents/Home"
mvn clean test
mvn -pl ai4se-demo -am package
java -jar ai4se-demo/target/ai4se-runtime.jar \
  --workspace ai4se-demo/sample-workspace \
  --input ai4se-demo/sample-input
```

要求：**Java 8** · Maven 3.9+

客户仓建槽（01）：

```bash
./scripts/onboard-repo.sh /path/to/customer-repo
```

## 实现模块（过渡期，≠ 产品定义）

当前 Maven 模块仍是历史实现；归属见 [ARCHITECTURE.md](./ARCHITECTURE.md)。  
**产品叙事以八域为准，不以 worker-api / kernel 为准。**

## Now

按 [通路验证手册](./PATHWAY-VERIFICATION-HANDBOOK.md) 的 **车站 Wave** 施工。  

**已通（控制面）：** W1–W10 门禁 + B 脱敏仓 `hybrid_adapter_dev`（Dev Package→Adapter 脊骨 + 真 `mvn test`；Dev 实现可为 Functional 预置，≠ 现场 Cursor 开发）+ Claude Adapter（隔离）+ Rule 触顶 + 闪断 Resume。  

**未通 / 禁止宣称通路通：** Analysis/Plan 仍 fixture；S5 签收为 FIXTURE；现场真客户仓；全站 `adapter_driven`。  

下一刀：Analysis/Plan 挂 Adapter，或现场真仓复跑（勿把 hybrid 绿写成通路通）。

验收（自动，无需人手复跑 CLI）：

```bash
mvn -pl ai4se-context,ai4se-execution,ai4se-orchestration -am test
```
