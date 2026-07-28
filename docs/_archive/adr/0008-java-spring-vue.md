# ADR-0008 · Java + Spring Boot + Vue 技术栈

- Status: **Accepted**
- Date: 2026-07-28
- Tags: tech-stack

## Context

Runtime 需要长期演进、企业集成、可观测与控制台；团队要求 Java + Spring Boot + Vue 优先。

## Decision

1. Runtime 与 Host API 使用 **Java 8 / Maven 多模块**（当前团队基线为 Corretto 1.8；后续若升级 JDK 另开 ADR）
2. 控制台规划为 **Vue 3 + TypeScript + Vite**（实现阶段），面向 Task/Trace/Profile，不做通用 Chat 产品
3. Kernel/Engines 不依赖 Web 框架类型；经装配注入
4. Capability SDK 为纯 Java 库，引擎不依赖 SDK
5. 默认持久化 PostgreSQL；开发可用 H2

> 初版曾规划 Java 21 + Spring Boot 3；Sprint-1 按团队现状冻结为 **Java 8 可编译骨架**。Spring Boot 版本随宿主引入时再选与 Java 8 兼容的线（如 Boot 2.7.x）。

## Consequences

- 目录增加 `host-api`、`console-web`、`sdk/`
- 招聘与技能模型按 Java/Vue 配置
- 不采用 Python Agent 框架作为 Kernel

## Follow-ups

- 架构文档 `22-tech-stack.md` 为部署视图权威来源
