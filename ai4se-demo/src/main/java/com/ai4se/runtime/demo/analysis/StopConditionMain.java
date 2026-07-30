package com.ai4se.runtime.demo.analysis;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 建造手册 §2.3 — Stop Condition 证据（Analysis 层）。
 * mvn -pl ai4se-demo exec:java -Ddemo.mainClass=com.ai4se.runtime.demo.analysis.StopConditionMain
 */
public final class StopConditionMain {

    private StopConditionMain() {
    }

    public static void main(String[] args) throws Exception {
        File module = com.ai4se.runtime.demo.delivery.WorkspaceBootstrap.resolveDemoModuleRoot();
        Path repoRoot = module.toPath().getParent().normalize();
        Path docs = repoRoot.resolve("docs");
        Path outRoot = new File(module, "target/stop-condition").toPath();
        Files.createDirectories(outRoot);
        Path reportFile = outRoot.resolve("evidence-delta.md");

        StringBuilder evidence = new StringBuilder();
        evidence.append("# 证据增量 — 停止条件（§2.3）\n\n");
        evidence.append("手册: docs/build-pathway-playbook.md §2.3 / §2.1.1 固定手段\n");
        evidence.append("本关问题: 澄清停止规则（连续两轮 BLOCKED / 超过 3 轮 / 拒绝回答）是否生效，且不进入 Planning？\n");
        evidence.append("角色: 仅 AGENT 申报 — 须 Reviewer 关闭。\n\n");

        int fails = 0;
        File workspace = new File(module, "pilot-workspace-promotion");

        evidence.append("## 1. 本关产出（§2.3 停止）\n\n");

        Path r1 = outRoot.resolve("round1-unknown");
        RequirementAnalysisPipeline.Result unknown = RequirementAnalysisPipeline.run(
                workspace.toPath(),
                RequirementAnalysisPipeline.PROMOTION_REQUIREMENT,
                r1,
                RequirementAnalysisPipeline.promotionUnknownAnswers(),
                true,
                "stop-r1");
        boolean r1Blocked = unknown.gap.getStatus() == GapReport.Status.BLOCKED && !unknown.planWritten;
        StopCondition.Result after1 = StopCondition.evaluate(1, r1Blocked, false, false);
        boolean r1Continue = !after1.isStop() && after1.decision == StopCondition.Decision.CONTINUE;

        Path r2 = outRoot.resolve("round2-fuzzy");
        RequirementAnalysisPipeline.Result fuzzy = RequirementAnalysisPipeline.run(
                workspace.toPath(),
                RequirementAnalysisPipeline.PROMOTION_REQUIREMENT,
                r2,
                RequirementAnalysisPipeline.promotionFuzzyAnswers(),
                true,
                "stop-r2");
        boolean r2Blocked = fuzzy.gap.getStatus() == GapReport.Status.BLOCKED && !fuzzy.planWritten;
        StopCondition.Result after2 = StopCondition.evaluate(2, r2Blocked, r1Blocked, false);
        Files.write(r2.resolve("stop-decision.md"),
                StopCondition.toMarkdown(after2, 2).getBytes(Charset.forName("UTF-8")));
        boolean twoRoundStop = after2.isStop()
                && after2.reason == StopCondition.Reason.TWO_CONSECUTIVE_BLOCKED
                && !fuzzy.planWritten;
        String stopBody = new String(Files.readAllBytes(r2.resolve("stop-decision.md")), Charset.forName("UTF-8"));
        boolean stopArtifact = stopBody.contains("停止 — 需要人工决策")
                && stopBody.contains("连续两轮仍阻塞")
                && stopBody.contains("TWO_CONSECUTIVE_BLOCKED");

        evidence.append("### 1a 连续两轮 BLOCKED ⇒ 停止（无 Plan）\n\n");
        evidence.append("- 第 1 轮 BLOCKED 且继续: ").append(r1Blocked && r1Continue).append('\n');
        evidence.append("- 第 2 轮 BLOCKED 且写出停止裁决: ").append(twoRoundStop && stopArtifact).append('\n');
        evidence.append("- 状态: **")
                .append(r1Blocked && r1Continue && twoRoundStop && stopArtifact ? "通过" : "失败")
                .append("**\n\n");
        if (!(r1Blocked && r1Continue && twoRoundStop && stopArtifact)) {
            fails++;
        }

        StopCondition.Result after4 = StopCondition.evaluate(4, true, true, false);
        boolean threePlus = after4.isStop() && after4.reason == StopCondition.Reason.EXCEEDED_THREE_ROUNDS;
        Path r4 = outRoot.resolve("round4-exceed");
        Files.createDirectories(r4);
        Files.write(r4.resolve("stop-decision.md"),
                StopCondition.toMarkdown(after4, 4).getBytes(Charset.forName("UTF-8")));
        evidence.append("### 1b 澄清超过 3 轮仍 BLOCKED ⇒ 停止\n\n");
        evidence.append("- 结果: ").append(threePlus).append('\n');
        evidence.append("- 状态: **").append(threePlus ? "通过" : "失败").append("**\n\n");
        if (!threePlus) {
            fails++;
        }

        Map<String, String> refuse = RequirementAnalysisPipeline.promotionUnknownAnswers();
        refuse.put("promotion_table", "拒绝回答");
        boolean refuseDetected = StopCondition.isRefuseAnswer(refuse.get("promotion_table"));
        StopCondition.Result refuseStop = StopCondition.evaluate(1, true, false, refuseDetected);
        Path refuseOut = outRoot.resolve("refuse");
        RequirementAnalysisPipeline.Result refuseRun = RequirementAnalysisPipeline.run(
                workspace.toPath(),
                RequirementAnalysisPipeline.PROMOTION_REQUIREMENT,
                refuseOut,
                refuse,
                true,
                "stop-refuse");
        Files.write(refuseOut.resolve("stop-decision.md"),
                StopCondition.toMarkdown(refuseStop, 1).getBytes(Charset.forName("UTF-8")));
        boolean refuseOk = refuseStop.isStop()
                && refuseStop.reason == StopCondition.Reason.USER_REFUSED
                && !refuseRun.planWritten
                && refuseRun.gap.getStatus() == GapReport.Status.BLOCKED;
        evidence.append("### 1c 用户拒绝回答 ⇒ 停止（无 Plan / 不偷渡 ASSUMABLE）\n\n");
        evidence.append("- 结果: ").append(refuseOk).append('\n');
        evidence.append("- 状态: **").append(refuseOk ? "通过" : "失败").append("**\n\n");
        if (!refuseOk) {
            fails++;
        }

        boolean negOk = !after1.isStop();
        evidence.append("### 1d 负例: 仅第 1 轮 BLOCKED 不得过早停止\n\n");
        evidence.append("- 结果: ").append(negOk).append('\n');
        evidence.append("- 状态: **").append(negOk ? "通过" : "失败").append("**\n\n");
        if (!negOk) {
            fails++;
        }

        evidence.append("## 2. 已关闭关复跑\n\n");
        runMvn(repoRoot, Arrays.asList("mvn", "-pl", "ai4se-demo", "-am", "-q", "compile"));
        int prior = runMvn(repoRoot, Arrays.asList(
                "mvn", "-pl", "ai4se-demo", "-q", "exec:java",
                "-Ddemo.mainClass=com.ai4se.runtime.demo.delivery.LoopReExecutionMain"));
        boolean priorOk = prior == 0;
        if (!priorOk) {
            fails++;
        }
        evidence.append("- LoopReExecutionMain 退出码: ").append(prior).append('\n');
        evidence.append("- 状态: **").append(priorOk ? "通过" : "失败").append("**\n\n");

        evidence.append("## 3. 蓝图对齐\n\n");
        String handbook = new String(Files.readAllBytes(docs.resolve("build-pathway-playbook.md")),
                Charset.forName("UTF-8"));
        boolean waterOk = handbook.contains("§2.3") && handbook.contains("S8b/c") && handbook.contains("❌");
        if (!waterOk) {
            fails++;
        }
        evidence.append("- 裁决: **ALIGN（对齐）**\n");
        evidence.append("- 事实: §2.3 原为手册条文；Phase 1 只证单轮能停。")
                .append("本关将「连续两轮 BLOCKED / 超过 3 轮 / 拒绝」落成 Demo 的 stop-decision.md。")
                .append("Analysis Stop ≠ 取代 Engine S4；S8b/c 仍锁定。\n");
        evidence.append("- 状态: **").append(waterOk ? "通过" : "失败").append("**\n\n");
        evidence.append("## 4. 防自拍关闭\n\n");
        evidence.append("- `AGENT_DECLARE`（代理申报）: ").append(fails == 0 ? "通过" : "失败").append('\n');
        evidence.append("- `REVIEWER_CLOSE`（审阅关闭）: **待定**\n\n");

        evidence.append("## 证据增量（仅本关新项）\n\n");
        evidence.append("| 新证据 | 结果 |\n|--------|------|\n");
        evidence.append("| 连续两轮 BLOCKED ⇒ 停止 + stop-decision.md | ")
                .append(twoRoundStop && stopArtifact ? "通过" : "失败").append(" |\n");
        evidence.append("| 超过 3 轮仍 BLOCKED ⇒ 停止 | ")
                .append(threePlus ? "通过" : "失败").append(" |\n");
        evidence.append("| 拒绝回答 ⇒ 停止且无 Plan | ")
                .append(refuseOk ? "通过" : "失败").append(" |\n");
        evidence.append("| 仅第 1 轮不得停止 | ")
                .append(negOk ? "通过" : "失败").append(" |\n");
        evidence.append("| 上一关 LoopReExecution 仍绿 | ")
                .append(priorOk ? "通过" : "失败").append(" |\n");
        evidence.append("\n未宣称: Runtime S4 人工等待、自动升级 UI、Claude。\n");
        evidence.append("不重评: Phase 1 单轮 UNKNOWN→BLOCKED。\n\n");

        evidence.append("## 结论\n\n");
        if (fails == 0) {
            evidence.append("**代理申报: 通过** — §2.3 停止条件已在 Analysis 层取证。")
                    .append("**等待 Reviewer 关闭。**\n");
        } else {
            evidence.append("**代理申报: 失败** — ").append(fails).append(" 项检查未通过。\n");
        }

        Files.write(reportFile, evidence.toString().getBytes(Charset.forName("UTF-8")));
        Files.write(docs.resolve("stop-condition-evidence.md"),
                evidence.toString().getBytes(Charset.forName("UTF-8")));
        System.out.println(evidence.toString());
        System.out.println("报告 = " + reportFile.toAbsolutePath());
        System.out.println("抽检产物 = " + r2.resolve("stop-decision.md").toAbsolutePath());
        if (fails != 0) {
            System.exit(1);
        }
        System.out.println("停止条件 代理申报通过（Reviewer 仍待关闭）");
    }

    private static int runMvn(Path repoRoot, List<String> cmd) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(repoRoot.toFile());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        StringBuilder sink = new StringBuilder();
        BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), Charset.forName("UTF-8")));
        try {
            String line;
            while ((line = r.readLine()) != null) {
                sink.append(line).append('\n');
            }
        } finally {
            r.close();
        }
        int code = p.waitFor();
        if (code != 0) {
            System.err.println("--- 失败 " + cmd + " exit=" + code + " ---");
            System.err.println(sink.length() > 3000 ? sink.substring(sink.length() - 3000) : sink.toString());
        }
        return code;
    }
}
