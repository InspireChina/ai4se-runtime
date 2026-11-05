# Architecture · 工程结构

> 思想宪法：[docs/00-product/capability-map.md](./docs/00-product/capability-map.md)  
> **新代码按能力域落点；旧 Maven 模块迁入中，叙事不再以 module 为产品。**

## 目标树

```text
ai4se-runtime/
├── docs/                 # 八域文档（已落地）
├── templates/            # 标准物
├── adapters/             # 04 AI Execution（渐进迁入）
├── scripts/              # 01 onboard 等
├── examples/
├── ARCHITECTURE.md       # 本文件
├── README.md
│
├── ai4se-common/         # 过渡：公共类型 → 将来并入 foundation 或拆分
├── ai4se-kernel/         # 过渡 → 07 Runtime Foundation
├── ai4se-worker-api/     # 过渡 → 07
├── ai4se-workers/        # 过渡 → 04 adapters + 07 Worker 端口
├── ai4se-runtime-engine/ # 过渡 → 07 + 少量 03 门禁
├── ai4se-context/        # 02 Context Package Builder（W2 起）
├── ai4se-execution/      # 04 Adapters（Cursor + Claude CLI）
├── ai4se-orchestration/  # 03 Workflow + Control（W3 起）
├── ai4se-demo/           # 试验 / 主链验证
└── ai4se-review-tools/   # 过渡 → templates/review + 工具
```

## 目标模块映射（渐进，不一次大爆炸）

| 能力域 | 目标模块名 | 当前落点 |
|--------|------------|----------|
| 01 Repository Intelligence | `ai4se-repository` | `scripts/onboard-repo.sh` + 后续新建 |
| 02 Context Engineering | `ai4se-context` | **`ai4se-context/`**（Analysis Package + Acceptance 拒跑） |
| 03 Delivery Orchestration | `ai4se-orchestration` | **`ai4se-orchestration/`**（Story 状态机 + `.story` 持久化） |
| 04 AI Execution | `ai4se-execution` + `adapters/*` | **`ai4se-execution/`**（CursorCliAdapter + ClaudeCliAdapter）+ 旧 `ai4se-workers` |
| 05 Verification | `ai4se-verification` | 后续；现靠 shell/demo |
| 06 Knowledge Lifecycle | `ai4se-learning` | 后续 |
| 07 Runtime Foundation | `ai4se-foundation` | `kernel` + `worker-api` + `runtime-engine` 核心 |
| 08 Infrastructure | `ai4se-infrastructure` | demo 启动 / 脚本 |

**纪律：** 新功能只问落 01–08 哪一域；禁止新开「随缘模块」当产品概念。

## 主链与代码

优先打通顺序见 [PATHWAY-VERIFICATION-HANDBOOK.md](./PATHWAY-VERIFICATION-HANDBOOK.md) 车站 Wave：  
建槽 → 开 Story → Context Package（每阶段）→ 状态机 + Adapter → 客户测试/Defect 回环 → Review → 本地 Commit →（后）人验收/知识回写。
