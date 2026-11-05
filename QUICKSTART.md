# AI4SE 最简上手（终端）

客户现场不背 Jar 参数。先装好 **Java 8+**、**Maven 3.9+**（仅从源码构建 Bundle 时需要）、**Git**，以及已登录的 **cursor / codex / claude** CLI 之一。

## 1. 拉仓并摸底

```bash
git clone https://github.com/InspireChina/ai4se-runtime.git
cd ai4se-runtime

./scripts/bootstrap-customer.sh /绝对路径/客户仓 cursor
# 或 codex / claude；可选 --model <id>；强制重建 Bundle 加 --rebuild
```

首次会自动构建 `dist/host-bundle`。完成后客户仓出现 `.ai4se/`（事实、`source-inventory`、轻量 Code Graph、working 知识），**不改业务源码、不 push**。Code Graph 在摸底时全量扫描，后续 Story 按源码摘要增量刷新；客户不必手工拼 `code-graph` 命令。

## 2. 开一张需求卡

把需求写成 markdown，例如 `/tmp/req.md`，然后：

```bash
./scripts/new-story.sh /绝对路径/客户仓 story-001 /tmp/req.md cursor

./scripts/story-ctl.sh status /绝对路径/客户仓 story-001
```

- 若停在澄清：`./scripts/story-ctl.sh answer … --answer 'Q1 选 B' --actor 你的名字`
- 就绪后看摘要：`./scripts/story-ctl.sh ready …`
- 开始无人值守（会要求输入 `START`）：

```bash
./scripts/story-ctl.sh start /绝对路径/客户仓 story-001 --actor 你的名字
```

可关掉终端；机器与模型 CLI 保持可用。进度与证据在客户仓 `.story/story-001/`。

## 3. 次日验收

```bash
./scripts/story-ctl.sh report /绝对路径/客户仓 story-001
```

对照 `.story/story-001/controller/acceptance-report.md`、本地 git 与逐 AC 证据。本系统**不会 push**。

## Skill 入口（可选）

若已在 Cursor / Codex / Claude / OMP 注册 Bundle 内 `skills/ai4se-customer-delivery`，日常可改为说：

```text
$ai4se-customer-delivery 接入客户项目
$ai4se-customer-delivery 新需求：……
```

Skill 与上述脚本走同一控制器。注册位置因宿主而异，见 Bundle 内 `docs/` 与 `skills/…/references/controller.md`。

## 已有 Bundle 时

```bash
export AI4SE_BUNDLE=/path/to/host-bundle
"$AI4SE_BUNDLE/scripts/bootstrap-customer.sh" /绝对路径/客户仓 cursor
```

或直接：`./scripts/build-host-bundle.sh /tmp/ai4se-host-bundle` 后把该目录拷到客户机。

## 不要做什么

- 不要对同一张卡在 RUNNING 时重复 `new-story` / `prepare` / `start`
- 不要把「构建成功」当成功能验收；以报告中的 `PROVEN` 探针为准
- 不要期望网页对话 alone 无人值守——后台执行依赖已注册 CLI Adapter
