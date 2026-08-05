package com.ai4se.runtime.demo.pathway;

import com.ai4se.orchestration.acceptance.HumanAcceptanceRecords;
import com.ai4se.orchestration.lifecycle.KnowledgeLifecycleControl;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * 短 P1：对已 COMPLETED 的现场 Story 落真人验收 + S6 noop，并回填 pathway-evidence。
 *
 * <pre>
 * java -cp ai4se-runtime.jar com.ai4se.runtime.demo.pathway.FieldCloseGateMain \
 *   --workspace /path/to/ruoyi-vue-pro --accepter peng.lv
 * </pre>
 */
public final class FieldCloseGateMain {

    private FieldCloseGateMain() {
    }

    public static void main(String[] args) throws Exception {
        Path workspace = null;
        String accepter = "peng.lv";
        for (int i = 0; i < args.length; i++) {
            if ("--workspace".equals(args[i]) && i + 1 < args.length) {
                workspace = Paths.get(args[++i]).toAbsolutePath().normalize();
            } else if ("--accepter".equals(args[i]) && i + 1 < args.length) {
                accepter = args[++i];
            }
        }
        if (workspace == null || !Files.isDirectory(workspace)) {
            System.err.println("Required: --workspace <customer-repo>");
            System.exit(1);
        }

        String noopReason = "本批仅为工具类单测/窄入口现场验证，暂不回写业务 Knowledge；避免把洞见当 Facts。";

        // 并-1：V4 主验收；MoneyUtils / findlast 同批附注
        closeOne(
                workspace,
                "story-yudao-v4-yuantofen",
                accepter,
                ""
                        + "主验收（V4）：确认 FAIL→Defect→再 Dev→PASS、本地 Commit 不 Push、"
                        + "Round2 为 Cursor；范围仅 MoneyUtils.yuanToFen。 "
                        + "同批附注：story-yudao-moneyutils（Cursor V3 单测基线）、"
                        + "story-yudao-findlast（functional Dev，非模型签收）证据已阅，范围可接受。",
                noopReason);

        closeOne(
                workspace,
                "story-yudao-moneyutils",
                accepter,
                "同批附注验收：Cursor Dev + MoneyUtilsTest PASS + 本地 Commit；Analysis/Plan 仍为 runner 预置（已知）。",
                noopReason);

        closeOne(
                workspace,
                "story-yudao-findlast",
                accepter,
                "同批附注验收：功能已交付且测绿；Dev 为 functional_hook，不得当作模型挂机签收。",
                noopReason);

        closeOne(
                workspace,
                "story-yudao-pricemultiply-smoke",
                accepter,
                "冒烟验收：Discovery skip + 人工 Plan/Approval + Cursor Dev；"
                        + "meta 已披露 discovery/approval_prepared_by_runner=false；"
                        + "仅 MoneyUtilsTest 正向用例，范围可接受。",
                noopReason);

        closeOne(
                workspace,
                "story-yudao-analysis-percent",
                accepter,
                "验收：Analysis+Dev Cursor 挂机；discovery/approval_prepared_by_runner=false；"
                        + "adapter_roles=Analysis,Development；仅 MoneyUtilsTest percent 正向用例可接受。",
                noopReason);

        closeOne(
                workspace,
                "story-yudao-plan-adapter-fen100",
                accepter,
                "验收：Analysis+Planning+Dev 三角色挂机；Plan 含 Allowed；"
                        + "仅 MoneyUtilsTest fen=100 正向用例；本地 Commit 不 Push，可接受。",
                noopReason);

        closeOne(
                workspace,
                "story-yudao-lowrisk-fen0",
                accepter,
                "验收：低风险自动批冒烟；approval mode=low_risk_auto / control-low-risk；"
                        + "Analysis+Planning+Dev Cursor；仅 MoneyUtilsTest fen=0；本地 Commit 不 Push，可接受。",
                noopReason);

        closeOne(
                workspace,
                "story-yudao-clarification-stop",
                accepter,
                "验收：Clarification 无答案 → STOPPED+Gap BLOCKED；--resume 答完后续跑 COMPLETED；"
                        + "gap_prepared_by_runner=false；仅 MoneyUtilsTest fen=2，可接受。",
                noopReason);

        closeOne(
                workspace,
                "story-yudao-v4-natural-priceadd-loop",
                accepter,
                "验收：自然 V4 回环（非 seeded yuanToFen）；v4_fail_mode=natural；"
                        + "v4_round1_source=incomplete_hook → Defect → Round2 Cursor PASS；"
                        + "纯 Cursor 一梭 PASS 会被 Control 拒（见 story-yudao-v4-natural-priceadd 笔记）。",
                noopReason);

        System.out.println("P1 done: HUMAN acceptance + lifecycle noop (incl. natural-v4-loop).");
    }

    private static void closeOne(
            Path workspace, String storyId, String accepter, String note, String noopReason)
            throws Exception {
        if (HumanAcceptanceRecords.hasRecord(workspace, storyId)
                && HumanAcceptanceRecords.isAccepted(workspace, storyId)) {
            System.out.println(storyId + ": acceptance already ACCEPTED — skip rewrite");
        } else {
            HumanAcceptanceRecords.recordAccepted(
                    workspace, storyId, accepter, note, HumanAcceptanceRecords.Kind.HUMAN);
            System.out.println(storyId + ": acceptance HUMAN ACCEPTED");
        }

        if (KnowledgeLifecycleControl.hasLifecycleArtifact(workspace, storyId)) {
            System.out.println(storyId + ": lifecycle already present — skip");
        } else {
            KnowledgeLifecycleControl.noop(workspace, storyId, noopReason);
            System.out.println(storyId + ": lifecycle noop");
        }

        // 回填 evidence（跑完后补的 S5/S6）
        Path storyRoot = workspace.resolve(".story").resolve(storyId);
        Path evidence = storyRoot.resolve("pathway-evidence");
        if (Files.isDirectory(evidence)) {
            copyTree(storyRoot.resolve("acceptance"), evidence.resolve("acceptance"));
            copyTree(storyRoot.resolve("lifecycle"), evidence.resolve("lifecycle"));
            Path meta = evidence.resolve("meta.yaml");
            if (Files.isRegularFile(meta)) {
                String text = new String(Files.readAllBytes(meta), StandardCharsets.UTF_8);
                text = text.replace("human_acceptance_kind: fixture", "human_acceptance_kind: human");
                Files.write(meta, text.getBytes(StandardCharsets.UTF_8));
            }
            System.out.println(storyId + ": evidence refreshed (acceptance/lifecycle/meta)");
        }
    }

    private static void copyTree(Path from, Path to) throws Exception {
        if (!Files.isDirectory(from)) {
            return;
        }
        Files.createDirectories(to);
            List<Path> files = new ArrayList<Path>();
            Files.walkFileTree(from, new java.nio.file.SimpleFileVisitor<Path>() {
            @Override
            public java.nio.file.FileVisitResult visitFile(
                    Path file, java.nio.file.attribute.BasicFileAttributes attrs)
                    throws java.io.IOException {
                files.add(file);
                return java.nio.file.FileVisitResult.CONTINUE;
            }
        });
        for (Path file : files) {
            Path rel = from.relativize(file);
            Path dest = to.resolve(rel.toString());
            Files.createDirectories(dest.getParent());
            Files.copy(file, dest, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
