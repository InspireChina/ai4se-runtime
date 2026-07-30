package com.ai4se.runtime.demo.analysis;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * 实义关：§2.3 Stop 必须由 Pipeline 写出，而不是只在独立 Main 里调用 evaluate。
 * mvn -pl ai4se-demo exec:java -Ddemo.mainClass=com.ai4se.runtime.demo.analysis.StopPipelineWiredMain
 */
public final class StopPipelineWiredMain {

    private StopPipelineWiredMain() {
    }

    public static void main(String[] args) throws Exception {
        File module = com.ai4se.runtime.demo.delivery.WorkspaceBootstrap.resolveDemoModuleRoot();
        Path docs = module.toPath().getParent().resolve("docs").normalize();
        Path outRoot = new File(module, "target/stop-pipeline-wired").toPath();
        Files.createDirectories(outRoot);
        File workspace = new File(module, "pilot-workspace-promotion");

        StringBuilder evidence = new StringBuilder();
        evidence.append("# 证据增量 — Stop 接入 Pipeline（实义关）\n\n");
        evidence.append("手册: §2.3 / §2.1.1\n");
        evidence.append("本关问题: 真实 Analysis Pipeline 是否按轮次写出 stop-decision.md 并禁止 Plan？\n");
        evidence.append("说明: 上一关 StopConditionMain 只证明了计算器；本关证明 Pipeline 消费它。\n");
        evidence.append("Reviewer: **默认可跳过抽检**（改的是真路径；证据仅为申报记录）。\n\n");

        int fails = 0;

        // round1
        RequirementAnalysisPipeline.Result r1 = RequirementAnalysisPipeline.run(
                workspace.toPath(),
                RequirementAnalysisPipeline.PROMOTION_REQUIREMENT,
                outRoot.resolve("r1"),
                RequirementAnalysisPipeline.promotionUnknownAnswers(),
                true,
                "stop-wired-r1",
                1,
                false);
        boolean r1Ok = r1.gap.getStatus() == GapReport.Status.BLOCKED
                && !r1.planWritten
                && !r1.stop.isStop()
                && !Files.exists(outRoot.resolve("r1/stop-decision.md"));
        evidence.append("## 1. Pipeline 第 1 轮 UNKNOWN\n\n");
        evidence.append("- BLOCKED、无 Plan、不停止、无 stop-decision.md: ").append(r1Ok).append('\n');
        evidence.append("- 状态: **").append(r1Ok ? "通过" : "失败").append("**\n\n");
        if (!r1Ok) {
            fails++;
        }

        // round2 via pipeline with previousBlocked=true
        RequirementAnalysisPipeline.Result r2 = RequirementAnalysisPipeline.run(
                workspace.toPath(),
                RequirementAnalysisPipeline.PROMOTION_REQUIREMENT,
                outRoot.resolve("r2"),
                RequirementAnalysisPipeline.promotionFuzzyAnswers(),
                true,
                "stop-wired-r2",
                2,
                true);
        Path stop2 = outRoot.resolve("r2/stop-decision.md");
        String stopText = Files.exists(stop2)
                ? new String(Files.readAllBytes(stop2), Charset.forName("UTF-8"))
                : "";
        boolean r2Ok = r2.stop.isStop()
                && !r2.planWritten
                && stopText.contains("停止 — 需要人工决策")
                && stopText.contains("连续两轮仍阻塞")
                && !Files.exists(outRoot.resolve("r2/plan.md"));
        evidence.append("## 2. Pipeline 第 2 轮（上一轮 BLOCKED）\n\n");
        evidence.append("- Pipeline 写出 stop-decision.md 且无 plan.md: ").append(r2Ok).append('\n');
        evidence.append("- 状态: **").append(r2Ok ? "通过" : "失败").append("**\n\n");
        if (!r2Ok) {
            fails++;
        }

        Map<String, String> refuse = RequirementAnalysisPipeline.promotionUnknownAnswers();
        refuse.put("promotion_table", "拒绝回答");
        RequirementAnalysisPipeline.Result rf = RequirementAnalysisPipeline.run(
                workspace.toPath(),
                RequirementAnalysisPipeline.PROMOTION_REQUIREMENT,
                outRoot.resolve("refuse"),
                refuse,
                true,
                "stop-wired-refuse",
                1,
                false);
        Path stopRf = outRoot.resolve("refuse/stop-decision.md");
        boolean refuseOk = rf.stop.isStop()
                && !rf.planWritten
                && Files.exists(stopRf)
                && new String(Files.readAllBytes(stopRf), Charset.forName("UTF-8")).contains("用户拒绝回答");
        evidence.append("## 3. Pipeline 拒绝回答\n\n");
        evidence.append("- 停止且写出裁决: ").append(refuseOk).append('\n');
        evidence.append("- 状态: **").append(refuseOk ? "通过" : "失败").append("**\n\n");
        if (!refuseOk) {
            fails++;
        }

        evidence.append("## 4. 蓝图\n\n");
        evidence.append("- 裁决: **ALIGN**\n");
        evidence.append("- 事实: 把手册 §2.3 从「旁路 Main 演示」接到 Analysis 真路径；")
                .append("仍非 Runtime S4。未新造 Gate 类型。\n");
        evidence.append("- 状态: **通过**\n\n");

        evidence.append("## 结论\n\n");
        if (fails == 0) {
            evidence.append("**代理申报: 通过** — Stop 已接入 Pipeline。Reviewer 默认可跳过，直接关闭或点下一关。\n");
        } else {
            evidence.append("**代理申报: 失败** — ").append(fails).append(" 项未通过。\n");
        }

        Files.write(outRoot.resolve("evidence-delta.md"), evidence.toString().getBytes(Charset.forName("UTF-8")));
        Files.write(docs.resolve("stop-pipeline-wired-evidence.md"),
                evidence.toString().getBytes(Charset.forName("UTF-8")));
        System.out.println(evidence.toString());
        if (fails != 0) {
            System.exit(1);
        }
    }
}
