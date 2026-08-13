package com.ai4se.orchestration.production;

import com.ai4se.context.packagebuild.PackageRefuseException;
import com.ai4se.context.story.StoryRequirementReader;
import com.ai4se.execution.api.ModelCliAdapter;
import com.ai4se.execution.cursor.CursorCliAdapter;
import com.ai4se.execution.support.FunctionalModelCliAdapter;
import com.ai4se.execution.support.ProcessInvoker;
import com.ai4se.orchestration.analysis.StageGateException;
import com.ai4se.orchestration.pathway.PathwayRunner;
import com.ai4se.orchestration.pathway.PathwayRunner.AssumablePolicy;
import com.ai4se.orchestration.pathway.PathwayRunner.DeliveryMode;
import com.ai4se.orchestration.pathway.PathwayRunner.LifecycleMode;
import com.ai4se.orchestration.pathway.PathwayRunner.PathwayResult;
import com.ai4se.orchestration.pathway.PathwayRunner.Script;
import com.ai4se.orchestration.run.ProductionTerminal;
import com.ai4se.orchestration.run.RunLedger;
import com.ai4se.orchestration.support.WorkspaceGit;
import com.ai4se.orchestration.verification.VerificationEntries;
import com.ai4se.orchestration.workflow.StoryWorkflowMachine;
import com.ai4se.orchestration.workflow.StoryWorkflowState;
import com.ai4se.runtime.common.util.Strings;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Strict production facade — sole assembler for customer Story runs.
 *
 * <p>Always injects the same real {@link CursorCliAdapter} for Analysis / Planning / Development /
 * Review. Refuses Functional adapters, DevMutation, review fixtures, and seeded V4 knobs.
 */
public final class ProductionPathway {

    /** Bullet {@code -}/{@code *} or numbered {@code 1.}/{@code 1)} — prose is not Acceptance. */
    private static final Pattern ACCEPTANCE_ITEM =
            Pattern.compile("(?m)^\\s*(?:[-*]|\\d+[.)])\\s+\\S+");

    private ProductionPathway() {
    }

    public static ProductionRunResult run(ProductionRunRequest request, ProcessInvoker invoker)
            throws IOException {
        return run(request, invoker, new CursorCliAdapter());
    }

    /**
     * Test / override hook: {@code cursor} must still be a {@link CursorCliAdapter} instance
     * (never Functional).
     */
    public static ProductionRunResult run(
            ProductionRunRequest request, ProcessInvoker invoker, CursorCliAdapter cursor)
            throws IOException {
        return execute(request, invoker, cursor, false);
    }

    /** Resume from last complete stage boundary recorded in {@code .story/<id>/run/}. */
    public static ProductionRunResult resume(
            ProductionRunRequest request, ProcessInvoker invoker, CursorCliAdapter cursor)
            throws IOException {
        return execute(request, invoker, cursor, true);
    }

    private static ProductionRunResult execute(
            ProductionRunRequest request,
            ProcessInvoker invoker,
            CursorCliAdapter cursor,
            boolean productionResume) throws IOException {
        if (request == null) {
            throw new StageGateException("ProductionRunRequest required");
        }
        if (invoker == null) {
            throw new StageGateException("ProcessInvoker required");
        }
        if (cursor == null) {
            throw new StageGateException("CursorCliAdapter required");
        }
        RunLedger ledger = RunLedger.open(request.workspace, request.storyId);
        if (productionResume) {
            ledger.requireConsistentForResume();
        } else {
            validateWorkspaceGates(request.workspace.toAbsolutePath().normalize(), request, invoker);
            ledger.beginRun(joinScopes(request.writeScope), request.maxDevelopmentRounds);
        }
        PathwayRunner.Config config = strictConfigBuilder(request, cursor)
                .runLedger(ledger)
                .productionResume(productionResume)
                .build();
        assertStrict(config);
        try {
            PathwayResult pathway = PathwayRunner.run(config, invoker);
            return ProductionRunResult.fromPathway(
                    pathway, request.maxDevelopmentRounds, ledger.directory());
        } catch (PackageRefuseException e) {
            return settleStopped(
                    request, ledger, ProductionTerminal.FAILED_POLICY, e.getMessage());
        } catch (StageGateException e) {
            ProductionTerminal terminal = ProductionTerminal.fromStageGateMessage(e.getMessage());
            return settleStopped(request, ledger, terminal, e.getMessage());
        }
    }

    private static ProductionRunResult settleStopped(
            ProductionRunRequest request,
            RunLedger ledger,
            ProductionTerminal terminal,
            String detail) throws IOException {
        StoryWorkflowState state = null;
        try {
            state = StoryWorkflowMachine.load(request.workspace, request.storyId);
        } catch (Exception ignored) {
            // best-effort
        }
        RunLedger.RunStateSnapshot snap = ledger.readState();
        if (Strings.isBlank(snap.terminalOrNull)) {
            ledger.markTerminal(terminal, detail);
        }
        return ProductionRunResult.stopped(
                request.storyId,
                terminal,
                request.maxDevelopmentRounds,
                detail,
                ledger.directory(),
                state);
    }

    /** Visible for strict-config unit tests — does not run the pathway. */
    public static PathwayRunner.Config buildStrictConfig(
            ProductionRunRequest request, CursorCliAdapter cursor) {
        PathwayRunner.Config config = strictConfigBuilder(request, cursor).build();
        assertStrict(config);
        return config;
    }

    static PathwayRunner.Config.Builder strictConfigBuilder(
            ProductionRunRequest request, CursorCliAdapter cursor) {
        if (request == null) {
            throw new StageGateException("ProductionRunRequest required");
        }
        if (cursor == null) {
            throw new StageGateException("CursorCliAdapter required");
        }
        requireCursorOnly(cursor, "analysis/planning/development/review");

        Path workspace = request.workspace.toAbsolutePath().normalize();
        List<String> writeScope = OperatorWriteScope.normalizeAndValidate(request.writeScope);

        PathwayRunner.Config.Builder b = PathwayRunner.Config.builder(workspace, request.storyId)
                .script(Script.V3)
                .suite("B")
                .analysisAdapter(cursor)
                .planAdapter(cursor)
                .devAdapter(cursor)
                .reviewAdapter(cursor)
                .assumablePolicy(AssumablePolicy.REQUIRE_ACK)
                .deliveryMode(DeliveryMode.LOCAL_COMMIT)
                .lifecycleMode(LifecycleMode.SKIP)
                .approvalMode(PathwayRunner.ApprovalMode.LOW_RISK_AUTO)
                .planHumanOwned(true)
                .planApprover("operator-write-scope")
                .approvalNote("production: Plan Allowed ⊆ operator writeScope")
                .verifyFromEntriesOnly()
                .boundedDeliveryLoop(request.maxDevelopmentRounds)
                .commitMessage("ai4se(production): " + request.storyId)
                .adapterTimeout(request.adapterTimeout)
                .roleModels(request.roleModels);

        for (String scope : writeScope) {
            b.allowedFile(scope);
        }
        if (request.seedRequirement != null) {
            b.seedPath(request.seedRequirement.toAbsolutePath().normalize());
        }
        return b;
    }

    private static String joinScopes(List<String> scopes) {
        if (scopes == null || scopes.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < scopes.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(scopes.get(i));
        }
        return sb.toString();
    }

    /** Read-only status for {@code ai4se status}. */
    public static String formatStatus(Path workspace, String storyId) throws IOException {
        Path runDir = RunLedger.runDir(workspace, storyId);
        StringBuilder sb = new StringBuilder();
        sb.append("story=").append(storyId).append('\n');
        sb.append("runDir=").append(runDir.toAbsolutePath().normalize()).append('\n');
        if (!Files.isDirectory(runDir)) {
            sb.append("run=absent\n");
            return sb.toString();
        }
        RunLedger ledger = RunLedger.open(workspace, storyId);
        RunLedger.RunStateSnapshot snap = ledger.readState();
        sb.append("stage=").append(nullToDash(snap.stageOrNull)).append('\n');
        sb.append("status=").append(nullToDash(snap.statusOrNull)).append('\n');
        sb.append("terminal=").append(nullToDash(snap.terminalOrNull)).append('\n');
        sb.append("lastEventSequence=").append(snap.lastEventSequence).append('\n');
        sb.append("failureFingerprint=")
                .append(nullToDash(snap.failureFingerprintOrNull)).append('\n');
        try {
            StoryWorkflowState wf = StoryWorkflowMachine.load(workspace, storyId);
            sb.append("workflow=").append(wf.stage()).append('/').append(wf.status()).append('\n');
        } catch (Exception e) {
            sb.append("workflow=unavailable\n");
        }
        return sb.toString();
    }

    private static String nullToDash(String s) {
        return Strings.isBlank(s) ? "-" : s;
    }

    static void assertStrict(PathwayRunner.Config config) {
        if (config.devMutation != null) {
            throw new StageGateException("ProductionPathway refuses DevMutation");
        }
        if (config.allowReviewFixture) {
            throw new StageGateException("ProductionPathway refuses allowReviewFixture");
        }
        if (config.lifecycleMode != LifecycleMode.SKIP) {
            throw new StageGateException("ProductionPathway requires LifecycleMode.SKIP");
        }
        if (config.deliveryMode != DeliveryMode.LOCAL_COMMIT) {
            throw new StageGateException("ProductionPathway requires LOCAL_COMMIT");
        }
        if (config.assumablePolicy != AssumablePolicy.REQUIRE_ACK) {
            throw new StageGateException("ProductionPathway requires AssumablePolicy.REQUIRE_ACK");
        }
        requireAllRoleAdapters(config);
        requireCursorOnly(config.analysisAdapter, "analysis");
        requireCursorOnly(config.planAdapter, "planning");
        requireCursorOnly(config.devAdapter, "development");
        requireCursorOnly(config.reviewAdapter, "review");
    }

    static void requireAllRoleAdapters(PathwayRunner.Config config) {
        if (config.analysisAdapter == null
                || config.planAdapter == null
                || config.devAdapter == null
                || config.reviewAdapter == null) {
            throw new StageGateException(
                    "ProductionPathway requires Analysis, Planning, Development, and Review adapters");
        }
    }

    static void requireCursorOnly(ModelCliAdapter adapter, String role) {
        if (adapter == null) {
            throw new StageGateException("ProductionPathway missing adapter for " + role);
        }
        if (adapter instanceof FunctionalModelCliAdapter) {
            throw new StageGateException(
                    "ProductionPathway refuses FunctionalModelCliAdapter for " + role);
        }
        if (!(adapter instanceof CursorCliAdapter)) {
            throw new StageGateException(
                    "ProductionPathway requires CursorCliAdapter for " + role
                            + ", got " + adapter.getClass().getName());
        }
    }

    static void validateWorkspaceGates(
            Path workspace, ProductionRunRequest request, ProcessInvoker invoker) throws IOException {
        if (!Files.isDirectory(workspace)) {
            throw new StageGateException("workspace not found: " + workspace);
        }
        requireGitHead(workspace, invoker);

        List<String> dirty = WorkspaceGit.productionCleanGateDirtyPaths(workspace, invoker);
        if (!dirty.isEmpty()) {
            throw new StageGateException(
                    "Refuse dirty worktree — commit or stash changes first "
                            + "(including .ai4se/.story control files; only build output ignored): "
                            + dirty);
        }

        Path entries = workspace.resolve(".ai4se/repository/entries.yaml");
        if (!Files.isRegularFile(entries)) {
            throw new StageGateException("Missing .ai4se/repository/entries.yaml");
        }
        List<String> tests = VerificationEntries.readUsableTestCommands(workspace);
        if (tests.isEmpty()) {
            throw new StageGateException("No usable test entries in entries.yaml");
        }

        Path storyReq = workspace.resolve(".story").resolve(request.storyId).resolve("requirement.md");
        if (Files.isRegularFile(storyReq)) {
            requireJudgableAcceptance(storyReq);
        } else if (request.seedRequirement != null) {
            if (!Files.isRegularFile(request.seedRequirement)) {
                throw new StageGateException(
                        "seedRequirement not found: " + request.seedRequirement);
            }
            requireJudgableAcceptance(request.seedRequirement);
        } else {
            throw new StageGateException(
                    "Story not open and seedRequirement missing: " + request.storyId);
        }
    }

    private static void requireGitHead(Path workspace, ProcessInvoker invoker) throws IOException {
        try {
            WorkspaceGit.headSha(workspace, invoker);
        } catch (StageGateException e) {
            throw new StageGateException(
                    "workspace must be a Git repository with at least one commit: " + e.getMessage());
        }
    }

    static void requireJudgableAcceptance(Path requirementMd) throws IOException {
        String text = new String(Files.readAllBytes(requirementMd), StandardCharsets.UTF_8);
        // Same heading aliases as StoryRequirementReader (Acceptance / criteria / criterion).
        java.util.Map<String, String> sections = StoryRequirementReader.parseSections(text);
        String body = sections.get("acceptance");
        if (Strings.isBlank(body)) {
            throw new StageGateException(
                    "requirement must include ## Acceptance (or limited aliases) with judgable "
                            + "criteria: " + requirementMd);
        }
        List<String> items = StoryRequirementReader.parseAcceptanceLines(body);
        if (items.isEmpty() || !ACCEPTANCE_ITEM.matcher(body).find()) {
            throw new StageGateException(
                    "requirement ## Acceptance must contain at least one concrete bullet "
                            + "or numbered criterion: " + requirementMd);
        }
        String trimmedBody = body.trim().toLowerCase(Locale.ROOT);
        if (trimmedBody.contains("可检验的通过条件") && !trimmedBody.contains("- ")
                && !trimmedBody.contains("* ")) {
            throw new StageGateException("requirement acceptance is still a template placeholder");
        }
        if (trimmedBody.replaceAll("\\s+", "").contains("-（可检验的通过条件）")
                || trimmedBody.replaceAll("\\s+", "").contains("-(可检验的通过条件)")) {
            throw new StageGateException("requirement acceptance is still a template placeholder");
        }
        // Usable vs placeholder is enforced later by AcceptanceGate (PackageRefuse → FAILED_POLICY).
    }
}
