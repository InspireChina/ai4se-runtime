# ADR-0005 · 引入 Repository Graph

- Status: **Accepted**
- Date: 2026-07-28
- Tags: repo-graph, context

## Context

仅靠全文检索不足以支撑可靠的计划与影响分析；每次全仓扫描成本高且不稳定。

## Decision

引入 **Repository Graph Engine**：

- 通用节点/边模型 + 白名单查询 API
- 语言/框架细节由 GraphEnricher（Plugin）贡献
- v1 默认内存图 + 文件哈希增量刷新
- Loop 绑定只读 Snapshot，避免执行中途图抖动

禁止向 Model 暴露任意图查询语言。

## Consequences

### Positive

- Planning/Rule 可基于结构事实
- 增量更新可控

### Negative

- 索引正确性成为新的质量面
- 多语言 Enricher 工作量

### Follow-ups

- 定义 v1 查询清单冻结
- Java Enricher 作为首个参考实现（实现阶段）
