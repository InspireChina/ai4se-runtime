# Portable Host Harness Contract v1 · 可移植宿主 Harness 契约

> **状态：本轮实现契约。** AI4SE 是客户仓旁的交付控制面，不替换客户已批准的
> Claude、Cursor、Codex、OMP 或其它模型工具。本契约把“当前宿主模型参与交互前半段”
> 与“受控 Adapter 执行已批准的无人值守后半段”明确分开。

## 1. 用户可感知的使用方式

用户只打开客户代码仓和客户允许的模型工具。一次安装后，在该工作区调用：

```text
AI4SE install → AI4SE Bridge prepare → 当前宿主模型读 Package 并写候选产物
→ AI4SE Bridge submit/validate → 人回答或批准 → 受控 Delivery Runtime
```

AI4SE Runtime 不进入客户业务源码，也不把客户源码或知识回写到产品仓。它在客户工作区
创建 `.ai4se/host/`（安装信息和宿主说明）以及既有 `.ai4se/`、`.story/` 受控产物。

## 2. 稳定内核与可替换宿主

| 层 | 职责 | 是否随客户/工具改变 |
|---|---|---|
| Runtime control plane | 状态、冻结、写入范围、证据、验证、停止/恢复、local commit | 否 |
| Host Bridge | 将阶段 Package 与候选校验暴露为本地命令/stdio 可调用能力 | 否 |
| Host Profile | 告诉 Claude/Cursor/Codex/OMP 如何调用 Bridge、读写何处 | 是 |
| Customer `.ai4se/` | 项目事实、知识、规则、附件索引、Story 状态 | 是，但不得跨客户复用 |
| 模型 | 阅读阶段 Package，生成候选知识/规格/代码/评审 | 可替换 |

Skill 只是 Host Profile 的一部分，不能拥有流程状态、批准权或交付判断。

## 3. 本轮可运行的最小纵向切片

本轮不伪称已为全部 IDE 发布原生插件。先提供所有可运行终端命令型宿主都能使用的
`terminal-host` Profile，并以同一协议为 Claude/Cursor/Codex/OMP 的后续薄适配保留入口。

### 3.1 安装

```text
ai4se install --workspace <customer-repo> --host terminal-host --runtime-jar <jar>
```

安装不得修改业务源码、构建配置、客户模型配置或网络策略。它只写：

```text
.ai4se/host/installation.properties
.ai4se/host/AI4SE-HOST.md
```

`AI4SE-HOST.md` 是可复制到客户工具 Skill/Rule/Command 的宿主中立指令；其每一步均调用
Bridge，并明确模型只能写候选路径。将来原生 Claude/Cursor/Codex/OMP Profile 只翻译这份
语义，不复制 Runtime 流程。

### 3.2 当前宿主模型参与的阶段

```text
bridge prepare-discovery
  → 创建只读 P1 Package 与空 candidate 目录
  → 当前宿主模型只写 candidate.yaml + documents/
bridge submit-discovery
  → 校验 source evidence、HEAD、写入范围；候选才可人工 approve

intake（冻结文字与附件）
bridge prepare-specification
  → 当前宿主模型只写 specification result + candidate 或 questions
bridge submit-specification
  → 校验二选一结果和写入范围；人再 answer/freeze
```

这让白天 OMP/IDE 会话模型承担摸底、读取原型摘要后的理解和规格整理，而不额外启动一个
CLI 模型进程。

### 3.3 已批准后的无人值守边界

开发、验证、缺陷修复、Review、local commit 仍由既有 `run/resume` 受控 Adapter 主链执行。
Adapter 必须是客户机器已安装、已批准的 CLI/API 能力；没有该能力时，Bridge 必须停在
`READY_FOR_UNATTENDED_DELIVERY`，不能声称后台可运行。

## 4. 附件、原型和 Browser Relay

Bridge 不直接绕过客户网络、登录或浏览器策略。Host Profile 只接受已经由客户工具取得的
本地附件，或带来源与快照哈希的摘要文件。不能读取时必须记录 `unavailable/denied` 并生成
澄清，不得把链接存在当作模型已理解页面。

附件最少字段：`source`、`purpose`、`captured_by`、`access_status`、`sha256`。现有
`StoryIntake` 继续负责复制文件和冻结 SHA；Browser Relay 适配将在有对应宿主权限后作为
单独 Profile 能力接入。

## 5. 多卡与模型选择

模型可以提交批次或模型选择建议，但 Runtime 才能执行调度决定。默认串行；并行需要独立
worktree、无写入范围/API/迁移/共享测试数据冲突、合并顺序和集成验证，当前不实现自动并行。
一个 Story 运行后固定其 Adapter；不因额度或失败静默切换模型。

## 6. 非目标

- 不把 Runtime 重写成模型自由编排的图框架；
- 不在没有客户批准的情况下安装浏览器、代理、JRE 或模型 CLI；
- 不自动批准知识、业务澄清、Plan 或最终验收；
- 不把 `terminal-host` 文档伪称为 Claude/Cursor/Codex 原生插件；
- 不自动 push、合并或部署。

## 7. 本轮验收

1. 安装器只能生成受控 Host 文件，且幂等拒绝冲突安装；
2. Bridge 能建立并暴露 Discovery/Specification P1 Package；
3. 宿主写出的越权文件、无来源知识、无效规格均被拒绝；
4. 合格候选能进入既有人工批准/冻结流程；
5. 在客户仓式 Git 工作区以 `terminal-host` 完成 install → discovery → approval →
   intake → specification clarification → freeze 的真实文件闭环；
6. 全量 Maven 回归通过。

后续只有在一个真实客户宿主（例如 OMP Browser Relay 或 Claude/Cursor MCP）证明同一协议可用后，
才增加该宿主的原生 Profile。
