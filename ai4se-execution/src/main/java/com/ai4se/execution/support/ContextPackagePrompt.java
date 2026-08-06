package com.ai4se.execution.support;

import com.ai4se.execution.api.AdapterRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Shared Context Package → prompt text for all Model CLI Adapters.
 * Appendix A: swapping Adapter must not change the Package contract / prompt semantics.
 */
public final class ContextPackagePrompt {

    private ContextPackagePrompt() {
    }

    public static String build(AdapterRequest request, Path manifest) throws IOException {
        String manifestText = new String(Files.readAllBytes(manifest), StandardCharsets.UTF_8);
        Path acceptance = request.packageDir().resolve("slices/acceptance.md");
        String acceptanceNote = Files.isRegularFile(acceptance)
                ? "Acceptance slice: " + acceptance.toAbsolutePath() + "\n"
                : "";
        String roleExtra = "";
        String role = request.role() == null ? "" : request.role().trim();
        if ("Analysis".equalsIgnoreCase(role)) {
            roleExtra = ""
                    + "Analysis 产出要求：\n"
                    + "- 只写事实摸底，不要改业务源码，不要给改码建议。\n"
                    + "- 在客户仓写入：.story/" + request.storyId()
                    + "/analysis/discovery.report.md\n"
                    + "- 文档用中文结构；API/路径/命令可保留英文标识。\n"
                    + "- 标题可用「摸底报告（Discovery）」；正文只含已观察事实。\n";
        } else if ("Planning".equalsIgnoreCase(role)) {
            roleExtra = ""
                    + "Planning 产出要求：\n"
                    + "- 不要改业务源码；不要自评测试已通过。\n"
                    + "- 在客户仓写入：.story/" + request.storyId()
                    + "/planning/plan.md\n"
                    + "- 必须包含 ## Design 与 ## Allowed Files（至少一条相对路径）。\n"
                    + "- Allowed Files 每行必须是裸相对路径：禁止 markdown 反引号、引号、尾注/(new)/注释。\n"
                    + "- 若包内有 allowed-hint，Allowed 应与之对齐（可收紧，勿越权扩大）。\n"
                    + "- 文档用中文结构；路径保持原样。\n";
        } else if ("Development".equalsIgnoreCase(role) || "Dev".equalsIgnoreCase(role)) {
            roleExtra = ""
                    + "Development 产出要求：\n"
                    + "- 只改 Allowed Files 声明的路径；禁止越权。\n"
                    + "- 遵守 slices/acceptance.md 每条验收标准。\n"
                    + "- 若存在 slices/gap-ref.md，须遵循其中假设，不得静默违背。\n"
                    + "- 禁止自评「测试已通过 / 可以交付」。\n"
                    + "- 完成后由 Verification 调用客户测试入口判定。\n";
        } else if ("Review".equalsIgnoreCase(role)) {
            roleExtra = ""
                    + "Review 产出要求：\n"
                    + "- 不要重跑全量测试冒充 Verification。\n"
                    + "- 对照 Acceptance 与 Verify 结论给出结构化判断。\n"
                    + "- 在客户仓写入：.story/" + request.storyId()
                    + "/review/review-result.md\n"
                    + "- 文件须含 decision（通过/附条件/驳回）、residual_risk、"
                    + "以及按 AC 条目的通过/不通过/证据。\n"
                    + "- review_source 由 Control 记为 adapter；不要自称已替代 Verification。\n";
        }
        return ""
                + "You are executing role=" + request.role()
                + " for story=" + request.storyId() + ".\n"
                + "Use ONLY the Context Package below. Do not roam the whole repository as primary input.\n"
                + "Do NOT decide workflow stages, retries, or skip Verification — Control owns that.\n"
                + roleExtra
                + "Package dir: " + request.packageDir().toAbsolutePath() + "\n"
                + acceptanceNote
                + "\n--- manifest.md ---\n"
                + manifestText
                + "\n--- end ---\n";
    }

    public static boolean isWriteRole(String role) {
        if (role == null) {
            return false;
        }
        String r = role.trim().toLowerCase(Locale.ROOT);
        // Only Development mutates business sources — Analysis/Planning must not get
        // --dangerously-skip-permissions / force-write.
        return "development".equals(r) || "dev".equals(r);
    }
}
