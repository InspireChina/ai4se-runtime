# 04 · AI Execution

> **Execution 不拥有业务知识，不拥有流程控制。**

## 成功标准

同一 Context Package 可交给 Adapter 执行；失败/输出原样返回给 03；换 Adapter 不改 02/03 合同。

## 负责

Claude CLI · Cursor CLI · Gemini / OpenAI · Shell · Git · MCP · 其他 Tool Adapter

## 不负责

- Retry / 熔断 / Resume（→ 03 Control）  
- 拼 Package（→ 02）  
- **Worker**（→ 07 Runtime；Worker 只是调用 Adapter 的端口）  
- 业务 Rule/Knowledge 正文  

## 文档

| 文档 | 说明 |
|------|------|
| [adapter-contract.md](./adapter-contract.md) | Adapter 边界 |

代码落点：`adapters/` 或未来 `ai4se-execution`（见 [ARCHITECTURE.md](../../ARCHITECTURE.md)）。
