# Claude CLI Adapter

附录 A · 同 Package 换 Adapter。实现：`ai4se-execution` → `ClaudeCliAdapter`。

默认二进制：`claude`（可用环境变量 `AI4SE_CLAUDE_BIN` 覆盖）。

```text
claude -p --output-format text "<prompt from Context Package>"
# Development 角色另加 --dangerously-skip-permissions
```

与 Cursor 共用 `ContextPackagePrompt`：换 Adapter 不改变 Package 输入语义。

验收：

```bash
mvn -pl ai4se-execution -am test \
  -Dtest=ClaudeCliAdapterTest,SamePackageSwapAdapterTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```
