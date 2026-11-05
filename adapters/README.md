# adapters/

**04 · AI Execution** 落点（渐进）。

原则：不拥有业务知识，不拥有流程控制（Retry/熔断留给 03 Control）。

| 子目录 / 模块 | 状态 |
|---------------|------|
| **`ai4se-execution`**（Maven） | **W4：** `CursorCliAdapter`；**附录 A：** `ClaudeCliAdapter` |
| `adapters/cursor-cli/` | 文档指针；实现在 `ai4se-execution` |
| `adapters/claude-cli/` | 文档指针；同 Package 换 Adapter |
| `shell/` / `git/` / `mcp/` | 渐进 |

## Cursor CLI

默认解析顺序：环境变量 `AI4SE_CURSOR_BIN` → macOS  
`/Applications/Cursor.app/Contents/Resources/app/bin/cursor` → 命令名 `agent`。

```text
# Cursor.app
cursor agent -p --output-format text "<prompt from Context Package>"
# Development / Analysis… 权限由 UnattendedPermissionPolicy + CliVendor 统一映射
# 扩展新 CLI：加 CliVendor 常量 + Policy 映射；Adapter 只调 apply(...)

# 或独立 agent 二进制
agent -p --output-format text "<prompt from Context Package>"
```

**注意：** Cursor **IDE 对话** ≠ 本 Adapter。无人值守 Dev 必须能拉起 CLI 进程。

## Claude CLI

默认二进制：`claude`（可用环境变量 `AI4SE_CLAUDE_BIN` 覆盖）。

```text
claude -p --output-format text "<prompt from Context Package>"
# Development：--dangerously-skip-permissions
# Analysis / Planning / Review：--permission-mode acceptEdits（写 .story 制品不卡权限）
```

两 Adapter 共用 `ContextPackagePrompt`；附录 A 必验：同 Package → 同 prompt 语义。

验收：

```bash
mvn -pl ai4se-execution,ai4se-orchestration -am test
```

真实机有 CLI 时，把 `AI4SE_CURSOR_BIN` / `AI4SE_CLAUDE_BIN` 指到该二进制即可；单测用注入的假进程，不要求本机已装 CLI。
