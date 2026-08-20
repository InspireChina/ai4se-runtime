package com.ai4se.orchestration.discovery;

import com.ai4se.context.discovery.DiscoveryCandidateReader;
import com.ai4se.context.discovery.DiscoveryPackageBuilder;
import com.ai4se.context.packagebuild.ContextPackageResult;
import com.ai4se.context.packagebuild.PackageBudget;
import com.ai4se.execution.api.AdapterResult;
import com.ai4se.execution.api.ModelCliAdapter;
import com.ai4se.execution.cursor.PackageAdapterSubmission;
import com.ai4se.execution.support.ProcessInvoker;
import com.ai4se.orchestration.analysis.StageGateException;
import com.ai4se.orchestration.support.WorkspaceGit;
import com.ai4se.runtime.common.util.Strings;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One bounded semantic-discovery turn for an already onboarded customer repository.
 *
 * <p>The deterministic onboard facts are the input. A model may only create a candidate under
 * {@code .ai4se/knowledge-candidates/<id>/}; it cannot directly change source, verified
 * knowledge, the index, or a Story. Control validates both the write boundary and the candidate
 * evidence contract before exposing it for a human approval decision.
 */
public final class DiscoveryAdapterExecution {

    private DiscoveryAdapterExecution() {
    }

    public static Outcome submit(
            Path workspace,
            String candidateId,
            String scope,
            ModelCliAdapter adapter,
            ProcessInvoker invoker,
            Duration timeout) throws IOException {
        if (workspace == null || adapter == null || invoker == null) {
            throw new StageGateException("Discovery requires workspace, adapter and process invoker");
        }
        if (Strings.isBlank(scope)) {
            throw new StageGateException("Discovery scope required (repository or module:<id>)");
        }
        String id = DiscoveryPackageBuilder.normalizeCandidateId(candidateId);
        requireCleanBeforeStart(workspace, invoker);
        String sourceCommit = WorkspaceGit.headSha(workspace, invoker);
        ContextPackageResult pkg = DiscoveryPackageBuilder.build(
                workspace, id, scope.trim(), sourceCommit, PackageBudget.PRODUCTION_P1);
        Path root = DiscoveryPackageBuilder.candidateRoot(workspace, id);
        requireOnlyCandidateChanges(workspace, root, invoker);

        Map<String, String> env = new LinkedHashMap<String, String>();
        env.put("AI4SE_ROLE", DiscoveryPackageBuilder.ROLE);
        env.put("AI4SE_DISCOVERY_SCOPE", scope.trim());
        env.put("AI4SE_DISCOVERY_CANDIDATE", id);
        AdapterResult result = PackageAdapterSubmission.submit(
                adapter,
                workspace,
                pkg,
                timeout == null ? Duration.ofMinutes(15) : timeout,
                env,
                null);
        writeAudit(root, adapter.name(), pkg.packageDir(), result);
        if (!sourceCommit.equals(WorkspaceGit.headSha(workspace, invoker))) {
            throw new StageGateException("Discovery changed HEAD — model-side git commits are forbidden");
        }
        requireOnlyCandidateChanges(workspace, root, invoker);
        if (result.hasNextStageHint()) {
            throw new StageGateException("Discovery adapter must not decide workflow stages or retries");
        }
        if (!result.success()) {
            throw new StageGateException("Discovery adapter failed: "
                    + (Strings.isBlank(result.message()) ? "exit=" + result.exitCode() : result.message()));
        }
        DiscoveryCandidateReader.Candidate candidate = DiscoveryCandidateReader.readAndValidate(
                workspace, id, scope.trim(), sourceCommit);
        return new Outcome(id, scope.trim(), sourceCommit, root, candidate.documents().size());
    }

    private static void requireCleanBeforeStart(Path workspace, ProcessInvoker invoker) throws IOException {
        List<String> dirty = WorkspaceGit.productionCleanGateDirtyPaths(workspace, invoker);
        if (!dirty.isEmpty()) {
            throw new StageGateException(
                    "Discovery requires clean customer worktree; commit/revert unrelated changes first: " + dirty);
        }
    }

    private static void requireOnlyCandidateChanges(Path workspace, Path root, ProcessInvoker invoker)
            throws IOException {
        String allowedPrefix = workspace.relativize(root).toString().replace('\\', '/') + "/";
        for (String changed : WorkspaceGit.productionCleanGateDirtyPaths(workspace, invoker)) {
            String normalized = changed.replace('\\', '/');
            if (!normalized.startsWith(allowedPrefix)) {
                throw new StageGateException(
                        "Discovery write-scope violation: " + normalized
                                + " (only " + allowedPrefix + " is allowed)");
            }
        }
    }

    private static void writeAudit(Path root, String adapter, Path pkg, AdapterResult result) throws IOException {
        String body = "# Discovery adapter submission\n\n"
                + "- adapter: " + adapter + "\n"
                + "- model: " + result.details().get("model") + "\n"
                + "- role: Discovery\n"
                + "- package: " + pkg + "\n"
                + "- success: " + result.success() + "\n"
                + "- exit_code: " + result.exitCode() + "\n"
                + "- candidate_only_write_scope: true\n";
        Files.write(root.resolve("adapter-discovery.md"), body.getBytes(StandardCharsets.UTF_8));
    }

    /** Immutable summary printed by CLI and suitable for approval evidence. */
    public static final class Outcome {
        private final String candidateId;
        private final String scope;
        private final String sourceCommit;
        private final Path candidateRoot;
        private final int documentCount;

        Outcome(String candidateId, String scope, String sourceCommit, Path candidateRoot, int documentCount) {
            this.candidateId = candidateId;
            this.scope = scope;
            this.sourceCommit = sourceCommit;
            this.candidateRoot = candidateRoot;
            this.documentCount = documentCount;
        }

        public String candidateId() { return candidateId; }
        public String scope() { return scope; }
        public String sourceCommit() { return sourceCommit; }
        public Path candidateRoot() { return candidateRoot; }
        public int documentCount() { return documentCount; }
    }
}
