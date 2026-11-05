# 宿主内部操作契约

这里的命令由模型宿主执行，用户只提供仓路径、需求、业务回答和一次启动确认。

## 定位工具

从客户仓 `.ai4se/host/installation.properties` 读取 `runtime_jar`。它位于 Bundle 的 `lib/ai4se-runtime.jar`；Bundle 的 `bin/ai4se` 是入口。尚未安装时，在 AI4SE 源仓执行 `scripts/build-host-bundle.sh <全新输出目录>`，或使用发布 Bundle。不要替换已启动任务的 Bundle。

运行需要 Java 8+ 和客户批准且已登录的模型 CLI；从源码构建另需 Maven。Adapter 使用客户已有 CLI 登录/网关配置，不要求独立 AI4SE 模型 API key，不修改现场供应商配置。

工程师终端亦可直接用现场脚本（与下列命令等价，见仓库 `QUICKSTART.md`）：

```bash
./scripts/bootstrap-customer.sh <客户仓> <codex|cursor|claude>
./scripts/new-story.sh <客户仓> <story-id> <request.md> <adapter>
./scripts/story-ctl.sh status|ready|answer|start|report …
```

首次接入（Skill 后台）：

```bash
<bundle>/bin/ai4se-flow bootstrap --workspace <客户仓绝对路径> --adapter <codex|cursor|claude> --candidate <唯一知识候选ID>
```

保留工具返回的进程/session id；若任务仍运行，观察该任务，不能启动第二次摸底。

## 新需求

将用户原话存成请求文件再提交。截图/文档逐个用 attachment；原型链接经客户允许的浏览器/连接器获取后保存本地材料和来源。无法获取的关键内容须在冻结前处理，不假称看过原型。

```bash
<bundle>/bin/ai4se deliver prepare --workspace <客户仓> --story <唯一ID> --request-file <原始需求.md> --adapter <执行器> --max-dev-rounds 3 --timeout-minutes 15 [--attachment <文件>] [--model <模型ID>]
<bundle>/bin/ai4se deliver status --workspace <客户仓> --story <ID>
```

控制器依据源码目录形成写入上限；Planning 决定准确 Allowed Files，在启动前展示给用户一次确认。不让用户手工猜哪些文件要修改。
非常规项目目录未被识别时，宿主依据确定性仓库清单定位真实源码目录或文件，先确认路径确属当前客户仓，再对 `deliver prepare` 重复传 `--write-scope <相对路径>`；这个参数由宿主处理，不要求用户提供。控制器在任何模型调用前拒绝仓外路径、`.git`、`.ai4se` 和 `.story`。

| 状态 | 宿主动作 |
|---|---|
| LAUNCHING / RUNNING | 后台继续执行；按需查看 controller/worker.log，不重复调用 prepare/resume |
| NEEDS_SPECIFICATION | 展示 specification/clarification.questions.md，记录真实回答 |
| NEEDS_ANALYSIS | 展示 analysis 的问题/Gap，记录真实业务回答 |
| NEEDS_ASSUMPTIONS | 展示 Analysis 的全部假设，仅在用户明确接受后记录真实 actor 和回答 |
| READY | 展示 controller/readiness.md，等待一次开始无人值守决定 |
| DELIVERED_AWAITING_ACCEPTANCE | 展示验收报告，等待工程师本地验收 |
| STOPPED / INTERRUPTED | 展示报告与失败事实，不自动清理代码、改探针、重置预算或重跑 |

回答与启动：

```bash
<bundle>/bin/ai4se deliver answer --workspace <客户仓> --story <ID> --answer <用户原回答> --actor <用户身份>
<bundle>/bin/ai4se deliver start --workspace <客户仓> --story <ID> --approve <readiness.properties中的readiness_sha256> --actor <批准人>
```

确认 Analysis 假设时，在展示假设并收到用户明确回答后，`answer` 另加 `--ack-assumptions yes`。仅准备阶段宿主进程意外中断且控制器状态是 `INTERRUPTED` 时，用 `deliver continue --workspace <客户仓> --story <ID>` 继续；执行期没有盲目续跑入口。

任务有独立 JVM、关闭的 stdin、持久日志和客户仓互斥锁，不依赖聊天持续在线。机器关机或云桌面强制销毁仍会中断；status 显示 INTERRUPTED，不能假称交付。

## 次日验收

```bash
<bundle>/bin/ai4se deliver report --workspace <客户仓> --story <ID>
<bundle>/bin/ai4se accept --workspace <客户仓> --story <ID> --actor <用户身份> --note <用户明确验收意见>
```

报告在 `.story/<ID>/controller/acceptance-report.md`，源码在同一客户 checkout，本地 commit 可以直接检查。PROVEN 只覆盖实际冻结探针中的断言。发现 Bug 时收集复现步骤、期望/实际结果，创建关联修复卡，仍携带原约束。

旧 run/resume 与 ai4se-flow full 保留为低层兼容接口，日常 Skill 使用新控制器。不要自动恢复历史终态卡，或混淆不同 worktree 上的同号卡。

## Code Graph（内部）

摸底时自动全量扫描；Story 阶段 `ensureFresh` 增量刷新。客户日常不要手工拼 `ai4se code-graph`。
