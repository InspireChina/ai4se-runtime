# Development Contract

> 能力域 **03 · Workflow · Development**。仅在允许面内改代码；**不**负责证明需求已满足。  
> 通例见 [context-engineering-spec.md](../../../20-context-engineering/context-engineering-spec.md)。

## Input

| 优先级 | 内容 |
|--------|------|
| **P1** | Requirement；**Approved** Plan；Acceptance；适用 Rule；适用 Skill（Platform/Tech/Customer 按需）；**Allowed Files**；Dependency Context（允许面直接依赖的最小说明）；Repository Context（**仅相关模块**）；**Defect Package**（若存在，整份） |

P1 切片**必须进入** Development Context Package（`slices/acceptance.md`、`slices/allowed-files.md` 等），缺则拒建包——不是靠 Prompt 提醒模型去翻仓库。
| **P2** | 少量 Learning（与本缺陷/域相关）；短 Architecture 摘录 |
| **禁止** | **整个 Repository**；无关模块；所有旧 Story；Learning 全集；无关 Knowledge 全文；测试日志洪水（非当前缺陷） |

## Output 必须

- **Workspace Diff**（限 Allowed Files，或经门禁扩展申请）  
- 变更说明（相对 Acceptance / Defect）  

## Output 禁止

- 「测试已通过 / 可以交付」等 **自评验绿**  
- 修改 Forbidden 面  

## Stop / Resume / FAIL

- **Stop：** 变更完成，交 Verification  
- **Resume：** 同 Development 轮次工具闪断  
- **FAIL：** 越权改文件；P1 缺 Approved Plan；无视 Defect Package  
