# Asset Hosting · 资产宿主

> 执行时不糊涂：什么在客户仓、什么拷到云桌面、什么只留本仓。

## 三方

| 放哪 | 是什么 |
|------|--------|
| **客户仓（Git）** | 长期资产 + Story 过程产物 |
| **客户云桌面（运行包）** | 从本仓发布的编排 + Builder + Adapter + Runtime + 模板副本 |
| **本仓（ai4se-runtime）** | 八域真源 · 模板演进 · 实现 · 文档 |

## 客户仓

```text
.ai4se/          Repository Intelligence 基线 · Knowledge · Learning · Rule/Skill
.story/          各阶段过程产物与状态
src/ · tests/    源码与客户测试能力
.git             本地 Commit（不 Push）
```

## 云桌面运行包

- 03 Delivery Orchestration  
- 02 Context Builder  
- 04 AI Execution（Adapters）  
- 07 Runtime Foundation  
- 模板与 Contract 副本  
- 08 启动/配置  
- Host Bridge + `terminal-host` Profile（当前模型工具调用 Runtime 的薄接入层）

**禁止：** 把客户业务 Knowledge 正文、完整源码拷回本仓。

## 本仓

- docs/ 八域宪法与合同  
- templates/ 标准物真源  
- 实现代码（按能力域归属演进）  
- scripts/（如 onboard）  

## Onboarding

**01 脚本**在客户仓建槽；通常**不**进无人值守主环。主环从「Story + 已有槽位」开始。

## 对照能力域

| 域 | 主要长在哪 |
|----|------------|
| 01 | 客户仓资产 + 本仓/桌面扫描脚本 |
| 02 | 桌面 Builder（读客户仓，不回流） |
| 03 | 桌面编排（状态可写 `.story`） |
| 04 | 桌面 Adapter |
| 05 | 桌面调用器；**测试能力在客户** |
| 06 | 策略在运行包；**正文回写客户仓** |
| 07 / 08 | 运行包 / 本仓 |
