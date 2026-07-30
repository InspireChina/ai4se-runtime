# Repository Facts

> Analyzer 只读输出。不含 Plan / Patch / Implementation。

## Workspace

`ai4se-demo/pilot-workspace-promotion`（第一次摸底用的可用根；非已证实的生产 Promotion 单体全仓）。

## Map

| 路径 | 说明 |
|------|------|
| `README.md` | 唯一文件 |

## Build / Modules

- 未发现 `pom.xml` / `build.gradle` / `package.json`
- 未发现 `src/main/java` 或其它源码树
- 模块列表：**(empty)**

## Hits (TopK search: promotion / 促销 / 满减 / order / coupon / 秒杀 / refund)

| Path | Excerpt | Score |
|------|---------|-------|
| `README.md` | Promotion Service (legacy surface unknown)… | low |

## Facts（可复查）

1. 工作区仅含 `README.md`。
2. README 声明：Database tables / OrderService / Promotion domain **unknown**，且写明不得由分析臆造。
3. Facts 中**未观察到** Promotion 表、Repository、Service、Controller。
4. Facts 中**未观察到** 店铺券、秒杀、退款、多商品计价相关代码或配置。
5. Facts 中**未观察到**「满300减50」或通用满减阶梯实现。
6. 扫描策略：map + filename/keyword search；无 Graph。

## Meta

- strategy: map+search
- truncated: false（文件极少）
- invent-schema: **forbidden**
