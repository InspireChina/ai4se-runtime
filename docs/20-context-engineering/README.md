# 02 · Context Engineering

> **负责让 AI 在有限 Token 下获得最大有效信息。**  
> 这是整个产品真正的核心。不要扩这句话。

## 成功标准

给定阶段：P1 必在 Context Package 内；禁止项不在；缺 P1 → Builder FAIL，不得开 CLI。

## 负责

Sources 选用 · Retrieval/裁剪 · Assembly（Context Package）· Optimization（压缩/为 Resume 重建包）

## 不负责

流程跳转（03）· 业务知识中枢 · Repo Scan（01）· 知识晋升策略正文（06）· Adapter 内部控制流（04）

## 内部分层（约定，非四引擎）

```text
Sources → Retrieval → Assembly → Optimization
```

现网：读合同列出的路径 → 裁剪 → 写 Package → 交 04。

## 文档

| 文档 | 说明 |
|------|------|
| [context-engineering-spec.md](./context-engineering-spec.md) | 阶段通例：P1/P2/禁止/Output/Stop/FAIL |
| [context-builder-contract.md](./context-builder-contract.md) | 非 AI 装配器 |
| [materials/](./materials/README.md) | Rule / Skill / Knowledge 检索材料（**非一级域**） |

## 现网最小

Markdown + YAML + Builder。不做 Ranking / 向量 Recall / Graph。
