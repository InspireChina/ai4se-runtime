package com.ai4se.orchestration.pathway;

import com.ai4se.context.packagebuild.AnalysisPackageBuilder;
import com.ai4se.context.story.StoryOpener;
import com.ai4se.execution.api.ModelCliAdapter;
import com.ai4se.execution.model.RoleModelConfig;
import com.ai4se.execution.support.FunctionalModelCliAdapter;
import com.ai4se.execution.support.ProcessInvoker;
import com.ai4se.orchestration.acceptance.HumanAcceptanceRecords;
import com.ai4se.orchestration.analysis.AnalysisAdapterExecution;
import com.ai4se.orchestration.analysis.ApprovalRecords;
import com.ai4se.orchestration.analysis.AssumableAckRecords;
import com.ai4se.orchestration.analysis.ClarificationRecords;
import com.ai4se.orchestration.analysis.DiscoveryRecords;
import com.ai4se.orchestration.analysis.GapRecords;
import com.ai4se.orchestration.analysis.GapStatus;
import com.ai4se.orchestration.analysis.LowRiskPlanApproval;
import com.ai4se.orchestration.analysis.PlanAdapterExecution;
import com.ai4se.orchestration.analysis.PlanRecords;
import com.ai4se.orchestration.analysis.StageGateException;
import com.ai4se.orchestration.control.BoundedDeliveryLoop;
import com.ai4se.orchestration.control.BoundedLoopResult;
import com.ai4se.orchestration.delivery.DeliveryRecords;
import com.ai4se.orchestration.development.DevAdapterExecution;
import com.ai4se.orchestration.development.DevPackageBuilder;
import com.ai4se.orchestration.development.DevelopmentRecords;
import com.ai4se.orchestration.development.DiffScopeGuard;
import com.ai4se.orchestration.evidence.PathwayEvidenceWriter;
import com.ai4se.orchestration.evidence.PathwayEvidenceWriter.SpineDisclosure;
import com.ai4se.orchestration.lifecycle.KnowledgeLifecycleControl;
import com.ai4se.orchestration.lifecycle.OnboardPolicy;
import com.ai4se.orchestration.review.ReviewAdapterExecution;
import com.ai4se.orchestration.review.ReviewRecords;
import com.ai4se.orchestration.verification.VerificationControl;
import com.ai4se.orchestration.verification.VerificationEntries;
import com.ai4se.orchestration.verification.VerificationOutcome;
import com.ai4se.orchestration.workflow.StoryWorkflowMachine;
import com.ai4se.orchestration.workflow.StoryWorkflowState;
import com.ai4se.orchestration.workflow.WorkflowStage;
import com.ai4se.orchestration.workflow.WorkflowStatus;
import com.ai4se.runtime.common.util.Strings;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Unmanned main-chain runner (W1–W10 library glue).
 * Control stays in code.
 * <ul>
 *   <li>Default {@code fixture_control}: Discovery skip / Plan / Approval / DevMutation — Adapter not invoked.</li>
 *   <li>Optional {@code hybrid_adapter_dev}: Development goes Package → Adapter once → observe Diff
 *       (W4-on-spine). Analysis/Plan may still be fixture-prepared (disclosed).</li>
 * </ul>
 * Evidence meta must disclose spine; do not treat fixture green as full Adapter hang-in signoff.
 * Does not Push; optional local {@code git commit}.
 */
public final class PathwayRunner {

    public enum Script {
        V3,
        V4
    }

    public enum DeliveryMode {
        LOCAL_COMMIT,
        AWAITING_HUMAN_COMMIT
    }

    /** W10 · after human acceptance. */
    public enum LifecycleMode {
        /** Record lifecycle: noop + reason (default). */
        NOOP,
        /** Write Learning under .ai4se/learning + index. */
        APPLY_LEARNING,
        /** Stop after Delivery — do not run S5/S6 (tests that isolate earlier Waves). */
        SKIP
    }

    /**
     * Plan Approval 策略。人闸仍在；差别是谁写 approval.md。
     */
    public enum ApprovalMode {
        /** A-suite / fixture：缺 approval 时由 runner 落盘（披露 prepared_by_runner）。 */
        ALWAYS_RECORD,
        /** Field：Allowed⊆hint 且 verify∈entries 则自动批；否则停在 PLANNING。 */
        LOW_RISK_AUTO,
        /** 缺 approval 一律停，等真人落盘。 */
        REQUIRE_HUMAN
    }

    /**
     * V4 首轮 FAIL 来源。seeded = Field/夹具预埋失败（须披露）；natural = Adapter 真实产出后测红。
     */
    public enum V4FailMode {
        SEEDED,
        NATURAL
    }

    /**
     * ASSUMABLE Gap policy — silent pass-through is ALLOW (compat); REQUIRE_ACK forces governance.
     */
    public enum AssumablePolicy {
        ALLOW,
        REQUIRE_ACK
    }

    private PathwayRunner() {
    }

    /**
     * Prefer Onboarding's full usable test list (conjunction). Fall back to config.verifyCommand
     * only when entries are empty/unusable — never silently drop sibling modules.
     */
    static List<String> resolveVerifyCommands(Path workspace, Config config) throws IOException {
        List<String> usable = new ArrayList<String>(VerificationEntries.readUsableTestCommands(workspace));
        if (!usable.isEmpty()) {
            return usable;
        }
        if (config != null && !Strings.isBlank(config.verifyCommand)) {
            return Collections.singletonList(config.verifyCommand.trim());
        }
        throw new StageGateException("No usable Verification test commands in entries.yaml");
    }

    public static PathwayResult run(Config config, ProcessInvoker invoker) throws IOException {
        if (config == null) {
            throw new StageGateException("PathwayRunner config required");
        }
        if (invoker == null) {
            throw new StageGateException("ProcessInvoker required");
        }
        Path workspace = config.workspace;
        String storyId = config.storyId;
        // W10: main loop reuses slots — never force re-onboard here
        OnboardPolicy.requireSlotsAlreadyPresent(workspace);
        PathwayPreflight.check(workspace, config);

        RoleModelConfig roleModels = resolveRoleModels(workspace, config);

        if ("B".equalsIgnoreCase(config.suite)
                && config.devMutation == null
                && config.devAdapter == null) {
            throw new StageGateException(
                    "B-suite refuses default touch-file Dev — provide DevMutation or Dev Adapter");
        }
        if (config.devAdapter != null && config.devMutation != null) {
            throw new StageGateException("Use either devAdapter or devMutation, not both");
        }

        if (!Files.isDirectory(workspace.resolve(".story").resolve(storyId))) {
            if (config.seedPath == null) {
                throw new StageGateException("Story not open and seedPath missing: " + storyId);
            }
            StoryOpener.open(workspace, storyId, config.seedPath);
        }

        AnalysisPackageBuilder.build(workspace, storyId);
        if (config.resumeAfterStop) {
            StoryWorkflowState stopped = StoryWorkflowMachine.load(workspace, storyId);
            if (stopped.status() != WorkflowStatus.STOPPED) {
                throw new StageGateException(
                        "resumeAfterStop requires STOPPED story, was " + stopped.status());
            }
            if (!Strings.isBlank(config.clarificationQuestion)
                    && !Strings.isBlank(config.clarificationAnswer)) {
                ClarificationRecords.writeResolved(
                        workspace,
                        storyId,
                        config.clarificationQuestion,
                        config.clarificationAnswer,
                        config.clarificationResolver);
            }
            if (!ClarificationRecords.hasResolved(workspace, storyId)) {
                throw new StageGateException(
                        "resumeAfterStop requires clarification.resolved.md (or --clarification-a)");
            }
            GapRecords.write(
                    workspace,
                    storyId,
                    GapStatus.CLEAR,
                    0,
                    "clarification resolved on resume — gap cleared");
            StoryWorkflowMachine.save(
                    workspace,
                    new StoryWorkflowState(
                            storyId, WorkflowStage.ANALYSIS, WorkflowStatus.RUNNING, null));
        } else {
            StoryWorkflowMachine.start(workspace, storyId);
        }

        // Discovery: Analysis Adapter hang-in, else prefer pre-existing / report / skip.
        boolean discoveryPreparedByRunner;
        boolean analysisAdapterInvoked = false;
        if (config.analysisAdapter != null) {
            AnalysisAdapterExecution.submitAnalysisPackage(
                    workspace, storyId, config.analysisAdapter, config.adapterTimeout, roleModels);
            discoveryPreparedByRunner = false;
            analysisAdapterInvoked = true;
        } else if (DiscoveryRecords.hasReportOrSkip(workspace, storyId)) {
            discoveryPreparedByRunner = false;
        } else if (!Strings.isBlank(config.discoveryReportBody)) {
            DiscoveryRecords.writeReport(workspace, storyId, config.discoveryReportBody);
            discoveryPreparedByRunner = true;
        } else {
            DiscoveryRecords.writeSkip(
                    workspace, storyId, config.discoverySkipRationale, config.discoverySkipApprover);
            discoveryPreparedByRunner = !config.discoverySkipExplicit;
        }
        if (!Strings.isBlank(config.clarificationQuestion)
                && !Strings.isBlank(config.clarificationAnswer)) {
            ClarificationRecords.writeResolved(
                    workspace,
                    storyId,
                    config.clarificationQuestion,
                    config.clarificationAnswer,
                    config.clarificationResolver);
        } else if (!Strings.isBlank(config.clarificationQuestion)
                && Strings.isBlank(config.clarificationAnswer)
                && !ClarificationRecords.hasResolved(workspace, storyId)) {
            String q = config.clarificationQuestion.trim();
            ClarificationRecords.writePending(
                    workspace, storyId, q, "blocking clarification before Plan");
            GapRecords.write(
                    workspace,
                    storyId,
                    GapStatus.BLOCKED,
                    1,
                    "Clarification required: " + q);
            StoryWorkflowMachine.stop(
                    workspace, storyId, "BLOCKED: Clarification pending — " + q);
            throw new StageGateException(
                    "Clarification Stop: answer required before Planning"
                            + " (clarification.pending.md; Gap BLOCKED)");
        }

        boolean gapPreparedByRunner;
        if (GapRecords.hasReport(workspace, storyId)) {
            GapStatus existing = GapRecords.readStatus(workspace, storyId);
            if (existing == GapStatus.BLOCKED && !ClarificationRecords.hasResolved(workspace, storyId)) {
                if (!StoryWorkflowMachine.load(workspace, storyId).status()
                        .equals(WorkflowStatus.STOPPED)) {
                    StoryWorkflowMachine.stop(
                            workspace,
                            storyId,
                            "BLOCKED: unresolved gap — " + existing.name());
                }
                throw new StageGateException(
                        "Gap BLOCKED — cannot enter Planning until Clarification resolved");
            }
            if (existing == GapStatus.BLOCKED && ClarificationRecords.hasResolved(workspace, storyId)) {
                GapRecords.write(
                        workspace,
                        storyId,
                        GapStatus.CLEAR,
                        0,
                        "clarification resolved — gap cleared for Planning");
                gapPreparedByRunner = false;
            } else {
                gapPreparedByRunner = false;
            }
            if (GapRecords.readStatus(workspace, storyId) == GapStatus.ASSUMABLE) {
                enforceAssumablePolicy(workspace, storyId, config);
            }
        } else if (analysisAdapterInvoked) {
            throw new StageGateException(
                    "Analysis Adapter completed without gap.report.properties"
                            + " — refuse runner CLEAR (假分析 / Gap 必须由 Analysis 产出)");
        } else {
            GapRecords.write(
                    workspace,
                    storyId,
                    GapStatus.CLEAR,
                    0,
                    "pathway runner clear (no analysis adapter — fixture)");
            gapPreparedByRunner = true;
        }
        StoryWorkflowMachine.advance(workspace, storyId); // → PLANNING

        // Plan/Approval: Plan Adapter hang-in, else prefer pre-existing / write / human-owned.
        boolean approvalPreparedByRunner;
        boolean planAdapterInvoked = false;
        if (config.planAdapter != null && !PlanRecords.hasFormalPlan(workspace, storyId)) {
            PlanAdapterExecution.submitPlanPackage(
                    workspace,
                    storyId,
                    config.planAdapter,
                    config.adapterTimeout,
                    config.allowedFiles,
                    roleModels);
            planAdapterInvoked = true;
        } else if (!PlanRecords.hasFormalPlan(workspace, storyId)) {
            PlanRecords.writeFormalPlan(workspace, storyId, config.planSummary, config.allowedFiles);
        }
        if (ApprovalRecords.isApproved(workspace, storyId)) {
            approvalPreparedByRunner = false;
        } else if (config.approvalMode == ApprovalMode.REQUIRE_HUMAN) {
            throw new StageGateException(
                    "Plan Approval required — human must write approval.md before Development");
        } else if (config.approvalMode == ApprovalMode.LOW_RISK_AUTO) {
            // When verifyCommand is blank (entries-only), do not invent mvn -q test for whitelist.
            String verifyForApproval = Strings.isBlank(config.verifyCommand) ? null : config.verifyCommand;
            String reason = LowRiskPlanApproval.ineligibleReason(
                    workspace,
                    storyId,
                    config.allowedFiles,
                    verifyForApproval,
                    config.approvalRequireTestPathsOnly);
            if (reason != null) {
                throw new StageGateException(
                        "Plan Approval required (low-risk auto ineligible): " + reason);
            }
            if (Strings.isBlank(config.verifyCommand)) {
                List<String> usable = VerificationEntries.readUsableTestCommands(workspace);
                if (usable.isEmpty()) {
                    throw new StageGateException(
                            "Plan Approval required (low-risk auto): no usable test entries");
                }
            }
            String approvalNote = !Strings.isBlank(config.approvalNote)
                    ? config.approvalNote
                    : "低风险自动批准：Allowed⊆hint 且 verify∈entries";
            ApprovalRecords.approvePlan(
                    workspace,
                    storyId,
                    LowRiskPlanApproval.APPROVER,
                    approvalNote,
                    LowRiskPlanApproval.MODE);
            approvalPreparedByRunner = false;
        } else {
            String approvalNote = !Strings.isBlank(config.approvalNote)
                    ? config.approvalNote
                    : (config.planHumanOwned || planAdapterInvoked
                            ? "人工批准（Field / Plan Adapter）"
                            : "pathway runner");
            ApprovalRecords.approvePlan(workspace, storyId, config.planApprover, approvalNote);
            approvalPreparedByRunner = !(config.planHumanOwned || planAdapterInvoked);
        }
        // Unconditional auth: final Plan Allowed ⊆ open-run hint/writeScope before Development.
        // Must not be tied to whether Approval was newly written (old approval.md must not bypass).
        enforcePlanAllowedWithinHint(workspace, storyId, config.allowedFiles);
        StoryWorkflowMachine.advance(workspace, storyId); // → DEVELOPMENT

        List<String> verifyCommands = resolveVerifyCommands(workspace, config);
        if (config.boundedMaxDevelopmentRounds > 0) {
            BoundedLoopResult loop = BoundedDeliveryLoop.run(
                    workspace,
                    storyId,
                    config.boundedMaxDevelopmentRounds,
                    config.devAdapter,
                    config.adapterTimeout,
                    roleModels,
                    verifyCommands,
                    config.changeNote,
                    invoker);
            if (!loop.passed()) {
                throw new StageGateException(
                        "Production bounded loop stopped: " + loop.reason
                                + " after " + loop.developmentRoundsUsed + " development round(s)");
            }
        } else {
            runDevelopmentRound(workspace, config, 1, invoker, roleModels);
            StoryWorkflowMachine.advance(workspace, storyId); // → VERIFICATION

            // Same command set for V4 round-1 FAIL and final PASS — never silent single-command vs entries.
            if (config.script == Script.V4) {
                VerificationControl.VerificationRecord fail = VerificationControl.run(
                        workspace, storyId, verifyCommands, invoker);
                if (fail.outcome != VerificationOutcome.FAIL) {
                    throw new StageGateException(
                            "V4 requires first Verify FAIL, got " + fail.outcome
                                    + " (v4_fail_mode=" + config.v4FailMode + ")");
                }
                // re-Dev after Defect Package built by Control — Adapter or mutation; no Adapter retry
                runDevelopmentRound(workspace, config, 2, invoker, roleModels);
                StoryWorkflowMachine.advance(workspace, storyId); // → VERIFICATION again
            }

            VerificationControl.VerificationRecord pass = VerificationControl.run(
                    workspace, storyId, verifyCommands, invoker);
            if (pass.outcome != VerificationOutcome.PASS) {
                throw new StageGateException("Pathway requires Verify PASS, got " + pass.outcome);
            }
        }

        StoryWorkflowMachine.advance(workspace, storyId); // → REVIEW
        boolean reviewAdapterInvoked = false;
        if (config.reviewAdapter != null) {
            ReviewAdapterExecution.submitReviewPackage(
                    workspace, storyId, config.reviewAdapter, config.adapterTimeout, roleModels);
            reviewAdapterInvoked = true;
        } else if (config.allowReviewFixture) {
            ReviewRecords.write(
                    workspace,
                    storyId,
                    config.reviewDecision,
                    config.reviewResidualRisk,
                    ReviewRecords.SOURCE_FIXTURE);
        } else {
            throw new StageGateException(
                    "Review Adapter required — or allowReviewFixture(true) with disclosed fixture"
                            + " (第九环不可静默「通过」)");
        }
        if (ReviewRecords.isRejected(workspace, storyId)) {
            throw new StageGateException("Review 驳回 — cannot enter Delivery");
        }
        StoryWorkflowMachine.advance(workspace, storyId); // → DELIVERY

        String commitSha = null;
        if (config.deliveryMode == DeliveryMode.LOCAL_COMMIT) {
            commitSha = DeliveryRecords.commitLocalAndRecord(
                    workspace, storyId, config.commitMessage, invoker);
        } else {
            DeliveryRecords.recordAwaitingHumanCommit(workspace, storyId);
        }

        StoryWorkflowState done = StoryWorkflowMachine.complete(workspace, storyId);
        if (done.status() != WorkflowStatus.COMPLETED) {
            throw new StageGateException("Expected COMPLETED, got " + done.status());
        }

        Path lifecycleArtifact = null;
        HumanAcceptanceRecords.Kind acceptanceKind = HumanAcceptanceRecords.Kind.FIXTURE;
        if (config.lifecycleMode != LifecycleMode.SKIP) {
            acceptanceKind = config.humanAcceptanceKind;
            HumanAcceptanceRecords.recordAccepted(
                    workspace,
                    storyId,
                    config.humanAccepter,
                    config.humanAcceptanceNote,
                    acceptanceKind);
            if (config.lifecycleMode == LifecycleMode.APPLY_LEARNING) {
                lifecycleArtifact = KnowledgeLifecycleControl.applyLearning(
                        workspace, storyId, config.learningId, config.learningBody);
            } else {
                lifecycleArtifact = KnowledgeLifecycleControl.noop(
                        workspace, storyId, config.lifecycleNoopReason);
            }
            KnowledgeLifecycleControl.requireCompleted(workspace, storyId);
        }

        String humanKind =
                acceptanceKind == HumanAcceptanceRecords.Kind.HUMAN
                        ? "human"
                        : (acceptanceKind == HumanAcceptanceRecords.Kind.DEFERRED ? "deferred" : "fixture");
        SpineDisclosure spine;
        boolean anyAdapter = config.devAdapter != null
                || analysisAdapterInvoked
                || planAdapterInvoked
                || reviewAdapterInvoked;
        if (anyAdapter) {
            ModelCliAdapter kindSource = config.devAdapter != null
                    ? config.devAdapter
                    : (config.reviewAdapter != null
                            ? config.reviewAdapter
                            : (config.analysisAdapter != null
                                    ? config.analysisAdapter
                                    : config.planAdapter));
            String adapterKind = kindSource instanceof FunctionalModelCliAdapter
                    ? SpineDisclosure.KIND_FUNCTIONAL
                    : SpineDisclosure.KIND_MODEL_CLI;
            String roles = joinAdapterRoles(
                    analysisAdapterInvoked, planAdapterInvoked,
                    config.devAdapter != null, reviewAdapterInvoked);
            spine = SpineDisclosure.hybridAdapterDev(
                    humanKind,
                    adapterKind,
                    discoveryPreparedByRunner,
                    approvalPreparedByRunner,
                    gapPreparedByRunner,
                    roles,
                    v4ExtraMeta(config));
        } else {
            spine = new SpineDisclosure(
                    SpineDisclosure.MODE_FIXTURE,
                    false,
                    discoveryPreparedByRunner,
                    approvalPreparedByRunner,
                    gapPreparedByRunner,
                    humanKind,
                    "none",
                    SpineDisclosure.KIND_NONE,
                    v4ExtraMeta(config));
        }
        String waveNote;
        if (anyAdapter) {
            waveNote = "B".equalsIgnoreCase(config.suite)
                    ? "B-suite-hybrid-adapter-dev"
                    : "W4-hybrid-adapter-dev";
        } else {
            waveNote = "B".equalsIgnoreCase(config.suite) ? "B-suite-fixture-spine" : "W10-fixture-spine";
        }
        Path evidence = PathwayEvidenceWriter.write(
                workspace,
                storyId,
                config.suite,
                config.script.name(),
                config.adapter,
                waveNote,
                spine);

        return new PathwayResult(
                storyId, config.script, commitSha, evidence, done, lifecycleArtifact, spine);
    }

    private static String v4ExtraMeta(Config config) {
        StringBuilder sb = new StringBuilder();
        if (config.script == Script.V4) {
            sb.append("v4_fail_mode: ").append(config.v4FailMode.name().toLowerCase(Locale.ROOT)).append('\n');
            if (!Strings.isBlank(config.v4Round1Source)) {
                sb.append("v4_round1_source: ").append(config.v4Round1Source.trim()).append('\n');
            }
        }
        if (config.roleModels != null && !config.roleModels.isEmpty()) {
            sb.append("role_models:\n");
            if (!Strings.isBlank(config.roleModels.defaultModel())) {
                sb.append("  default: ").append(config.roleModels.defaultModel()).append('\n');
            }
            for (Map.Entry<String, String> e : config.roleModels.byRole().entrySet()) {
                sb.append("  ").append(e.getKey()).append(": ").append(e.getValue()).append('\n');
            }
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    private static void enforceAssumablePolicy(Path workspace, String storyId, Config config)
            throws IOException {
        if (config.assumablePolicy != AssumablePolicy.REQUIRE_ACK) {
            return;
        }
        if (AssumableAckRecords.hasAck(workspace, storyId)) {
            return;
        }
        if (!Strings.isBlank(config.assumableAckBy)) {
            AssumableAckRecords.write(
                    workspace, storyId, config.assumableAckBy, config.assumableAckNote);
            return;
        }
        AssumableAckRecords.requireAck(workspace, storyId);
    }

    /**
     * Final Plan Allowed must be ⊆ open-run allowed hint / operator writeScope.
     * Runs after Approval resolution so pre-existing approval.md cannot bypass the ceiling.
     */
    static void enforcePlanAllowedWithinHint(
            Path workspace, String storyId, List<String> allowedHint) throws IOException {
        if (allowedHint == null || allowedHint.isEmpty()) {
            throw new StageGateException("allowed hint / writeScope required before Development");
        }
        List<String> planAllowed = PlanRecords.readAllowedFiles(workspace, storyId);
        if (planAllowed.isEmpty()) {
            throw new StageGateException("Plan Allowed is empty before Development");
        }
        List<String> outside = DiffScopeGuard.findViolations(planAllowed, allowedHint);
        if (!outside.isEmpty()) {
            throw new StageGateException(
                    "Plan Allowed must be subset of operator writeScope/hint before Development; outside: "
                            + outside);
        }
    }

    private static String joinAdapterRoles(
            boolean analysis, boolean plan, boolean development, boolean review) {
        List<String> roles = new ArrayList<String>();
        if (analysis) {
            roles.add("Analysis");
        }
        if (plan) {
            roles.add("Planning");
        }
        if (development) {
            roles.add("Development");
        }
        if (review) {
            roles.add("Review");
        }
        if (roles.isEmpty()) {
            return "none";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < roles.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(roles.get(i));
        }
        return sb.toString();
    }

    /**
     * Development-round mutation. Round 1 = first Dev; round 2 = re-Dev after Defect (V4).
     * Used by B-suite / fixtures when Adapter is not on the spine.
     */
    public interface DevMutation {
        void apply(Path workspace, int developmentRound) throws IOException;
    }

    private static void runDevelopmentRound(
            Path workspace,
            Config config,
            int round,
            ProcessInvoker invoker,
            RoleModelConfig roleModels) throws IOException {
        if (config.devAdapter != null) {
            DevAdapterExecution.submitDevPackage(
                    workspace,
                    config.storyId,
                    round,
                    config.devAdapter,
                    config.adapterTimeout,
                    roleModels);
        } else {
            applyDevMutation(workspace, config, round);
            // Mutation path has no Adapter submit — build the round's Dev Package explicitly
            // (including Defect P1 after Verify FAIL).
            DevPackageBuilder.build(workspace, config.storyId);
        }
        String note = round <= 1
                ? config.changeNote
                : config.changeNote + " (after defect)";
        DevelopmentRecords.recordObservedChanges(workspace, config.storyId, note, invoker);
    }

    private static RoleModelConfig resolveRoleModels(Path workspace, Config config) throws IOException {
        RoleModelConfig file = RoleModelConfig.loadFromWorkspace(workspace);
        RoleModelConfig env = RoleModelConfig.fromProcessEnv();
        RoleModelConfig cfg = config.roleModels == null ? RoleModelConfig.empty() : config.roleModels;
        // Precedence: Config/CLI overlay > process env > customer file
        return file.mergeOverlay(env).mergeOverlay(cfg);
    }

    private static void applyDevMutation(Path workspace, Config config, int round) throws IOException {
        if (config.devMutation != null) {
            config.devMutation.apply(workspace, round);
            return;
        }
        ensureChangedFilesExist(workspace, config);
    }

    private static void ensureChangedFilesExist(Path workspace, Config config) throws IOException {
        for (String rel : config.allowedFiles) {
            Path p = workspace.resolve(rel);
            if (!Files.isRegularFile(p)) {
                Files.createDirectories(p.getParent());
                Files.write(p, ("// pathway fixture " + rel + "\n").getBytes(StandardCharsets.UTF_8));
            } else {
                // touch so porcelain sees a change on re-Dev
                byte[] existing = Files.readAllBytes(p);
                Files.write(p, (new String(existing, StandardCharsets.UTF_8) + "// touch\n")
                        .getBytes(StandardCharsets.UTF_8));
            }
        }
    }

    public static final class Config {
        public final Path workspace;
        public final String storyId;
        public final Script script;
        public final String suite;
        public final String adapter;
        public final Path seedPath;
        public final String verifyCommand;
        public final List<String> allowedFiles;
        public final String discoverySkipRationale;
        public final String discoverySkipApprover;
        /** True when caller set {@link Builder#discoverySkip} (human-owned skip, not fixture default). */
        public final boolean discoverySkipExplicit;
        /** When non-blank, write discovery.report.md instead of skip. */
        public final String discoveryReportBody;
        public final String clarificationQuestion;
        public final String clarificationAnswer;
        public final String clarificationResolver;
        public final String planSummary;
        public final String planApprover;
        /** True when Plan/Allowed/Approval are human-owned (e.g. Field CLI), not fixture invention. */
        public final boolean planHumanOwned;
        public final String approvalNote;
        public final ApprovalMode approvalMode;
        /** When {@link ApprovalMode#LOW_RISK_AUTO}, also require Allowed to be test paths only. */
        public final boolean approvalRequireTestPathsOnly;
        public final String changeNote;
        public final String reviewDecision;
        public final String reviewResidualRisk;
        /** When true and no reviewAdapter, fixture review is allowed with review_source=fixture disclosure. */
        public final boolean allowReviewFixture;
        public final DeliveryMode deliveryMode;
        public final String commitMessage;
        public final LifecycleMode lifecycleMode;
        public final String humanAccepter;
        public final String humanAcceptanceNote;
        public final HumanAcceptanceRecords.Kind humanAcceptanceKind;
        public final String lifecycleNoopReason;
        public final String learningId;
        public final String learningBody;
        public final DevMutation devMutation;
        public final ModelCliAdapter devAdapter;
        public final ModelCliAdapter analysisAdapter;
        public final ModelCliAdapter planAdapter;
        public final ModelCliAdapter reviewAdapter;
        public final RoleModelConfig roleModels;
        public final AssumablePolicy assumablePolicy;
        public final String assumableAckBy;
        public final String assumableAckNote;
        public final Duration adapterTimeout;
        /** V4 only: seeded (disclosed) vs natural first-FAIL. Default SEEDED. */
        public final V4FailMode v4FailMode;
        /** Optional disclosure for Round1 source (e.g. incomplete_hook). */
        public final String v4Round1Source;
        /** Resume a STOPPED story after Clarification was answered. */
        public final boolean resumeAfterStop;
        /**
         * When &gt; 0, use {@link BoundedDeliveryLoop} instead of Script V3/V4.
         * ProductionPathway sets this from {@code maxDevelopmentRounds}.
         */
        public final int boundedMaxDevelopmentRounds;

        private Config(Builder b) {
            this.workspace = b.workspace;
            this.storyId = b.storyId;
            this.script = b.script == null ? Script.V3 : b.script;
            this.suite = Strings.isBlank(b.suite) ? "A" : b.suite;
            this.devAdapter = b.devAdapter;
            this.analysisAdapter = b.analysisAdapter;
            this.planAdapter = b.planAdapter;
            this.reviewAdapter = b.reviewAdapter;
            this.roleModels = b.roleModels == null ? RoleModelConfig.empty() : b.roleModels;
            this.assumablePolicy =
                    b.assumablePolicy == null ? AssumablePolicy.ALLOW : b.assumablePolicy;
            this.assumableAckBy = b.assumableAckBy;
            this.assumableAckNote = b.assumableAckNote;
            this.adapterTimeout = b.adapterTimeout == null ? Duration.ofMinutes(10) : b.adapterTimeout;
            this.v4FailMode = b.v4FailMode == null ? V4FailMode.SEEDED : b.v4FailMode;
            this.v4Round1Source = b.v4Round1Source;
            this.resumeAfterStop = b.resumeAfterStop;
            this.boundedMaxDevelopmentRounds =
                    b.boundedMaxDevelopmentRounds < 0 ? 0 : b.boundedMaxDevelopmentRounds;
            if (this.boundedMaxDevelopmentRounds > 0 && this.devAdapter == null) {
                throw new StageGateException(
                        "boundedDeliveryLoop requires devAdapter (no DevMutation)");
            }
            if (Strings.isBlank(b.adapter) || "none".equalsIgnoreCase(b.adapter)) {
                if (this.devAdapter != null) {
                    this.adapter = this.devAdapter.name();
                } else if (this.reviewAdapter != null) {
                    this.adapter = this.reviewAdapter.name();
                } else if (this.analysisAdapter != null) {
                    this.adapter = this.analysisAdapter.name();
                } else if (this.planAdapter != null) {
                    this.adapter = this.planAdapter.name();
                } else {
                    this.adapter = "none";
                }
            } else {
                this.adapter = b.adapter.trim();
            }
            this.seedPath = b.seedPath;
            // null → legacy fixture default; blank → entries-only (production / Field with entries).
            this.verifyCommand = b.verifyCommand == null ? "mvn -q test" : b.verifyCommand.trim();
            this.allowedFiles = Collections.unmodifiableList(new ArrayList<String>(b.allowedFiles));
            this.discoverySkipRationale =
                    Strings.isBlank(b.discoverySkipRationale) ? "fixture known" : b.discoverySkipRationale;
            this.discoverySkipApprover =
                    Strings.isBlank(b.discoverySkipApprover) ? "pathway-runner" : b.discoverySkipApprover;
            this.discoverySkipExplicit = b.discoverySkipExplicit;
            this.discoveryReportBody = b.discoveryReportBody;
            this.clarificationQuestion = b.clarificationQuestion;
            this.clarificationAnswer = b.clarificationAnswer;
            this.clarificationResolver =
                    Strings.isBlank(b.clarificationResolver) ? "human" : b.clarificationResolver;
            this.planSummary = Strings.isBlank(b.planSummary) ? "pathway plan" : b.planSummary;
            this.planApprover = Strings.isBlank(b.planApprover) ? "pathway-runner" : b.planApprover;
            this.planHumanOwned = b.planHumanOwned;
            this.approvalNote = b.approvalNote;
            this.approvalMode = b.approvalMode == null ? ApprovalMode.ALWAYS_RECORD : b.approvalMode;
            this.approvalRequireTestPathsOnly = b.approvalRequireTestPathsOnly;
            this.changeNote = Strings.isBlank(b.changeNote) ? "implement within Allowed" : b.changeNote;
            this.reviewDecision = Strings.isBlank(b.reviewDecision) ? "通过" : b.reviewDecision;
            this.reviewResidualRisk = b.reviewResidualRisk;
            this.allowReviewFixture = b.allowReviewFixture;
            this.deliveryMode = b.deliveryMode == null ? DeliveryMode.LOCAL_COMMIT : b.deliveryMode;
            this.commitMessage = Strings.isBlank(b.commitMessage)
                    ? ("ai4se: story " + b.storyId)
                    : b.commitMessage;
            this.lifecycleMode = b.lifecycleMode == null ? LifecycleMode.NOOP : b.lifecycleMode;
            this.humanAccepter =
                    Strings.isBlank(b.humanAccepter) ? "pathway-runner" : b.humanAccepter;
            this.humanAcceptanceNote =
                    Strings.isBlank(b.humanAcceptanceNote) ? "A-suite fixture acceptance" : b.humanAcceptanceNote;
            this.humanAcceptanceKind = b.humanAcceptanceKind == null
                    ? HumanAcceptanceRecords.Kind.FIXTURE
                    : b.humanAcceptanceKind;
            this.lifecycleNoopReason = Strings.isBlank(b.lifecycleNoopReason)
                    ? "no knowledge delta for fixture story"
                    : b.lifecycleNoopReason;
            this.learningId = Strings.isBlank(b.learningId) ? "learn-" + b.storyId : b.learningId;
            this.learningBody = Strings.isBlank(b.learningBody)
                    ? "Delivery pattern note from story " + b.storyId
                    : b.learningBody;
            this.devMutation = b.devMutation;
            if (workspace == null || Strings.isBlank(storyId)) {
                throw new StageGateException("workspace and storyId required");
            }
            if (allowedFiles.isEmpty()) {
                throw new StageGateException("allowedFiles required");
            }
            if (this.lifecycleMode == LifecycleMode.APPLY_LEARNING
                    && this.humanAcceptanceKind == HumanAcceptanceRecords.Kind.FIXTURE) {
                throw new StageGateException(
                        "APPLY_LEARNING requires humanAcceptanceKind HUMAN or DEFERRED — refuse silent fixture");
            }
        }

        public static Builder builder(Path workspace, String storyId) {
            return new Builder(workspace, storyId);
        }

        public static final class Builder {
            private final Path workspace;
            private final String storyId;
            private Script script = Script.V3;
            private String suite = "A";
            private String adapter = "none";
            private Path seedPath;
            /** null = legacy default {@code mvn -q test}; blank = rely on entries.yaml only. */
            private String verifyCommand;
            private final List<String> allowedFiles = new ArrayList<String>();
            private String discoverySkipRationale;
            private String discoverySkipApprover;
            private boolean discoverySkipExplicit;
            private String discoveryReportBody;
            private String clarificationQuestion;
            private String clarificationAnswer;
            private String clarificationResolver;
            private String planSummary;
            private String planApprover;
            private boolean planHumanOwned;
            private String approvalNote;
            private ApprovalMode approvalMode = ApprovalMode.ALWAYS_RECORD;
            private boolean approvalRequireTestPathsOnly;
            private String changeNote;
            private String reviewDecision;
            private String reviewResidualRisk;
            private boolean allowReviewFixture;
            private DeliveryMode deliveryMode = DeliveryMode.LOCAL_COMMIT;
            private String commitMessage;
            private LifecycleMode lifecycleMode = LifecycleMode.NOOP;
            private String humanAccepter;
            private String humanAcceptanceNote;
            private HumanAcceptanceRecords.Kind humanAcceptanceKind = HumanAcceptanceRecords.Kind.FIXTURE;
            private String lifecycleNoopReason;
            private String learningId;
            private String learningBody;
            private DevMutation devMutation;
            private ModelCliAdapter devAdapter;
            private ModelCliAdapter analysisAdapter;
            private ModelCliAdapter planAdapter;
            private ModelCliAdapter reviewAdapter;
            private RoleModelConfig roleModels = RoleModelConfig.empty();
            private AssumablePolicy assumablePolicy = AssumablePolicy.ALLOW;
            private String assumableAckBy;
            private String assumableAckNote;
            private Duration adapterTimeout;
            private V4FailMode v4FailMode = V4FailMode.SEEDED;
            private String v4Round1Source;
            private boolean resumeAfterStop;
            private int boundedMaxDevelopmentRounds;

            private Builder(Path workspace, String storyId) {
                this.workspace = workspace;
                this.storyId = storyId;
            }

            public Builder script(Script s) {
                this.script = s;
                return this;
            }

            public Builder suite(String s) {
                this.suite = s;
                return this;
            }

            public Builder adapter(String a) {
                this.adapter = a;
                return this;
            }

            public Builder seedPath(Path p) {
                this.seedPath = p;
                return this;
            }

            public Builder verifyCommand(String c) {
                this.verifyCommand = c;
                return this;
            }

            /** Production: do not invent {@code mvn -q test}; Verification uses entries conjunction. */
            public Builder verifyFromEntriesOnly() {
                this.verifyCommand = "";
                return this;
            }

            public Builder allowedFile(String f) {
                this.allowedFiles.add(f);
                return this;
            }

            public Builder discoveryReportBody(String body) {
                this.discoveryReportBody = body;
                return this;
            }

            /** Legal discovery.skip with human rationale — not canned report injection. */
            public Builder discoverySkip(String rationale, String approver) {
                this.discoverySkipRationale = rationale;
                this.discoverySkipApprover = approver;
                this.discoverySkipExplicit = true;
                this.discoveryReportBody = null;
                return this;
            }

            public Builder lifecycleNoopReason(String reason) {
                this.lifecycleNoopReason = reason;
                return this;
            }

            public Builder clarificationResolved(String question, String answer, String resolver) {
                this.clarificationQuestion = question;
                this.clarificationAnswer = answer;
                this.clarificationResolver = resolver;
                return this;
            }

            /** Open Clarification Stop: question recorded, no answer → Gap BLOCKED. */
            public Builder clarificationQuestionOnly(String question) {
                this.clarificationQuestion = question;
                this.clarificationAnswer = null;
                return this;
            }

            public Builder planSummary(String summary) {
                this.planSummary = summary;
                return this;
            }

            public Builder planApprover(String approver) {
                this.planApprover = approver;
                return this;
            }

            /** Mark Plan/Allowed/Approval as human-owned (Field CLI) for honest spine disclosure. */
            public Builder planHumanOwned(boolean humanOwned) {
                this.planHumanOwned = humanOwned;
                return this;
            }

            public Builder approvalNote(String note) {
                this.approvalNote = note;
                return this;
            }

            public Builder approvalMode(ApprovalMode mode) {
                this.approvalMode = mode;
                return this;
            }

            public Builder approvalRequireTestPathsOnly(boolean require) {
                this.approvalRequireTestPathsOnly = require;
                return this;
            }

            public Builder humanAcceptanceNote(String note) {
                this.humanAcceptanceNote = note;
                return this;
            }

            public Builder changeNote(String n) {
                this.changeNote = n;
                return this;
            }

            public Builder deliveryMode(DeliveryMode m) {
                this.deliveryMode = m;
                return this;
            }

            public Builder commitMessage(String m) {
                this.commitMessage = m;
                return this;
            }

            public Builder lifecycleMode(LifecycleMode m) {
                this.lifecycleMode = m;
                return this;
            }

            public Builder humanAccepter(String a) {
                this.humanAccepter = a;
                return this;
            }

            public Builder humanAcceptanceKind(HumanAcceptanceRecords.Kind kind) {
                this.humanAcceptanceKind = kind;
                return this;
            }

            public Builder applyLearning(String id, String body) {
                this.lifecycleMode = LifecycleMode.APPLY_LEARNING;
                this.learningId = id;
                this.learningBody = body;
                return this;
            }

            public Builder devMutation(DevMutation mutation) {
                this.devMutation = mutation;
                return this;
            }

            /** W4-on-spine: Development submits built Package to this Adapter once. */
            public Builder devAdapter(ModelCliAdapter adapter) {
                this.devAdapter = adapter;
                return this;
            }

            /** Analysis Package → Adapter once → must leave discovery.report|skip. */
            public Builder analysisAdapter(ModelCliAdapter adapter) {
                this.analysisAdapter = adapter;
                return this;
            }

            /** Planning Package → Adapter once → must leave plan.md with Allowed. */
            public Builder planAdapter(ModelCliAdapter adapter) {
                this.planAdapter = adapter;
                return this;
            }

            /** Review Package → Adapter once → structured review-result (source=adapter). */
            public Builder reviewAdapter(ModelCliAdapter adapter) {
                this.reviewAdapter = adapter;
                return this;
            }

            /** Per-role model ids (Analysis / Dev / Review / …). Overlay on file+env. */
            public Builder roleModels(RoleModelConfig models) {
                this.roleModels = models == null ? RoleModelConfig.empty() : models;
                return this;
            }

            public Builder roleModel(String role, String modelId) {
                RoleModelConfig.Builder b = RoleModelConfig.builder();
                if (this.roleModels != null && !this.roleModels.isEmpty()) {
                    if (!Strings.isBlank(this.roleModels.defaultModel())) {
                        b.defaultModel(this.roleModels.defaultModel());
                    }
                    for (Map.Entry<String, String> e : this.roleModels.byRole().entrySet()) {
                        b.role(e.getKey(), e.getValue());
                    }
                }
                b.role(role, modelId);
                this.roleModels = b.build();
                return this;
            }

            public Builder reviewDecision(String decision) {
                this.reviewDecision = decision;
                return this;
            }

            public Builder reviewResidualRisk(String risk) {
                this.reviewResidualRisk = risk;
                return this;
            }

            /**
             * Explicit fixture Review — disclosed as review_source=fixture; not a closed-loop claim.
             * Required when reviewAdapter is null.
             */
            public Builder allowReviewFixture(boolean allow) {
                this.allowReviewFixture = allow;
                return this;
            }

            public Builder assumablePolicy(AssumablePolicy policy) {
                this.assumablePolicy = policy;
                return this;
            }

            /** When REQUIRE_ACK and ack file absent, runner may record ack for Field/fixture disclosure. */
            public Builder assumableAck(String by, String note) {
                this.assumableAckBy = by;
                this.assumableAckNote = note;
                return this;
            }

            public Builder v4FailMode(V4FailMode mode) {
                this.v4FailMode = mode;
                return this;
            }

            public Builder v4Round1Source(String source) {
                this.v4Round1Source = source;
                return this;
            }

            /** Continue a Clarification-STOPPED story after answer is available. */
            public Builder resumeAfterStop(boolean resume) {
                this.resumeAfterStop = resume;
                return this;
            }

            /**
             * Production bounded Dev↔Verify loop (PR2). When set, Script V3/V4 branches are skipped.
             */
            public Builder boundedDeliveryLoop(int maxDevelopmentRounds) {
                this.boundedMaxDevelopmentRounds = maxDevelopmentRounds;
                return this;
            }

            public Builder adapterTimeout(Duration timeout) {
                this.adapterTimeout = timeout;
                return this;
            }

            public Config build() {
                return new Config(this);
            }
        }
    }

    public static final class PathwayResult {
        public final String storyId;
        public final Script script;
        public final String commitShaOrNull;
        public final Path evidenceRoot;
        public final StoryWorkflowState finalState;
        public final Path lifecycleArtifactOrNull;
        public final SpineDisclosure spine;

        public PathwayResult(
                String storyId,
                Script script,
                String commitShaOrNull,
                Path evidenceRoot,
                StoryWorkflowState finalState,
                Path lifecycleArtifactOrNull,
                SpineDisclosure spine) {
            this.storyId = storyId;
            this.script = script;
            this.commitShaOrNull = commitShaOrNull;
            this.evidenceRoot = evidenceRoot;
            this.finalState = finalState;
            this.lifecycleArtifactOrNull = lifecycleArtifactOrNull;
            this.spine = spine;
        }
    }
}
