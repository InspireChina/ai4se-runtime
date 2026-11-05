# Planning Contract

> 能力域 **03 · Workflow · Planning**。产出可批准的开发/测试/风险/迁移/回滚等方案（可多 Agent）。  
> 通例见 [context-engineering-spec.md](../../../20-context-engineering/context-engineering-spec.md)。  
> 输出：**Plan Bundle**

## Input

| 优先级 | 内容 |
|--------|------|
| **P1** | Requirement；Clarification 关闭结果；Analysis 产出（含模块/Knowledge IDs）；Repository Context（相关）；适用 Rule；Platform Skill（如何写 Plan） |
| **P2** | Tech Skill；Customer Skill；Learning patterns；Architecture |
| **禁止** | 全仓代码；无关模块；历史 Story；测试原始日志洪水 |

## Output 必须

- Plan Design（含 **Allowed / Declared Files**）  
- Plan Test / Test Strategy（含验收引用、命令指针）  
- Acceptance 列表（可测）  

### Allowed Files 行格式（制品契约 · 问题类加固）

每条 Allowed / Declared 路径是**带语法的制品**，不是散文：

- **必须：** 一条相对路径 token（`/` 分隔；可含目录尾 `/`）  
- **禁止：** markdown 反引号、引号、尾注（`(new)`）、`#` 注释、行内空格夹叙、`..`、绝对路径  

不合规制品在 Plan 写入/读取时**校验拒绝**（进不了 Development）。扩展语法须先改正文契约，再改校验器——禁止按实例 peel。

## Output 禁止

- 未批准即当执行令  
- 把 Patches 字节冒充 Plan  
- 不合规 Allowed 行（见上）  

## Stop / Resume / FAIL

- **Stop：** 等人批准（Approval）  
- **Resume：** 同 Planning、补充材料后续跑  
- **FAIL：** 无 Declared Files；与 Requirement 脱节；P1 缺失  
