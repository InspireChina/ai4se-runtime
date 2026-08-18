package com.ai4se.execution.support;

import com.ai4se.execution.api.AdapterRequest;
import com.ai4se.context.packagebuild.ModelInputEnvelope;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Shared Context Package → prompt text for all Model CLI Adapters.
 * Appendix A: swapping Adapter must not change the Package contract / prompt semantics.
 */
public final class ContextPackagePrompt {

    private ContextPackagePrompt() {
    }

    public static String build(AdapterRequest request, Path manifest) throws IOException {
        String manifestText = new String(Files.readAllBytes(manifest), StandardCharsets.UTF_8);
        Path modelInput = request.packageDir().resolve(ModelInputEnvelope.FILE);
        String modelInputText = Files.isRegularFile(modelInput)
                ? new String(Files.readAllBytes(modelInput), StandardCharsets.UTF_8)
                : null;
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
                    + "- 必须同时写入：.story/" + request.storyId()
                    + "/analysis/gap.report.properties\n"
                    + "  内容键：gap_status=CLEAR|ASSUMABLE|BLOCKED；blocking_gap_count=整数；assumable_gap_count=整数；"
                    + "summary=一行摘要。\n"
                    + "  CLEAR 时两个 count 都为 0；ASSUMABLE 时 blocking=0 且 assumable>0；BLOCKED 时 blocking>0。\n"
                    + "  实现策略、测试写法、局部重构选择不是 Gap；Requirement、Allowed files、AC、验证命令齐全时写 CLEAR。\n"
                    + "  ASSUMABLE 仅用于真实的需求、环境、兼容性或数据假设（并在 gap.report.md 写清假设）；硬阻塞用 BLOCKED。\n"
                    + "- 建议另写 gap.report.md（五区结构）供人审；机器门闸读 properties。\n"
                    + "- 落盘优先用 Write/Edit 工具写上述路径；需要建目录可用 mkdir。"
                    + " 不要等待人工批准、不要改 Allowed 之外的业务源码。\n"
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
                    + "- 落盘优先用 Write/Edit 写 plan.md；不要等待人工批准。\n"
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
                    + "- 机器 sidecar（必须）：写入 .story/" + request.storyId()
                    + "/review/review-result.properties，内容为：\n"
                    + "  decision=PASS|CONDITIONAL|REJECT\n"
                    + "  residual_risk=<one line>\n"
                    + "  （不要写 review_source；由 Control 强制写入 adapter）\n"
                    + "- 仅 PASS 可自动进入 Delivery；CONDITIONAL=需人工/补充验证；REJECT=禁止 Delivery。\n"
                    + "- Markdown 详情（可选）：.story/" + request.storyId()
                    + "/review/review-result.md，可含按 AC 条目的通过/不通过/证据。\n"
                    + "- 落盘优先用 Write/Edit；不要等待人工批准、不要改业务源码。\n"
                    + "- 不要自称已替代 Verification。\n";
        }
        return ""
                + "You are executing role=" + request.role()
                + " for story=" + request.storyId() + ".\n"
                + "Use ONLY the Context Package below. Do not roam the whole repository as primary input.\n"
                + "Do NOT decide workflow stages, retries, or skip Verification — Control owns that.\n"
                + roleExtra
                + "Package dir: " + request.packageDir().toAbsolutePath() + "\n"
                + "Manifest audit path: " + manifest.toAbsolutePath() + "\n"
                + acceptanceNote
                + "\n--- " + (modelInputText == null ? "manifest.md" : ModelInputEnvelope.FILE) + " ---\n"
                + (modelInputText == null ? manifestText : modelInputText)
                + "\n--- end ---\n";
    }

    /**
     * Business-source write roles (Development). Prefer {@link UnattendedWriteScope#forRole}.
     */
    public static boolean isWriteRole(String role) {
        return UnattendedWriteScope.forRole(role) == UnattendedWriteScope.BUSINESS_SOURCE;
    }

    /**
     * Roles that Contract-write under {@code .story/}. Prefer {@link UnattendedWriteScope#forRole}.
     */
    public static boolean needsStoryArtifactWrite(String role) {
        return UnattendedWriteScope.forRole(role) == UnattendedWriteScope.STORY_ARTIFACT;
    }
}
