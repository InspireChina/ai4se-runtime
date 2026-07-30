# Gap Report

## gap_status

**BLOCKED**

## Counts

- blocking_gap_count: **7+**（关键未知均未关闭）
- assumable_gap_count: **0**（本次禁止用假设推进业务）

## Known

- Requirement 字面目标：满 300 减 50；叠店铺券；不叠秒杀；要退款；要多商品
- Facts：工作区几乎为空；无 Promotion/Order/券/秒杀实现证据
- Context confidence = low
- 合同要求：关键 Unknown 必须 BLOCKED，不得进 Planning

## Unknown

- 真实仓库与模块位置
- Promotion 是否已有及模型
- Order 计价入口
- 店铺券 / 秒杀 模型与互斥/叠加机制
- 满减金额口径
- 退款与多商品分摊规则
- 与「已有优惠」的兼容面（若存在）

## Decision Needed

1. 指定真实 Promotion/订单代码仓与模块边界
2. 确认或否定现有 Promotion / 券 / 秒杀 / 退款能力
3. 定义满减口径、叠加/互斥、退款、多商品分摊的产品规则（可测）
4. 明确「第一次需求」下是绿场新建还是改存量（Facts 无法判定）

## Risk

- 在 BLOCKED 下进入 Planning/Coding → 臆造表与服务，破坏已有优惠或错接订单
- 叠券/斥秒杀规则不清 → 资损或客诉
- 退款规则不清 → 资金对账错误
- 把占位 README 仓当成生产仓 → 整条交付无效

## Gate

- **禁止 Planning**
- **禁止 Execution / Verification / Delivery（业务交付）**
- 仅允许：澄清问答 → Gap Re-check
