# Runtime Learning Report — Sprint-7 Real Worker Foundation

> **新增交付物。** 记录 Runtime 驱动真实世界时的学习，而非架构扩写清单。

## 1. 我们验证了什么？

Runtime **无需新 Domain / 新架构层**，即可：

1. 受理 Task  
2. 经现有 Lifecycle 绑定 Context、打开 Trace  
3. 调用 Worker SPI  
4. Worker 内执行 **真实 OS 进程**  
5. 把 stdout/stderr/exitCode 收成 COMMITTED Artifact  
6. 写 Checkpoint、关闭 Trace、Task SUCCEEDED  

结论：**瓶颈不在 Kernel 缺对象，而在 Worker 是否真实、是否可控。**

## 2. 真实世界带来的事实

| 学习 | 含义 |
|------|------|
| stdout 是不可信文本 | Artifact 必须带 exitCode / command 标签，不能只信 message |
| 工作目录重要 | `workdir` 来自 Task workspace；Worker 必须显式 `ProcessBuilder.directory` |
| 超时是一等失败 | 悬挂进程会卡死无人值守路径 → timeout + destroy 必备 |
| 白名单 > 通用 shell | `/bin/sh -c` 会立刻引入注入与不可审计副作用 |
| 失败也要有 Trace/Artifact 策略 | 非白名单命令：ABANDONED Artifact、无 Checkpoint（当前政策）、Task FAILED |
| Engine 保持无知是对的 | Runtime 仍只看 `WorkResultStatus`；ProcessBuilder 泄漏会污染 Kernel |

## 3. 对 Calibration（6.1）的回响

| 6.1 判断 | Sprint-7 验证 |
|----------|----------------|
| 收窄的 Execution 增强可行 | ✅ ShellWorker 即「真执行」最小切片 |
| 勿上 Scheduler/Plugin | ✅ 未需要 |
| TD-3 观测偏薄 | 仍在：靠 lifecycle journal + Trace stdout；可接受 |
| Engine vs Scheduler 叙事债 | 未消解；真 Worker 未要求 Scheduler |

## 4. 什么 *没有* 学到（避免过度解读）

- 尚未证明多 Worker 并发 / 依赖图  
- 尚未证明 Resume / 崩溃恢复  
- 尚未证明危险命令治理（Capability）  
- `git status` ≠ 完整 Git 工作流引擎  

## 5. 对下一 Sprint 的建议（学习导向）

1. **优先：** 再增加 1～2 个白名单真实动作（例如只读 `git log -1`），继续压测 Artifact/Checkpoint 契约。  
2. **其次：** 小步改善 Result 面（暴露 checkpointId）——仍非新 Domain。  
3. **不要：** 因「真 Worker 已通」就引入 Scheduler / Workflow / Claude。  

## 6. 一句话

> **Runtime 已经能驱动真实世界；继续扩展架构不会增加这个证明。**
