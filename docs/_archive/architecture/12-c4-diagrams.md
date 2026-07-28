# 12 · C4 Architecture Diagrams

## Level 1 — System Context

```mermaid
flowchart LR
  Dev[Developer / Approver] --> Console[Vue Console]
  Dev --> CLI[CLI]
  Plat[Platform Engineer] --> Console
  Plat --> Profiles[Profile & Plugin repos]
  Console --> Runtime[AI SE Runtime<br/>Spring Boot]
  CLI --> Runtime
  Runtime --> LLM[LLM Providers]
  Runtime --> VCS[Git / VCS Host]
  Runtime --> CI[CI/CD]
  Runtime --> Tracker[Issue Tracker]
  Runtime --> WS[Project Workspaces]
  Runtime --> DB[(PostgreSQL)]
```

**说明**：人与系统的主交互是 Task 提交、策略例外批准、Profile 配置与 Trace 复盘；不是开放域聊天。

## Level 2 — Containers

```mermaid
flowchart TB
  subgraph Client
    Vue[console-web Vue3]
    CLI[host-cli]
  end

  subgraph App["host-api Spring Boot"]
    REST[REST Controllers]
    Facades[Facades]
    Kernel[Kernel + Engines]
    Workers[Task Workers]
  end

  subgraph Data
    PG[(PostgreSQL)]
    Vol[Workspace Volume]
  end

  subgraph Ext
    Plugins[Plugin JARs]
    Adapters[Adapter Beans]
  end

  Vue --> REST
  CLI --> REST
  REST --> Facades --> Kernel
  Kernel --> Workers
  Kernel --> PG
  Kernel --> Vol
  Kernel --> Plugins
  Kernel --> Adapters
  Adapters --> ExtSys[External Systems]
```

## Level 3 — Components

```mermaid
flowchart TB
  subgraph Facades
    TaskF[Task Facade]
    ProfF[Profile Facade]
    ObsF[Observation Facade]
    KnowF[Knowledge Facade]
    ExtF[Extension Facade]
  end

  subgraph Kernel
    TLM[Task Lifecycle]
    CPS[Checkpoint Service]
    TRS[Trace Service]
    PG[Policy Guard]
    EB[Event Bus]
  end

  subgraph Engines
    WF[Workflow]
    SK[Skill]
    RL[Rule]
    CAP[Capability]
    MD[Model]
    PL[Plugin]
    RG[RepoGraph]
    KN[Knowledge]
    PF[Profile]
    OBS[Observation]
  end

  TaskF --> TLM
  ProfF --> PF
  KnowF --> KN
  ObsF --> OBS
  ExtF --> PL
  TLM --> CPS
  TLM --> TRS
  TLM --> WF
  TLM --> RL
  TLM --> PF
  WF --> SK
  SK --> CAP
  SK --> KN
  CAP --> RG
  OBS --> TRS
  PL --> WF
  PL --> SK
  PL --> RL
  PL --> CAP
```

## Level 4 — Code 指引

类级细节见各 RFC；实现约定：

- `com.ai4se.runtime.host.api.*` — Controllers / DTOs
- `com.ai4se.runtime.kernel.task.*` — Task 状态机
- `com.ai4se.runtime.kernel.checkpoint.*`
- `com.ai4se.runtime.kernel.trace.*`
- `com.ai4se.runtime.engine.*`
- `com.ai4se.runtime.sdk.capability.*` — 仅 Plugin 使用
