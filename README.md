# AI Software Engineering Runtime

面向无人值守软件工程任务的可插拔执行平台（**AI4SE Runtime**）。

仓库：https://github.com/InspireChina/ai4se-runtime

## 快速开始

```bash
export JAVA_HOME="$HOME/Library/Java/JavaVirtualMachines/corretto-1.8.0_502/Contents/Home"
mvn clean test
mvn -pl ai4se-demo -am exec:java
```

要求：**Java 8** · Maven 3.9+

## 模块说明

| 模块 | 职责 |
|------|------|
| `ai4se-common` | ID、ErrorTaxonomy、`ExecutionContextView` |
| `ai4se-worker-api` | Worker SPI |
| `ai4se-kernel` | Task · Artifact · ExecutionContext · Trace · Checkpoint |
| `ai4se-runtime-engine` | `Runtime.submit`（仅依赖 Worker SPI） |
| `ai4se-workers` | `MockWorker` · `CommandWorker` |
| `ai4se-demo` | `DemoMain`（CommandWorker + echo hello） |

依赖：`demo → engine + workers`；`engine → kernel → common`；`workers → worker-api`（不依赖 engine）。

## Roadmap（最近 Sprint）

| Sprint | 内容 | 状态 |
|--------|------|------|
| **Sprint-3** | Vertical Slice + Trace 四节点 | 完成 |
| **Sprint-4** | Runtime Core Stabilize + ArchUnit | 完成 |
| **Sprint-5** | Checkpoint Foundation（MemoryCheckpointStore） | 完成 |

权威文档：[`docs/architecture/`](./docs/architecture/)（**Frozen**）
