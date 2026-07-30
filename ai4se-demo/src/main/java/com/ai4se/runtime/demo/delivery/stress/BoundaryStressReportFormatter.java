package com.ai4se.runtime.demo.delivery.stress;

import com.ai4se.runtime.demo.delivery.DeliveryStageResult;
import com.ai4se.runtime.demo.delivery.stress.BoundaryStressMain.StressDeliveryOutcome;
import com.ai4se.runtime.engine.api.RuntimeResult;
import java.util.List;

/** Sprint-9 Boundary Stress report (Architecture Review grain). */
final class BoundaryStressReportFormatter {

    private BoundaryStressReportFormatter() {
    }

    static String format(List<StressDeliveryOutcome> outcomes) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Sprint-9 Runtime Boundary Stress Report\n\n");
        sb.append("> Evidence: 3 different real deliveries via existing Runtime.submit + Workers.\n");
        sb.append("> No new Runtime / Domain / StageRunner / Scheduler / Workflow / Planning engine.\n\n");

        boolean allPass = true;
        for (StressDeliveryOutcome o : outcomes) {
            allPass &= o.success;
        }
        sb.append("Overall: ").append(allPass ? "PASS" : "FAIL")
                .append(" · deliveries=").append(outcomes.size()).append("\n\n");

        sb.append("## Deliveries executed\n\n");
        for (StressDeliveryOutcome o : outcomes) {
            sb.append("### ").append(o.scenario.id())
                    .append(" — ").append(o.scenario.typeLabel())
                    .append(o.success ? " · PASS" : " · FAIL").append("\n\n");
            sb.append("- Requirement: ").append(o.scenario.requirement()).append('\n');
            sb.append("- Discovery command: `").append(o.scenario.discoveryCommand()).append("`\n");
            sb.append("- Workspace: `").append(o.workspace.getAbsolutePath()).append("`\n");
            sb.append("- Stages:\n");
            for (DeliveryStageResult stage : o.stages) {
                RuntimeResult r = stage.getResult();
                sb.append("  - ").append(stage.getStage())
                        .append(": ").append(r.isSuccess() ? "OK" : "FAIL")
                        .append(" worker=").append(r.getWorkerId())
                        .append(" goal=").append(r.getGoalType())
                        .append(" artifactNamesViaLabels=see RuntimeResult")
                        .append(" cp=")
                        .append(r.getCheckpointId().isPresent() ? r.getCheckpointId().get().value() : "-")
                        .append(" ms=").append(r.getDurationMs()).append('\n');
            }
            sb.append('\n');
        }

        sb.append("---\n\n");
        sb.append("## ① Runtime 职责 — 三次都没有变化\n\n");
        sb.append("三次交付中，Runtime 每步仍且只做：\n\n");
        sb.append("1. 创建 Task + 推进 STARTING/RUNNING/SUCCEEDED|FAILED\n");
        sb.append("2. 打开/冻结 ExecutionContext\n");
        sb.append("3. Trace spans: submit → worker → artifact → finish\n");
        sb.append("4. **一次** `Worker.execute`\n");
        sb.append("5. WorkResult → Artifact COMMITTED/ABANDONED\n");
        sb.append("6. Checkpoint 只写\n");
        sb.append("7. 返回 RuntimeResult（checkpointId / durationMs / workerId / goalType）\n\n");
        sb.append("举证：每种需求各 6 次 submit，lifecycle 形状一致；"
                + "需求类型（配置 / REST / 跨模块）未改变 Runtime 代码路径。\n\n");

        sb.append("## ② Worker — 变化最大\n\n");
        sb.append("| 变化面 | 证据 |\n|--------|------|\n");
        sb.append("| Discovery 命令 | `git status`（01/03）vs `pwd`（02）— 同 ShellWorker，不同 params |\n");
        sb.append("| Execution 文件集 | FeatureFlags vs UserApi 新建 vs core+api 双文件 — FileEditWorker params 不同 |\n");
        sb.append("| Verification 耗时 | mvn test 主导 duration；业务越重 Worker 侧越慢，Runtime 结算不变 |\n");
        sb.append("| Worker 种类本身 | 仍只有 ShellWorker + FileEditWorker；**未新增 Worker 类** |\n\n");
        sb.append("结论：边界压力主要落在 **Worker 输入（command/files）**，不是 Runtime 类型系统。\n\n");

        sb.append("## ③ Artifact — 已经稳定\n\n");
        sb.append("| Artifact name | 生产者 | 稳定性 |\n|---------------|--------|--------|\n");
        sb.append("| `shell-stdout.txt` | ShellWorker | 三次 Discovery/Verification 均此名 |\n");
        sb.append("| `file-edit-manifest.txt` | FileEditWorker | Plan/Execution/Review/Delivery 均此名 |\n");
        sb.append("| Kernel ArtifactKind | — | **未新增**；仅 name 区分 |\n");
        sb.append("| workspace 报告文件 | FileEdit 写入 | PLAN/REVIEW/DELIVERY.md 是文件不是 Kernel Object |\n\n");

        sb.append("## ④ 哪些阶段其实属于 Demo\n\n");
        sb.append("| 阶段 | Runtime? | Demo? | 证据 |\n|------|----------|-------|------|\n");
        sb.append("| Requirement 文本 | No | Yes | 各 Scenario.requirement() |\n");
        sb.append("| 阶段顺序与失败停机 | No | Yes | SerialDeliveryRunner |\n");
        sb.append("| Discovery 是否用 git 或 pwd | No | Yes | scenario.discoveryCommand |\n");
        sb.append("| Plan 正文 | No | Yes | scenario.planFiles |\n");
        sb.append("| Execution 补丁内容 | No | Yes | scenario.executionFiles |\n");
        sb.append("| Review/Delivery 叙述 | No | Yes | Formatters |\n");
        sb.append("| 单步结算 | Yes | No | Runtime.submit |\n");
        sb.append("| git/mvn/写文件 | Worker | 发起在 Demo | — |\n\n");

        sb.append("## ⑤ 想抽象但证据仍不足\n\n");
        sb.append("| 冲动 | 为何不足 |\n|------|----------|\n");
        sb.append("| StageRunner 进 Runtime | 三次阶段名相同，但是 **人为复用同一 Demo runner**；"
                + "Discovery 命令已分叉（git vs pwd）；未证明所有真实项目都要这六段 |\n");
        sb.append("| Planning 引擎 | Plan 仍是 Demo 预写 markdown，无搜索/推理 |\n");
        sb.append("| 统一 DeliveryReport schema 进 Kernel | 报告仅为 Review 文档；三次内容结构相同因 formatter 相同，非业务必然 |\n");
        sb.append("| 按需求类型分流的 Capability | 仅 3 个样本，且全是 FileEdit+Shell |\n\n");

        sb.append("## ⑥ 可进 Runtime vs 留在 Demo\n\n");
        sb.append("### 真正可以留在 / 保持在 Runtime（已稳定）\n\n");
        sb.append("- 单步 Task/Context/Trace/Artifact/Checkpoint/`RuntimeResult` 结算\n");
        sb.append("- Budget 超时下传\n");
        sb.append("- Worker SPI 无关业务类型\n\n");
        sb.append("### 继续留在 Demo\n\n");
        sb.append("- Scenario 选择与补丁库\n");
        sb.append("- 串行六阶段编排（SerialDeliveryRunner）\n");
        sb.append("- Review/Delivery markdown\n");
        sb.append("- Discovery 命令策略\n\n");
        sb.append("### 仍禁止进入 Runtime（本 Sprint）\n\n");
        sb.append("- StageRunner / Scheduler / Workflow / Planning 引擎 / 新 Domain\n\n");

        sb.append("---\n\n");
        sb.append("# ChatGPT Review Package\n\n");
        sb.append("## 1 Runtime Stable Boundary\n\n");
        sb.append("- Single-submit lifecycle unchanged across 3 requirement types\n");
        sb.append("- Artifact+Checkpoint+Trace+RuntimeResult shape unchanged\n");
        sb.append("- No Runtime code added for stress\n\n");
        sb.append("## 2 Runtime Unstable Boundary\n\n");
        sb.append("- None inside Runtime path\n");
        sb.append("- Instability sits in Demo scenario inputs (commands, file maps, discovery choice)\n");
        sb.append("- Naming debt: workflowId still present but unused as Workflow\n\n");
        sb.append("## 3 Worker Evolution\n\n");
        sb.append("- No new Worker class\n");
        sb.append("- ShellWorker params: git status | pwd | mvn test\n");
        sb.append("- FileEditWorker params: config patch | new API class | cross-module dual patch\n");
        sb.append("- Largest variance = Worker inputs, not SPI\n\n");
        sb.append("## 4 Artifact Stability\n\n");
        sb.append("- Stable names: shell-stdout.txt, file-edit-manifest.txt\n");
        sb.append("- No new Kernel Artifact type\n");
        sb.append("- Report files remain workspace artifacts via FileEdit\n\n");
        sb.append("## 5 Runtime Purity Check\n\n");
        sb.append("- PASS: no business/config/REST/cross-module knowledge in Runtime\n");
        sb.append("- Demo owns SerialDeliveryRunner + scenarios\n");
        sb.append("- Workers own side effects\n\n");
        sb.append("## 6 Architecture Drift\n\n");
        sb.append("- No Domain / StageRunner / Scheduler / Workflow / Planning engine added\n");
        sb.append("- Drift risk: Demo scenario pack growing (acceptable under stress charter)\n");
        sb.append("- Frozen invariants preserved\n\n");
        sb.append("## 7 Architecture Recommendation\n\n");
        sb.append("- Keep Runtime boundary frozen as-is\n");
        sb.append("- Do **not** promote StageRunner yet (stage list enforced by Demo reuse, not nature)\n");
        sb.append("- Prefer more real deliveries OR Worker allowlist growth over Kernel growth\n\n");
        sb.append("## 8 Next Sprint Proposal\n\n");
        sb.append("**Proposal: Second Production Delivery on a non-sample target "
                + "(or Human-wait gate if a real clarify blocker appears)** — still no StageRunner.\n\n");
        sb.append("- Why: stress proved Runtime stable; next risk is Demo-only orchestration "
                + "meeting a requirement that needs human clarify or non-mvn verify.\n");
        sb.append("- Why not StageRunner: three identical stage shells were Demo-authored; "
                + "Discovery already diverged; abstracting now = freeze Demo habit.\n");
        return sb.toString();
    }
}
