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
        if ("Specification".equalsIgnoreCase(role) || "Spec".equalsIgnoreCase(role)) {
            roleExtra = ""
                    + "Specification 产出要求：\n"
                    + "- 只处理 P1 的原始需求、附件清单和仓库事实；不得改业务源码、不得进入 Planning/Development。\n"
                    + "- 在客户仓写入：.story/" + request.storyId()
                    + "/specification/specification.result.properties，内容为：\n"
                    + "  decision=CANDIDATE|CLARIFICATION_REQUIRED\n"
                    + "  summary=<one line>\n"
                    + "- 若信息足以形成可供人确认的规格：decision=CANDIDATE，并写 "
                    + ".story/" + request.storyId() + "/specification/candidate-requirement.md。"
                    + "它必须有非空 ## raw、## goal、## in_scope、## out_of_scope、## acceptance；"
                    + "若 P1 有附件，必须有 ## attachments 且逐项列出附件文件名。\n"
                    + "- 若业务选择、展示语义、权限、数据来源或验收不可判定：decision=CLARIFICATION_REQUIRED，并写 "
                    + ".story/" + request.storyId() + "/specification/clarification.questions.md。"
                    + "每题使用 ## Q<n>，包含问题、2-4 个可选项、推荐项、依据和不回答的影响。\n"
                    + "- 不能因为仓库里已有相似实现就替客户做业务选择；不能假称已理解无法读取的图片。\n";
        } else if ("Analysis".equalsIgnoreCase(role)) {
            roleExtra = ""
                    + "Analysis 产出要求：\n"
                    + "- 只写事实摸底，不要改业务源码，不要给改码建议。\n"
                    + "- 在客户仓写入：.story/" + request.storyId()
                    + "/analysis/discovery.report.md\n"
                    + "- 先读取 P1 中 repository-facts/module-map/baseline（若存在）；只把有来源的观察写入 discovery。"
                    + "  P1 的 Unknown 仍是 Unknown，不得将它补成猜测。\n"
                    + "- 若 P1 有 attachments-index.md：逐项检查附件；能实际读取的附件要在 discovery 引用文件名和它影响的结论。"
                    + "  无法读取或无法可靠解释图片/原型时写 BLOCKED 问题，要求可判读的文字说明；禁止假称已理解。\n"
                    + "- 必须同时写入：.story/" + request.storyId()
                    + "/analysis/gap.report.properties\n"
                    + "  内容键：gap_status=CLEAR|ASSUMABLE|BLOCKED；blocking_gap_count=整数；assumable_gap_count=整数；"
                    + "summary=一行摘要。\n"
                    + "  CLEAR 时两个 count 都为 0；ASSUMABLE 时 blocking=0 且 assumable>0；BLOCKED 时 blocking>0。\n"
                    + "  BLOCKED 时 assumable_gap_count 必须为 0；不要同时把同一问题标为 BLOCKED 和 ASSUMABLE。\n"
                    + "  实现策略、测试写法、局部重构选择不是 Gap；Requirement、Allowed files、AC、验证命令齐全时写 CLEAR。\n"
                    + "  若 P1 含 slices/verification-entry.yaml，其中 build/test 命令是已确认的环境事实；"
                    + "不得仅因 Requirement 未重复该命令而报告 ASSUMABLE。\n"
                    + "  ASSUMABLE 仅用于真实的需求、环境、兼容性或数据假设（并在 gap.report.md 写清假设）；硬阻塞用 BLOCKED。\n"
                    + "  若 BLOCKED，必须同时写 .story/" + request.storyId()
                    + "/analysis/clarification.questions.md；每题用 ## Q<n>，给 2-4 个选项、推荐项、依据和不回答的影响。\n"
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
                    + "- 必须同时包含非空 ## Change Map 与 ## Test Strategy；Change Map 列出每个拟改文件及目的，"
                    + "Test Strategy 将每条 Acceptance 映射到验证方式/命令，不能用‘运行全量测试’代替。\n"
                    + "- 必须包含 ## Impact Assessment，逐行声明 api/data/authorization/ui/observability: PRESENT|NOT_APPLICABLE。"
                    + "  api=PRESENT 时另写 .story/" + request.storyId() + "/planning/api-contract.md；"
                    + "data=PRESENT 时另写 .story/" + request.storyId() + "/planning/data-change.md（含兼容、迁移与回滚）。\n"
                    + "- 必须在 .story/" + request.storyId() + "/planning/probe-candidate/ 写每条 AC 的候选验收探针和 probes.properties。"
                    + "  manifest 的 ac.count 必须等于 AC 数；ac.N.path 必须写未来冻结路径 .ai4se/acceptance-probes/"
                    + request.storyId() + "/<file>；ac.N.command 必须调用该未来路径；ac.N.sha256 是候选文件 SHA-256。"
                    + "  这是候选，只有人执行 freeze-probes 后才成为冻结探针；不要改 .ai4se/。\n"
                    + "  探针必须证明所选测试实际执行：禁止使用 -DfailIfNoTests=false 或 "
                    + "-Dsurefire.failIfNoSpecifiedTests=false 来把缺失测试伪装为成功；Maven 精确选测时显式要求测试存在。\n"
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
