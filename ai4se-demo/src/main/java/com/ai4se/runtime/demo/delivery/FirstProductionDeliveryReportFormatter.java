package com.ai4se.runtime.demo.delivery;

import com.ai4se.runtime.engine.api.RuntimeResult;
import java.io.File;
import java.util.List;

/**
 * Canonical First Production Delivery Report (Architecture Review grain).
 * Includes ChatGPT Review Package section.
 */
final class FirstProductionDeliveryReportFormatter {

    private FirstProductionDeliveryReportFormatter() {
    }

    static String format(String requirement, List<DeliveryStageResult> stages, File workspace) {
        boolean ok = allSucceeded(stages);
        long totalMs = 0L;
        for (DeliveryStageResult stage : stages) {
            totalMs += stage.getResult().getDurationMs();
        }

        StringBuilder sb = new StringBuilder();
        sb.append("# First Production Delivery Report\n\n");
        sb.append("Mode: First Production Delivery\n\n");
        sb.append("Overall: ").append(ok ? "PASS" : "FAIL")
                .append(" · TotalDurationMs=").append(totalMs).append("\n\n");
        sb.append("---\n\n");

        sb.append("## 【A】本次 Requirement\n\n");
        sb.append(requirement).append("\n\n");
        sb.append("Workspace (run copy): `").append(workspace.getAbsolutePath()).append("`\n\n");

        sb.append("## 【B】Execution Flow（Mermaid）\n\n");
        sb.append("```mermaid\n");
        sb.append("flowchart TD\n");
        sb.append("  R[Requirement] --> D[Discovery: ShellWorker git status]\n");
        sb.append("  D --> P[Plan: FileEditWorker PLAN.md]\n");
        sb.append("  P --> E[Execution: FileEditWorker patch]\n");
        sb.append("  E --> V[Verification: ShellWorker mvn test]\n");
        sb.append("  V --> RV[Review: FileEditWorker REVIEW_REPORT.md]\n");
        sb.append("  RV --> DL[Delivery: FileEditWorker DELIVERY_REPORT.md]\n");
        sb.append("```\n\n");
        sb.append("Stage outcomes:\n\n");
        for (DeliveryStageResult stage : stages) {
            RuntimeResult r = stage.getResult();
            sb.append("- ").append(stage.getStage()).append(": ")
                    .append(r.isSuccess() ? "OK" : "FAIL")
                    .append(" task=").append(r.getTaskId().value())
                    .append(" worker=").append(r.getWorkerId())
                    .append(" cp=")
                    .append(r.getCheckpointId().isPresent() ? r.getCheckpointId().get().value() : "-")
                    .append(" ").append(r.getDurationMs()).append("ms\n");
        }
        sb.append('\n');

        sb.append("## 【C】修改文件列表\n\n");
        sb.append("### Modified (sample workspace via Runtime Worker)\n\n");
        sb.append("- `src/main/java/com/example/delivery/ConfigService.java`\n");
        sb.append("- `README.md`\n\n");
        sb.append("### Added (sample workspace via Runtime Worker)\n\n");
        sb.append("- `src/main/java/com/example/delivery/TimeoutSource.java`\n");
        sb.append("- `PLAN.md`\n");
        sb.append("- `REVIEW_REPORT.md`\n");
        sb.append("- `DELIVERY_REPORT.md`\n\n");
        sb.append("### Deleted\n\n");
        sb.append("- (none)\n\n");
        sb.append("### Supporting Runtime / Demo changes (agent-authored to enable proof)\n\n");
        sb.append("- Modified: `Runtime.java`, `RuntimeResult.java`, `ShellWorker.java`\n");
        sb.append("- Added: `FileEditWorker.java`, demo delivery package, sample workspace, tests\n");
        sb.append("- Added: `docs/first-production-delivery-report.md` (this file)\n\n");

        sb.append("## 【D】新增 Class\n\n");
        sb.append("| Class | 职责 | 为什么不能复用已有对象 |\n");
        sb.append("|-------|------|------------------------|\n");
        sb.append("| `FileEditWorker` | 在 workspace 内写文本文件并产出 manifest Artifact | "
                + "`ShellWorker` 仅允许 echo/pwd/git/mvn；不能安全承载多文件补丁 |\n");
        sb.append("| `TimeoutSource` (sample) | 超时配置接口 | 业务需求「增加一个接口」；不属于 Runtime Kernel |\n");
        sb.append("| Demo delivery formatters / pipeline | 多阶段编排 + 报告文本 | "
                + "禁止新增 Workflow；编排暂放 Demo |\n\n");

        sb.append("## 【E】新增 Artifact Type\n\n");
        sb.append("- **未新增 Kernel ArtifactKind / Domain。**\n");
        sb.append("- Worker 仅通过现有 Artifact 管线提交不同 **name**：\n");
        sb.append("  - `file-edit-manifest.txt`（FileEditWorker）\n");
        sb.append("  - `shell-stdout.txt`（ShellWorker）\n");
        sb.append("- 报告文件是 workspace 文件，不是新 Kernel Object。\n\n");

        sb.append("## 【F】新增 Test\n\n");
        sb.append("| Test | 覆盖 |\n");
        sb.append("|------|------|\n");
        sb.append("| `FileEditWorkerTest` | 写文件成功；拒绝 `..` 路径 |\n");
        sb.append("| `ShellWorkerTest` (extended) | `mvn -f … -q test` allowlist 解析 |\n");
        sb.append("| `FirstProductionDeliveryTest` | 端到端六阶段交付 + VERIFICATION 绿 |\n");
        sb.append("| `CheckpointIntegrationTest` / result fields | `RuntimeResult.checkpointId` / `durationMs` |\n");
        sb.append("| sample `ConfigServiceTest` | 业务验收：timeoutMs==5000 |\n\n");

        sb.append("## 【G】Architecture Drift\n\n");
        sb.append("| Check | Result |\n");
        sb.append("|-------|--------|\n");
        sb.append("| 新增 Domain | **No** |\n");
        sb.append("| 新增 Kernel Object | **No** |\n");
        sb.append("| 新增 Scheduler / Workflow / Plugin / Capability / Knowledge / Rule / Resume / DB / Bus / MQ | **No** |\n");
        sb.append("| 违反 Frozen | **No**（仍 Orchestrator + Worker SPI；Checkpoint 只写不 Resume） |\n");
        sb.append("| 职责漂移 | Demo 承担阶段编排（证据见 Lessons）；Runtime 仍单次 submit |\n");
        sb.append("| 过度设计 | FileEditWorker 为最小可证伪补丁工人；无引擎空壳 |\n\n");

        sb.append("## 【H】Lessons Learned\n\n");
        sb.append("Evidence only:\n\n");
        sb.append("1. **Runtime.submit = 单 Worker 步**：六阶段靠 Demo 串 6 次 submit；"
                + "没有 Runtime 内阶段状态机也能交付，但交付编排不在 Runtime 边界内。\n");
        sb.append("2. **Delivery Report 字段缺口曾存在**：本次给 `RuntimeResult` 补了 "
                + "`checkpointId` / `durationMs` / `workerId` / `goalType`，否则报告只能翻 store。\n");
        sb.append("3. **真交付需要写文件 + 跑测**：原 Shell allowlist（echo/pwd/git）不足以改代码；"
                + "FileEditWorker + 受限 `mvn -f … -q test` 是证据驱动扩展，不是平台抽象。\n");
        sb.append("4. **预算超时必须传到 Worker**：Verification 需 >30s；Runtime 改为使用 "
                + "`Budget.maxWallClock`，否则 mvn 会被 ShellWorker 杀掉。\n\n");

        sb.append("## 【I】Next Capability Recommendation（仅一个）\n\n");
        sb.append("**推荐：Runtime Engine 内的固定串行 Stage Runner（硬编码阶段列表驱动多次 Worker 调用，"
                + "仍无 Workflow DSL / Scheduler）。**\n\n");
        sb.append("- **为什么：** 本次交付证明瓶颈在「阶段编排落在 Demo」，报告与验收需要跨阶段证据串联。\n");
        sb.append("- **为什么不是别的：** 不是 Human-wait（本需求无澄清阻塞）；不是 Resume（未出现恢复痛点）；"
                + "不是 Knowledge/Plugin（单样本交付未暴露召回/扩展问题）。\n\n");

        sb.append("---\n\n");
        sb.append("# ChatGPT Review Package\n\n");
        sb.append("> Architecture Review only — no source dumps, no log walls.\n\n");
        sb.append("## 1. 修改文件列表\n\n");
        sb.append("- Sample (via Worker): ConfigService.java, README.md; added TimeoutSource.java, PLAN/REVIEW/DELIVERY reports\n");
        sb.append("- Runtime: Runtime.java, RuntimeResult.java\n");
        sb.append("- Workers: ShellWorker.java; added FileEditWorker.java\n");
        sb.append("- Demo/Test: delivery package, FirstProductionDeliveryTest, FileEditWorkerTest\n");
        sb.append("- Docs: docs/first-production-delivery-report.md\n\n");
        sb.append("## 2. 新增对象职责\n\n");
        sb.append("- FileEditWorker: workspace text patch → Artifact manifest\n");
        sb.append("- TimeoutSource: sample domain interface\n");
        sb.append("- Demo pipeline/formatters: external stage orchestration + report text\n\n");
        sb.append("## 3. 新增 Artifact Type\n\n");
        sb.append("- None at Kernel level. New Artifact **names** only: file-edit-manifest.txt\n\n");
        sb.append("## 4. Execution Mermaid\n\n");
        sb.append("```mermaid\n");
        sb.append("flowchart LR\n");
        sb.append("  Req --> Discovery --> Plan --> Execution --> Verification --> Review --> Delivery\n");
        sb.append("```\n\n");
        sb.append("## 5. Architecture Drift Summary\n\n");
        sb.append("- No new Domain / Kernel Object / platform abstraction.\n");
        sb.append("- Frozen preserved; Checkpoint write-only.\n");
        sb.append("- Drift risk: stage orchestration lives in Demo (documented).\n\n");
        sb.append("## 6. Lessons Learned\n\n");
        sb.append("- Single-submit Runtime; multi-stage Demo.\n");
        sb.append("- RuntimeResult needed checkpoint/duration/worker/goal for delivery reporting.\n");
        sb.append("- Real edit + mvn verify required Worker extensions.\n");
        sb.append("- Budget timeout must reach ShellWorker.\n\n");
        sb.append("## 7. Next Capability\n\n");
        sb.append("- Fixed serial Stage Runner inside Runtime Engine (not Workflow DSL).\n");
        return sb.toString();
    }

    private static boolean allSucceeded(List<DeliveryStageResult> stages) {
        if (stages.size() < 6) {
            return false;
        }
        for (DeliveryStageResult stage : stages) {
            if (!stage.getResult().isSuccess()) {
                return false;
            }
        }
        return true;
    }
}
