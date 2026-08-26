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
        if ("Discovery".equalsIgnoreCase(role)) {
            roleExtra = ""
                    + "Repository Discovery 产出要求：\n"
                    + "- 这是客户仓的首次语义建库，不是需求开发；P1 的确定性扫描事实是起点，不能把猜测写成事实。\n"
                    + "- 只允许写 P1 discovery-seed.properties 所列 candidate_root 下的 documents/ 与 candidate.yaml。"
                    + "  禁止修改业务源码、.story、.ai4se/knowledge、.ai4se/index 或任何已有文件。\n"
                    + "- 在 candidate_root/candidate.yaml 写：candidate_id、scope、source_commit，以及 documents 列表。"
                    + "  每个文档项必须有 id、path（documents/<id>.md）、kind、tags、refs、source_paths。\n"
                    + "- 每个 documents/<id>.md 必须有标题、## Evidence、## Unknowns。Evidence 用仓内真实相对路径/类/接口/表名支撑；"
                    + "  不确定、未覆盖或需要人工确认的内容只放 Unknowns，不能补造结论。\n"
                    + "- candidate.yaml 声明的每一条 source_paths，必须原样逐字出现于对应 documents/<id>.md 的 ## Evidence；"
                    + "  不能只引用同目录或同模块的其他文件。\n"
                    + "- 建议输出 3-6 份高价值文档：system-context、module-boundaries、delivery-conventions、"
                    + "data-and-integration（仅有证据时）、testing-and-operations（仅有证据时）。"
                    + "  文档是候选，必须经人工 approve-knowledge 后才进入后续 Story Context。\n"
                    + "- 仅为完成候选所需而读取 target scope、直接依赖、入口和邻近测试；不得全仓漫游后堆砌摘要。\n"
                    + "- 文档用中文结构；代码符号、路径、命令保持原样。\n";
        } else if ("Specification".equalsIgnoreCase(role) || "Spec".equalsIgnoreCase(role)) {
            roleExtra = ""
                    + "Specification 产出要求：\n"
                    + "- 只处理 P1 的原始需求、附件清单和仓库事实；不得改业务源码、不得进入 Planning/Development。\n"
                    + "- 先以原始需求中的业务名词为线索，做一次有边界的只读定向摸底：从 P1 的 module-map/facts 找入口，"
                    + "再读取该入口的直接 Controller/Service/实体或状态常量/UI/邻近测试。只读这些直接依赖，"
                    + "不得全仓漫游或把代码摘要当成需求。\n"
                    + "- 在客户仓写入：.story/" + request.storyId()
                    + "/specification/specification.result.properties，内容为：\n"
                    + "  decision=CANDIDATE|CLARIFICATION_REQUIRED\n"
                    + "  summary=<one line>\n"
                    + "- 若信息足以形成可供人确认的规格：decision=CANDIDATE，并写 "
                    + ".story/" + request.storyId() + "/specification/candidate-requirement.md。"
                    + "它必须有非空 ## raw、## goal、## in_scope、## out_of_scope、## acceptance；"
                    + "若 P1 有附件，必须有 ## attachments 且逐项列出附件文件名；"
                    + "若 P1 有 clarification.resolved.md，必须另有 ## decisions，逐项把已回答的 Q 编号和实际选择"
                    + "写进候选规格与验收语句，不得只把答案当背景。\n"
                    + "- 若业务选择、展示语义、权限、数据来源或验收不可判定：decision=CLARIFICATION_REQUIRED，并写 "
                    + ".story/" + request.storyId() + "/specification/clarification.questions.md。"
                    + "每题使用 ## Q<n>，包含问题、2-4 个可选项、推荐项、代码证据和不回答的影响。"
                    + "代码证据必须列出实际仓内相对路径及字段/状态/接口等观察，不得只写泛泛‘依据’。\n"
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
                    + "  若 P1 有 slices/specification-decisions.md：已答业务选择不得再次提问。确有新发现时，"
                    + "问题必须标注 classification: NEW_FACT|CONTRADICTION|MISSED_DISCOVERY，并给出实际代码证据；"
                    + "MISSED_DISCOVERY 是过程改进信号，不得伪装成正常的需求变更。\n"
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
                    + "- 若 P1 的 verification-entry.yaml 含 quality 段，它是仓库所有者显式登记的提交前质量门禁；"
                    + "必须在 Test Strategy 中说明其覆盖面，不得删改或以 build 成功替代。\n"
                    + "- 必须包含 ## Impact Assessment：可逐行声明 api/data/authorization/ui/observability: PRESENT|NOT_APPLICABLE，"
                    + "也可用两列 Markdown 表格（第一列 area、第二列精确为 PRESENT 或 NOT_APPLICABLE）。"
                    + "  api=PRESENT 时另写 .story/" + request.storyId() + "/planning/api-contract.md；"
                    + "data=PRESENT 时另写 .story/" + request.storyId() + "/planning/data-change.md（含兼容、迁移与回滚）。\n"
                    + "- 必须在 .story/" + request.storyId() + "/planning/probe-candidate/ 写每条 AC 的候选验收探针和 probes.properties。"
                    + "  manifest 的 ac.count 必须等于 AC 数；ac.N.path 必须写未来冻结路径 .ai4se/acceptance-probes/"
                    + request.storyId() + "/<file>；ac.N.command 必须调用该未来路径；ac.N.sha256 是候选文件 SHA-256。"
                    + "  这是候选，只有人执行 freeze-probes 后才成为冻结探针；不要改 .ai4se/。\n"
                    + "  探针必须证明所选测试实际执行：禁止使用 -DfailIfNoTests=false 或 "
                    + "-Dsurefire.failIfNoSpecifiedTests=false 来把缺失测试伪装为成功；Maven 精确选测时显式要求测试存在。\n"
                    + "- ## Behavioral Scenarios 的 verification 只能写 ENTRY_TEST、AC_PROBE:AC<n>，或本 Story 的"
                    + " .ai4se/acceptance-probes/" + request.storyId() + "/<file>.sh；不得写任意临时命令。\n"
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
                    + "- 若存在 slices/specification-decisions.md，它与冻结 Requirement 同为业务约束；不得重开或覆盖其中已答选择。\n"
                    + "- 若存在 slices/frozen-acceptance-probes.md，它是操作方冻结的可执行验收契约。必须保留其中精确测试选择器（包括类名/方法名）可执行，"
                    + "不得以语义近似但名称不同的测试替代。\n"
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
                + "Treat the Context Package below as the authoritative primary input. Do not roam the whole "
                + "repository; only perform the narrow direct reads explicitly allowed by your role instructions.\n"
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
