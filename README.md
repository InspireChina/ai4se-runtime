# AI Software Engineering Runtime

面向无人值守软件工程任务的可插拔执行平台（**AI4SE Runtime**）。

仓库：https://github.com/InspireChina/ai4se-runtime

## 文档（只认这几份）

| 文档 | 用途 |
|------|------|
| [建造通路手册](./docs/build-pathway-playbook.md) | **主导航**：台阶、门禁、冷启动验收、90 天通路 |
| [当前支持状态](./docs/current-support-status.md) | **S0**：现在有/没有什么（防口头假完成） |
| [现状全景](./docs/project-status-plain-language.md) | 现在能做什么 / 不能做什么 |
| [串行流水线设计](./docs/serial-pipeline-design.md) | 摸底→澄清→计划→编码→测试→知识 的目标形状 |
| [Frozen 架构](./docs/architecture/) | L0 宪法（Invariants / Enforcement / Kernel） |
| [First Production Delivery Report](./docs/first-production-delivery-report.md) | 首次真实交付证明（跑 Demo/Test 后生成） |
| [Runtime Boundary Validation](./docs/runtime-boundary-validation-report.md) | Sprint-8.5：Runtime / Worker / Demo 边界（只分析） |
| [Runtime Boundary Stress](./docs/runtime-boundary-stress-report.md) | Sprint-9：三类真实交付压测边界（跑后生成） |
| [Production Input Review](./docs/sprint-8-production-input-review.md) | Sprint-8：input/ → jar 驱动 Runtime（Kernel 冻结） |
| [Engineering Delivery Contract](./docs/engineering-delivery-contract.md) | 交付阶段/产物/SUCCESS 合同（只规范，非实现） |
| [Requirement Analysis Contract](./docs/requirement-analysis-contract.md) | 需求理解→Gap→条件澄清→计划（Delivery 前半段） |
| [Repository Analyzer Design](./docs/repository-analyzer-design.md) | 仓库现状分析抽象（v0=Map+Search，不做 Graph） |
| [Repository Context Contract](./docs/repository-context-contract.md) | Facts vs Context；Planning/Clarify 消费谁 |
| [Pilot Requirement Analysis](./docs/pilot-requirement-analysis.md) | NL → Delivery Bundle（无 Patch/Runtime 改动） |
| [Engineering Validation (Promotion)](./docs/engineering-validation-promotion.md) | 电商促销需求分析 Bundle（诚实 UNKNOWN） |

历史讨论稿在 [`docs/_archive/`](./docs/_archive/)，**不排期、不当真源**。

## 快速开始

```bash
export JAVA_HOME="$HOME/Library/Java/JavaVirtualMachines/corretto-1.8.0_502/Contents/Home"
mvn clean test
mvn -pl ai4se-demo -am package
java -jar ai4se-demo/target/ai4se-runtime.jar \
  --workspace ai4se-demo/sample-workspace \
  --input ai4se-demo/sample-input
```

生产输入合同见 [`ai4se-demo/sample-input/CONTRACT.md`](./ai4se-demo/sample-input/CONTRACT.md)。  
脚本：`./scripts/run-production.sh`

要求：**Java 8** · Maven 3.9+

其他入口：

```bash
mvn -pl ai4se-demo exec:java -Ddemo.mainClass=com.ai4se.runtime.demo.ShellWorkerMain
mvn -pl ai4se-demo exec:java -Ddemo.mainClass=com.ai4se.runtime.demo.delivery.FirstProductionDeliveryMain
mvn -pl ai4se-demo exec:java -Ddemo.mainClass=com.ai4se.runtime.demo.delivery.stress.BoundaryStressMain
```

## 模块说明

| 模块 | 职责 |
|------|------|
| `ai4se-common` | ID、ErrorTaxonomy、`ExecutionContextView` |
| `ai4se-worker-api` | Worker SPI |
| `ai4se-kernel` | Task · Artifact · ExecutionContext · Trace · Checkpoint |
| `ai4se-runtime-engine` | `Runtime.submit`（仅依赖 Worker SPI） |
| `ai4se-workers` | `ShellWorker` · `NoopWorker` · `MockWorker` · `CommandWorker` |
| `ai4se-demo` | `ShellWorkerMain` · `WalkingSkeletonMain` · `DemoMain` |
| `ai4se-review-tools` | ReviewPackage 生成器（工程基础设施，不依赖 Kernel） |

依赖：`demo → engine + workers`；`engine → kernel → common`；`workers → worker-api`（不依赖 engine）；`review-tools` 零 Runtime 依赖。

## ReviewPackage（工程）

```bash
./scripts/generate-review-package.sh
# 输出：review-package/generated/rp-<utc-timestamp>/
```

规范见 [`review-package/SCHEMA.md`](./review-package/SCHEMA.md)。

## 进度水位

已完成（最小）：S0 ADR · S1–S2 · S3 部分 · S4–S8a。S8b/c 锁定；S9 样例级。  
真源：[建造通路手册](./docs/build-pathway-playbook.md) 水位表 · [当前支持状态](./docs/current-support-status.md)。
