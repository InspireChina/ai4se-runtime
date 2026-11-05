# Vision · 为什么做这个项目

> **北极星。** 长期方向不随试验、模型、实现细节改口。  
> 能力地图：[capability-map.md](./capability-map.md)

---

## 这个项目是什么

**AI Delivery Orchestrator 不是一个 AI，也不是一个 Runtime。**

它是一套 **AI 软件交付标准（AI Software Delivery Standard）**，按 **八个能力域** 组织，并固化成可在客户云桌面无人值守执行的交付系统。

真正执行工作的始终是 Claude CLI、Cursor CLI 或未来任何模型。  
本项目 **不负责替代它们**。  
本项目负责让 **任何模型**，在 **任何客户项目** 上，按 **同一套交付标准** 稳定工作。

一句话：

> **不替客户开发。组织 AI 按标准去开发客户项目。**

---

## 为什么要做

今天 AI 最大的问题不是不会写代码，而是结果不稳定、不可恢复、不可验证、换模型就失效。

真正缺失的是：

> **一套稳定的软件交付标准。**

中心思想不是「Claude 怎么写代码」，而是：

> **「AI 软件工程应该是什么样子？」**

文档与代码叙事应围绕：**八能力域 · Contract · Context Package · Orchestration · Verification · Knowledge Lifecycle**。  
越来越少把 Runtime / Worker / 某一家模型写成产品中心。

---

## 真正值钱的一层

**Context Engineering：** 在有限 Token 下让 AI 获得最大有效信息。

Claude / Cursor 谁都会调；知道何时裁剪、装什么、禁止什么，才是产品。

---

## 成功的标准

> 面对 **新客户项目** + **新 Story**，按主链无人值守跑到本地 Commit，等待人工验收——**稳定 · 可恢复 · 可验证 · 与具体模型无关。**

主链见 [capability-map.md](./capability-map.md)。

不是：Engine 功能越来越多、Prompt 越来越长、概念越来越多。

---

## 相关文档

- 宪法：[capability-map.md](./capability-map.md)  
- 原则：[philosophy.md](./philosophy.md)  
- 宿主：[asset-hosting.md](./asset-hosting.md)  
- 名词：[glossary.md](./glossary.md)
