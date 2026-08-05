# Adapter Contract

> 能力域 **04 · AI Execution**。  
> **不拥有业务知识，不拥有流程控制。**

## 输入

- Context Package（由 02 Builder 产出）  
- 执行参数（工作目录、超时等，由 03/08 注入）

## 输出

- 原始/规范化执行结果（stdout/stderr/exit/产物路径指针）  
- **不得**自行决定 Retry、换阶段、熔断

## 禁止

- Adapter 内写「失败就再试三次并跳过 Verification」等控制流  
- Adapter 读取客户仓 Knowledge 全树自行检索（检索在 02）  
- 将 Worker 实现成「Claude 业务 Agent」

## Worker 关系

Runtime（07）Worker = 统一调用端口；本域 Adapter = 被调用的手。
