# Sprint-7 Design Note — Real Worker Foundation

> 工程 Design Note（非 Frozen Architecture）。**不改 Kernel**；不新增 Domain / Scheduler / Workflow / Capability / Plugin。

## 1. Goal

验证 Runtime 已能驱动 **真实本地软件工程动作**（进程执行），而不是继续扩展架构。

```text
Task → Runtime.submit → ShellWorker.execute
  → 真实本地命令（ProcessBuilder）
  → 采集 stdout / stderr / exitCode
  → Artifact → Checkpoint → Trace → Task Completed
```

## 2. Why ShellWorker（不是新层）

| 选项 | 选择 |
|------|------|
| ShellWorker | ✅ 推荐：通用真实副作用边界，白名单可控 |
| GitWorker | 可被 ShellWorker 的 `git status` 覆盖；暂不单列 Domain Worker |
| 改 Kernel | ❌ 禁止 |

`CommandWorker` 保留为兼容委托；**Sprint-7 规范名是 `ShellWorker`**。

## 3. Mapping（现有对象 only）

| 步骤 | 既存能力 |
|------|----------|
| submit | `Runtime` Orchestrator |
| Worker.execute | Worker SPI + `ShellWorker` |
| 真实命令 | `ProcessBuilder`（Worker 内；Engine 不可见） |
| Artifact | `ArtifactLifecycleService` + metrics→`shell-stdout.txt` |
| Checkpoint | `MemoryCheckpointStore`（成功路径） |
| Trace | 四节点 + close |
| Task Completed | `SUCCEEDED` + Context `FROZEN` |

## 4. Safety

- **Allowlist only：** `echo …` / `pwd` / `git status`  
- 无 `/bin/sh -c`、无管道与重定向元字符  
- Timeout + destroyForcibly  
- 非白名单 → `FATAL_FAIL` → Artifact ABANDONED（不 Commit）

## 5. Explicit non-goals

Scheduler · Workflow · Capability · Plugin · Resume · Claude CLI · 扩大任意 shell。

## 6. Demo / Tests

| 入口 | 说明 |
|------|------|
| `ShellWorkerMain` | 真实 echo/pwd/git |
| `ShellWorkerIntegrationTest` | 全链路 + 拒绝危险命令 |
| `ShellWorkerTest` | argv 白名单 + 真实 stdout |

## 7. Kernel unchanged

无新 Domain Object；无 Kernel API 变更。引擎仍只分支 `WorkResultStatus`。
