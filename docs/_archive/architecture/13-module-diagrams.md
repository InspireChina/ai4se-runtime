# 13 · Module Relationship Diagrams

## 模块全景（统一后）

```mermaid
flowchart TB
  subgraph hosts
    host_api
    host_cli
  end

  subgraph console
    console_web
  end

  subgraph runtime
    runtime_kernel
    engine_workflow
    engine_skill
    engine_rule
    engine_capability
    engine_model
    engine_plugin
    engine_repo_graph
    engine_knowledge
    engine_profile
    engine_observation
  end

  subgraph sdk
    capability_sdk
    capability_sdk_test
  end

  subgraph spi
    spi_all[spi-*]
    spi_persistence
  end

  subgraph adapters
    adapter_tech[adapter-fs/git/model/...]
    adapter_persistence_jdbc
  end

  subgraph plugins
    plugin_builtin
    plugin_domain[plugin-coding-loop]
  end

  console_web -.HTTP.-> host_api
  host_api --> runtime_kernel
  host_cli --> host_api

  runtime_kernel --> engine_workflow
  runtime_kernel --> engine_skill
  runtime_kernel --> engine_rule
  runtime_kernel --> engine_capability
  runtime_kernel --> engine_model
  runtime_kernel --> engine_plugin
  runtime_kernel --> engine_repo_graph
  runtime_kernel --> engine_knowledge
  runtime_kernel --> engine_profile
  runtime_kernel --> engine_observation
  runtime_kernel --> spi_persistence

  engine_capability --> spi_all
  engine_workflow --> spi_all
  engine_knowledge --> spi_all
  engine_profile --> spi_all

  plugin_domain --> spi_all
  plugin_domain --> capability_sdk
  plugin_builtin --> spi_all
  capability_sdk --> spi_all
  capability_sdk_test --> capability_sdk

  adapter_tech --> spi_all
  adapter_persistence_jdbc --> spi_persistence

  engine_capability -.forbids.-> capability_sdk
```

## 扩展开发关系

```mermaid
flowchart LR
  Dev --> Profile[Project Profile]
  Dev --> Plugin
  Dev --> CapSDK[Capability SDK]
  CapSDK --> Capability
  Plugin --> Capability
  Plugin --> Skill
  Plugin --> Rule
  Plugin --> Workflow
  Plugin --> KnowledgePack
  Profile --> Plugin
  Profile --> KnowledgePack
  Task --> Profile
  Task --> Workflow
```

## 数据流

```mermaid
flowchart LR
  Req[TaskRequest] --> API
  API --> TLM[Task Lifecycle]
  TLM --> PF[Profile Snapshot]
  TLM --> WF
  WF --> RL
  WF --> SK
  SK --> CAP
  CAP --> Port --> Adapter --> Ext
  TLM --> CP[CheckpointStore]
  TLM --> TR[TraceStore]
  SK --> KN[Knowledge]
  SK --> RG[RepoGraph]
```

## 测试与模块

| 测试 | 模块 |
|------|------|
| Unit | 单 Engine、SDK Harness |
| ArchUnit | 依赖方向（含禁止 engine→sdk） |
| Contract | Profile/Plugin/Workflow/Rule 描述符 |
| API | host-api Testcontainers |
| E2E | Fake Model + 样例 Profile（后期） |
