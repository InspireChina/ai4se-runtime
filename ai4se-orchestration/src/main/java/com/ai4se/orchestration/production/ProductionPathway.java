package com.ai4se.orchestration.production;

import com.ai4se.context.packagebuild.PackageRefuseException;
import com.ai4se.context.story.StoryRequirementReader;
import com.ai4se.context.workspace.WorkspaceSlotException;
import com.ai4se.execution.api.ModelCliAdapter;
import com.ai4se.execution.model.RoleModelConfig;
import com.ai4se.execution.support.FunctionalModelCliAdapter;
import com.ai4se.execution.support.ProcessInvoker;
import com.ai4se.orchestration.analysis.StageGateException;
import com.ai4se.orchestration.lifecycle.OnboardPolicy;
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
import com.ai4se.orchestration.verification.AcceptanceProbeSet;
import com.ai4se.orchestration.workflow.StoryWorkflowMachine;
import com.ai4se.orchestration.workflow.StoryWorkflowState;
import com.ai4se.orchestration.workflow.WorkflowStatus;
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
 * <p>Injects one explicitly registered real CLI adapter for Analysis / Planning / Development /
 * Review. Refuses Functional adapters, unknown implementations, DevMutation, review fixtures,
 * and seeded V4 knobs.
 */
public final class ProductionPathway {

    /** Bullet {@code -}/{@code *} or numbered {@code 1.}/{@code 1)} — prose is not Acceptance. */
    private static final Pattern ACCEPTANCE_ITEM =
            Pattern.compile("(?m)^\\s*(?:[-*]|\\d+[.)])\\s+\\S+");

    private ProductionPathway() {
    }

    public static ProductionRunResult run(ProductionRunRequest request, ProcessInvoker invoker)
            throws IOException {
        return run(request, invoker, ProductionAdapterRegistry.create("cursor", invoker, null));
    }

    /**
     * Test / override hook: {@code cursor} must still be a {@link CursorCliAdapter} instance
     * (never Functional).
     */
    public static ProductionRunResult run(
            ProductionRunRequest request, ProcessInvoker invoker, ModelCliAdapter cursor)
            throws IOException {
        return execute(request, invoker, cursor, false);
    }

    /** Resume from last complete stage boundary recorded in {@code .story/<id>/run/}. */
    public static ProductionRunResult resume(
            ProductionRunRequest request, ProcessInvoker invoker, ModelCliAdapter cursor)
            throws IOException {
        return execute(request, invoker, cursor, true);
    }

    private static ProductionRunResult execute(
            ProductionRunRequest request,
            ProcessInvoker invoker,
            ModelCliAdapter cursor,
            boolean productionResume) throws IOException {
        if (request == null) {
            throw new StageGateException("ProductionRunRequest required");
        }
        if (invoker == null) {
            throw new StageGateException("ProcessInvoker required");
        }
        if (cursor == null) {
            throw new StageGateException("registered production Adapter required");
        }
        // Complete all read-only workspace/onboard/requirement gates before opening the ledger.
        // RunLedger.open creates .story/<id>/run, so opening it before a failed preflight would
        // dirty an otherwise clean worktree and make the next invocation fail its clean gate.
        if (!productionResume) {
            validateWorkspaceGates(request.workspace.toAbsolutePath().normalize(), request, invoker);
        }
        RunLedger ledger = RunLedger.open(request.workspace, request.storyId);
        if (productionResume) {
            ledger.requireConsistentForResume();
            try {
                validateResumeAdapter(ledger, cursor);
            } catch (StageGateException e) {
                return settleStopped(request, ledger, ProductionTerminal.FAILED_POLICY, e.getMessage());
            }
        } else {
            ledger.beginRun(
                    joinScopes(request.writeScope),
                    request.maxDevelopmentRounds,
                    cursor.name(),
                    ledgerModel(request.roleModels),
                    ledgerModelSelection(request.roleModels));
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
        return settleStoppedInternal(request, ledger, terminal, detail);
    }

    /** Test seam for ledger/workflow consistency. */
    static ProductionRunResult settleStoppedForTest(
            ProductionRunRequest request,
            RunLedger ledger,
            ProductionTerminal terminal,
            String detail) throws IOException {
        return settleStoppedInternal(request, ledger, terminal, detail);
    }

    private static ProductionRunResult settleStoppedInternal(
            ProductionRunRequest request,
            RunLedger ledger,
            ProductionTerminal terminal,
            String detail) throws IOException {
        StoryWorkflowState state = null;
        try {
            state = StoryWorkflowMachine.load(request.workspace, request.storyId);
            if (state.status() == WorkflowStatus.RUNNING) {
                String reason = "production settle " + terminal.name()
                        + (Strings.isBlank(detail) ? "" : ": " + detail.trim());
                if (reason.length() > 500) {
                    reason = reason.substring(0, 500);
                }
                state = StoryWorkflowMachine.stop(request.workspace, request.storyId, reason);
            }
        } catch (Exception ignored) {
            // best-effort: ledger settle must not fail because workflow sync failed
            try {
                state = StoryWorkflowMachine.load(request.workspace, request.storyId);
            } catch (Exception ignored2) {
                // leave null
            }
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
            ProductionRunRequest request, ModelCliAdapter cursor) {
        PathwayRunner.Config config = strictConfigBuilder(request, cursor).build();
        assertStrict(config);
        return config;
    }

    static PathwayRunner.Config.Builder strictConfigBuilder(
            ProductionRunRequest request, ModelCliAdapter cursor) {
        if (request == null) {
            throw new StageGateException("ProductionRunRequest required");
        }
        if (cursor == null) {
            throw new StageGateException("registered production Adapter required");
        }
        requireRegisteredProductionAdapter(cursor, "analysis/planning/development/review");

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
                .requireAcceptanceProofs(true)
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
        sb.append("adapter=").append(nullToDash(snap.adapterOrNull)).append('\n');
        sb.append("adapter_provenance=")
                .append(nullToDash(snap.adapterProvenanceOrNull)).append('\n');
        sb.append("model=").append(nullToDash(snap.modelOrNull)).append('\n');
        sb.append("model_selection=").append(nullToDash(snap.modelSelectionOrNull)).append('\n');
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

    private static String ledgerModel(RoleModelConfig models) {
        if (models == null || models.isEmpty()) {
            return "(cli-default)";
        }
        if (!Strings.isBlank(models.defaultModel())) {
            return models.defaultModel();
        }
        return "(role-resolved)";
    }

    private static String ledgerModelSelection(RoleModelConfig models) {
        if (models == null || models.isEmpty()) {
            return "cli-default";
        }
        if (!Strings.isBlank(models.defaultModel())) {
            return "explicit";
        }
        return "role-resolved";
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
        requireRegisteredProductionAdapter(config.analysisAdapter, "analysis");
        requireRegisteredProductionAdapter(config.planAdapter, "planning");
        requireRegisteredProductionAdapter(config.devAdapter, "development");
        requireRegisteredProductionAdapter(config.reviewAdapter, "review");
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
        requireRegisteredProductionAdapter(adapter, role);
    }

    static void requireRegisteredProductionAdapter(ModelCliAdapter adapter, String role) {
        if (adapter == null) {
            throw new StageGateException("ProductionPathway missing adapter for " + role);
        }
        if (adapter instanceof FunctionalModelCliAdapter) {
            throw new StageGateException(
                    "ProductionPathway refuses FunctionalModelCliAdapter for " + role);
        }
        if (!ProductionAdapterRegistry.isRegistered(adapter)) {
            throw new StageGateException(
                    "ProductionPathway requires a registered production Adapter for " + role
                            + ", got " + adapter.getClass().getName());
        }
    }

    /**
     * Enforce adapter continuity across a production resume. New ledgers are pinned; ledgers
     * created before adapter persistence remain resumable but are explicitly marked legacy.
     */
    static void validateResumeAdapter(RunLedger ledger, ModelCliAdapter requested)
            throws IOException {
        if (ledger == null || requested == null) {
            throw new StageGateException("FAILED_POLICY: resume requires a production Adapter");
        }
        RunLedger.RunStateSnapshot snap = ledger.readState();
        if (!Strings.isBlank(snap.adapterOrNull)) {
            if (!snap.adapterOrNull.trim().equals(requested.name())) {
                throw new StageGateException(
                        "FAILED_POLICY: resume Adapter mismatch; ledger="
                                + snap.adapterOrNull.trim() + " requested=" + requested.name());
            }
            return;
        }
        ledger.markLegacyAdapterProvenance();
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
        // Validate every onboard slot before RunLedger.open can create .story/<id>/run.
        try {
            OnboardPolicy.requireSlotsAlreadyPresent(workspace);
        } catch (WorkspaceSlotException e) {
            throw new StageGateException("Onboard slots invalid: " + e.getMessage());
        }
        AcceptanceProbeSet.requireFrozenPreflight(workspace, request.storyId);
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
