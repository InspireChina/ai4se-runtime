# Knowledge Lifecycle Contract

> 能力域 **06**。宿主：**客户仓**。  
> **不产生知识，只管理知识。**

## 负责

| 动作 | 说明 |
|------|------|
| 新增 / 更新 | Story 验收后写入 Knowledge / Learning / Rule / Skill |
| 废弃 | index 标 deprecated；02 不再装入 P1 |
| 索引 | 维护 id → path → kind → tags → refs |
| 晋升 | Learning/Pattern → Rule 或 Skill（可半自动，须可审） |

## 不负责

- Repository Scan / Facts（01）  
- 当次请求 Context Package 装配（02）  
- 发明业务结论替代交付过程证据  

## 与 Learning

Learning 是一类**事件与材料**（见 [learning-contract.md](./learning-contract.md)），落地仍走本 Lifecycle。

## 成功

结束后：文件/index 有更新，或 `.story` 中记录 `lifecycle: noop` 及原因。
