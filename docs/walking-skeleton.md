# Sprint-3 / Task-001 — Walking Skeleton

> 第一条可运行链路：`Runtime.submit` → Task → ExecutionContext → MockWorker → Mock Artifact → SUCCEEDED → `RuntimeResult`  
> **不接** Claude CLI / 模型 / 真实 Worker  
> 约束权威：[runtime-invariants.md](./architecture/runtime-invariants.md) · [runtime-enforcement.md](./architecture/runtime-enforcement.md)（Frozen）

## 1. Runtime 时序（文字）

1. Caller 调用 `Runtime.submit(RuntimeRequest)`
2. **Task Lifecycle Service** 创建 Task（`CREATED`，身份字段只写一次）
3. Engine 经合法转移推进：`VALIDATING → QUEUED → SCHEDULED → STARTING`
4. **Context Lifecycle Service** materialize `ExecutionContext`；Task **bind 一次**（G3 / T4）
5. Task → `RUNNING`
6. Engine 构造 `WorkRequest`，调用 **MockWorker.execute**（固定 OK，无副作用产物登记）
7. **Artifact Lifecycle Service** `propose`（`PROPOSED`）→ `commit`（`COMMITTED`）
8. Context **仅索引 COMMITTED** Artifact（G5 / E4）
9. Engine 同一意图内：Task → `SUCCEEDED`，再 `freeze` Context（G4）
10. 返回 `RuntimeResult`（taskId / contextId / SUCCEEDED / artifactIds）

## 2. Mermaid Sequence Diagram

```mermaid
sequenceDiagram
  autonumber
  participant Caller
  participant Engine as Runtime (Unified Engine)
  participant TLS as TaskLifecycleService
  participant CLS as ContextLifecycleService
  participant Worker as MockWorker
  participant ALS as ArtifactLifecycleService
  participant Task as Task (Kernel)
  participant Ctx as ExecutionContext (Kernel)
  participant Art as Artifact (Kernel)

  Caller->>Engine: submit(RuntimeRequest)
  Engine->>TLS: create(request)
  TLS->>Task: build (CREATED)
  TLS-->>Engine: Task

  Engine->>TLS: transition(VALIDATING)
  TLS->>Task: transitionTo(VALIDATING)
  Engine->>TLS: transition(QUEUED)
  Engine->>TLS: transition(SCHEDULED)
  Engine->>TLS: transition(STARTING)

  Engine->>CLS: materialize(contextId, taskId, profileId)
  CLS->>Ctx: new ExecutionContext
  CLS-->>Engine: Context
  Engine->>TLS: bindExecutionContext(contextId)
  TLS->>Task: bindExecutionContext (once)
  Engine->>TLS: transition(RUNNING)

  Engine->>Worker: execute(WorkRequest, ContextView)
  Worker-->>Engine: WorkResult.OK (fixed)

  Engine->>ALS: proposeMock(...)
  ALS->>Art: build (PROPOSED) + store
  ALS-->>Engine: Artifact
  Engine->>ALS: commit(artifactId)
  ALS->>Art: commit() → COMMITTED
  Engine->>ALS: requireCommitted(artifactId)
  Engine->>CLS: indexCommitted(context, id, COMMITTED)
  CLS->>Ctx: indexArtifact(id)

  Note over Engine: G4 intent — terminal + freeze
  Engine->>TLS: transition(SUCCEEDED)
  TLS->>Task: transitionTo(SUCCEEDED)
  Engine->>CLS: freeze(context)
  CLS->>Ctx: freeze()

  Engine-->>Caller: RuntimeResult (success)
```

## 3. Enforcement 映射（本骨架）

| Invariant | 如何保证 |
|-----------|----------|
| G1 | 唯一入口 `Runtime.submit` 必建 Task |
| G2 | 结果只带回 `ArtifactId` 列表 |
| G3 / T4 | 一次 materialize + bind；无第二 Context |
| G4 | SUCCEEDED 后立即 freeze；测试断言 frozen |
| G5 / E4 | `indexCommitted` 拒绝非 COMMITTED |
| T2 | 仅经 `Task.transitionTo` + 合法表 |
| A3 | `propose` → `commit` 管线 |
| E3 | freeze 后写索引抛错（单测覆盖） |

## 4. 模块入口

- Java：`ai4se-runtime-engine` → `com.ai4se.runtime.engine.Runtime`
- 测试：`RuntimeWalkingSkeletonTest`
