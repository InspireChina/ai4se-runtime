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
                    + "- 若包内有 allowed-hint，Allowed 应与之对齐（可收紧，勿越权扩大）。\n"
                    + "- 文档用中文结构；路径保持原样。\n";
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
        return "development".equals(r) || "dev".equals(r)
                || "analysis".equals(r) || "planning".equals(r);
    }
}
