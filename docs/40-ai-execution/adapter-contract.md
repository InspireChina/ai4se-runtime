# Adapter Contract

> 能力域 **04 · AI Execution**。  
> **不拥有业务知识，不拥有流程控制。**

## 输入

- Context Package（由 02 Builder 产出）  
- 执行参数（工作目录、超时等，由 03/08 注入）

## 输出

- 原始/规范化执行结果（stdout/stderr/exit/产物路径指针）  
- **不得**自行决定 Retry、换阶段、熔断

## 禁止

- Adapter 内写「失败就再试三次并跳过 Verification」等控制流  
- Adapter 读取客户仓 Knowledge 全树自行检索（检索在 02）  
- 将 Worker 实现成「Claude 业务 Agent」

## 二进制与就绪（问题类：执行未就绪）

- Shell：一律经共享 `ShellExecutable.resolve()`（跳过 Windows WSL stub）  
- **脚本型 CLI：** Windows 下 shebang / `.sh` 不得直丢 `CreateProcess`（error=193）→ `ShellExecutable.launchArgv` 经 bash 启动（Cursor/Claude Adapter 共用）  
- Claude：`AI4SE_CLAUDE_BIN` → 常见安装路径探测 → 仍无则明确失败（禁止 silently 裸 `claude` 当策略）  
- Cursor：`AI4SE_CURSOR_BIN` → 同样受 preflight；脚本路径须可用 bash  
- **开跑 preflight**（Orchestration Control）：shell / entries /（若使用 Claude 或 Cursor Adapter）CLI 未就绪则**不进 Analysis**  

## 无人值守写权限（问题类 · 公共能力，非单 Adapter 补丁）

**能力入口：** `UnattendedWriteScope`（角色写面）+ `UnattendedPermissionPolicy.apply(CliVendor, role, argv)`（厂商映射目录）。

| Scope | 角色写面 | Claude | Cursor | Codex |
|-------|----------|--------|--------|-------|
| `BUSINESS_SOURCE` | Development | `--dangerously-skip-permissions` | `--force --approve-mcps` | `--approve-for-me` |
| `STORY_ARTIFACT` | Analysis / Planning / Review | `--permission-mode acceptEdits` | `--trust --auto-review --approve-mcps` | `--approve-for-me` |
| `NONE` | 未知 / 无 Contract 写 | （不加） | （不加） | （不加） |

Codex 的 `--approve-for-me` 是受控的 `workspace-write` 自动审批模式，适用于上述两个受控写面。
Codex Adapter 只传递 `--approve-for-me`；不得同时传递 `--sandbox workspace-write`，因为 Codex CLI 会拒绝该组合。

**扩展 OpenCode / Codex / …（同一问题类，禁止再抄 if 树）：**

1. `CliVendor` 增加常量  
2. `UnattendedPermissionPolicy` 为该 vendor 补齐 `STORY_ARTIFACT` + `BUSINESS_SOURCE` 映射（缺映射失败封闭）  
3. 新 `*CliAdapter.buildArgv` **只**调用 `UnattendedPermissionPolicy.apply(VENDOR, role, argv)`  
4. 单测 `productionCliAdaptersMustCallSharedPolicy` / `everyRegisteredVendorMapsBothWriteScopes` 守门  

同类洞（Trust / Shell 批 / MCP 批）归入 vendor 映射，不在某个角色分支里零散加 flag。

## 按角色选模型（问题类：全程同一模型绑死）

同一 Adapter 类型（如 `claude-cli`）可在不同角色使用不同 `--model`：

| 配置面 | 示例 |
|--------|------|
| 客户仓文件 | `.ai4se/runtime/role-models.yaml`（`default` + `roles.analysis/development/review/acceptance`） |
| 环境变量 | `AI4SE_MODEL`；`AI4SE_MODEL_ANALYSIS` / `_PLANNING` / `_DEVELOPMENT` / `_REVIEW` / `_ACCEPTANCE` |
| Field CLI | `--model`；`--model-analysis`；`--model-development`；`--model-review`；`--model-acceptance` |
| API | `PathwayRunner.Config.roleModels(...)` / `.roleModel(role, id)` |

**优先级（高→低）：** Config/CLI → env → 客户仓 yaml → Adapter 构造默认 → 省略 `--model`（厂商 CLI 默认）。

Control 注入 `AI4SE_MODEL`；Claude/Cursor Adapter 仅透传 `--model`，**不**自行决定角色或换阶段。  
`acceptance` 与 `review` 可互为回退（专配优先）。

示例：需求分析用 Sonnet 落文档，编码用 DeepSeek，验收用专用模型：

```text
--analysis-adapter claude --model-analysis claude-sonnet-4-5 \
--adapter claude --model-development deepseek-v4-pro \
--review-adapter claude --model-acceptance <acceptance-model>
```

## Worker 关系

Runtime（07）Worker = 统一调用端口；本域 Adapter = 被调用的手。
