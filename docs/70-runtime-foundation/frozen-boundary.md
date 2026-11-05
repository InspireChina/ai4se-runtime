# Frozen Boundary · Runtime Foundation

> **07 · Runtime Foundation** 是薄执行底座，**Frozen**，不是产品增长面。变更须 ADR。

## 冻结对象（可演进须 ADR）

| 对象 | 职责 |
|------|------|
| Task | 可调度、可恢复、可审计的工作单元 |
| Artifact | 跨阶段唯一产物货币 |
| Worker | 统一执行接口 |
| Trace | 执行轨迹 / 审计 |
| Checkpoint | 恢复点 |
| Decision | 门禁 / 人闸决策 |

活动架构真源：[`architecture/`](architecture/README.md) · ADR：[`adr/`](adr/README.md)

## 产品层 vs Engine 层

| 层 | 可变性 | 例子 |
|----|--------|------|
| **01–06 产品域 + 合同** | 产品演进主战场 | 八域 Contract、SOP、Stage 合同 |
| **70-runtime-foundation（本目录）** | Frozen；改须 ADR | Kernel 对象、Invariants、Enforcement |

## 锁定（无证据不开）

- 本仓 Graph Engine / 业务 Knowledge 中枢  
- Scheduler 产品化（超出 ADR-0016）  
- 把 Engine 叙事抬成第九能力域  

## 与 ADR-0007

**Engine ≠ Coding Agent。** 见 [`adr/0007-runtime-not-agent.md`](./adr/0007-runtime-not-agent.md)。  
产品调度真源：[`../30-delivery-orchestration/`](../30-delivery-orchestration/README.md)。
