package com.ai4se.runtime.demo.host;

import com.ai4se.context.discovery.DiscoveryCandidateReader;
import com.ai4se.context.discovery.DiscoveryPackageBuilder;
import com.ai4se.context.packagebuild.ContextPackageResult;
import com.ai4se.context.packagebuild.PackageBudget;
import com.ai4se.context.packagebuild.SpecificationPackageBuilder;
import com.ai4se.execution.support.ProcessInvoker;
import com.ai4se.orchestration.analysis.StageGateException;
import com.ai4se.orchestration.specification.SpecificationRecords;
import com.ai4se.orchestration.support.WorkspaceGit;
import com.ai4se.runtime.common.util.Strings;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

/**
 * Host-neutral handoff between a customer's already-open model tool and the AI4SE control plane.
 *
 * <p>The bridge deliberately does not invoke a model. It prepares a bounded package, lets the
 * current host model write only candidate output, then validates that output before the existing
 * human approval/freeze flow can continue. This keeps interactive host sessions and unattended
 * CLI adapters separate without duplicating delivery policy in a host skill.
 */
public final class HostBridge {

    private static final String HOST_AUDIT_DIR = "host";

    private HostBridge() {
    }

    public static Prepared prepareDiscovery(Path workspace, String candidateId, String scope)
            throws IOException {
        requireWorkspace(workspace);
        requireRepositoryCleanForDiscovery(workspace);
        String id = DiscoveryPackageBuilder.normalizeCandidateId(candidateId);
        String sourceCommit = WorkspaceGit.headSha(workspace, new ProcessInvoker.RealProcessInvoker());
        ContextPackageResult pkg = DiscoveryPackageBuilder.build(
                workspace, id, requireScope(scope), sourceCommit, PackageBudget.PRODUCTION_P1);
        appendAudit(workspace.resolve(DiscoveryPackageBuilder.candidateRoot(workspace, id)),
                "prepare-discovery.md", "stage=DISCOVERY\nsource_commit=" + sourceCommit + "\n"
                        + "host=terminal-host\npackage=" + pkg.packageDir() + "\n"
                        + "candidate_only_write_scope=1\n");
        return new Prepared("DISCOVERY", id, pkg.packageDir(), pkg.manifestPath(), sourceCommit,
                ".ai4se/knowledge-candidates/" + id + "/candidate.yaml and documents/*.md");
    }

    public static Submitted submitDiscovery(Path workspace, String candidateId, String scope)
            throws IOException {
        requireWorkspace(workspace);
        String id = DiscoveryPackageBuilder.normalizeCandidateId(candidateId);
        Path root = DiscoveryPackageBuilder.candidateRoot(workspace, id);
        Path seed = root.resolve("package/slices/discovery-seed.properties");
        if (!Files.isRegularFile(seed)) {
            throw new StageGateException("Host Discovery submit requires a Bridge-prepared package: " + seed);
        }
        String expectedCommit = property(seed, "source_commit");
        if (Strings.isBlank(expectedCommit)) {
            throw new StageGateException("Bridge Discovery package missing source_commit");
        }
        String observedCommit = WorkspaceGit.headSha(workspace, new ProcessInvoker.RealProcessInvoker());
        if (!expectedCommit.equals(observedCommit)) {
            throw new StageGateException("Host Discovery changed HEAD — restart from a clean workspace");
        }
        requireOnlyDiscoveryCandidateAndHostInstall(workspace, root);
        DiscoveryCandidateReader.Candidate candidate = DiscoveryCandidateReader.readAndValidate(
                workspace, id, requireScope(scope), expectedCommit);
        appendAudit(root, "submit-discovery.md", "stage=DISCOVERY\nsource_commit=" + observedCommit + "\n"
                + "host=terminal-host\nvalidated=1\ndocuments=" + candidate.documents().size() + "\n"
                + "next=HUMAN_KNOWLEDGE_APPROVAL\n");
        return new Submitted("DISCOVERY", id, root, candidate.documents().size(),
                "HUMAN_KNOWLEDGE_APPROVAL");
    }

    public static Prepared prepareSpecification(Path workspace, String storyId) throws IOException {
        requireWorkspace(workspace);
        requireCurrentStoryAndHostOnly(workspace, storyId);
        String sourceCommit = WorkspaceGit.headSha(workspace, new ProcessInvoker.RealProcessInvoker());
        ContextPackageResult pkg = SpecificationPackageBuilder.build(
                workspace, storyId, PackageBudget.PRODUCTION_P1);
        Path root = workspace.resolve(".story").resolve(storyId).resolve(HOST_AUDIT_DIR);
        appendAudit(root, "prepare-specification.md", "stage=SPECIFICATION\nsource_commit=" + sourceCommit
                + "\nhost=terminal-host\npackage=" + pkg.packageDir() + "\n"
                + "candidate_only_write_scope=.story/" + storyId + "/specification/\n");
        return new Prepared("SPECIFICATION", storyId, pkg.packageDir(), pkg.manifestPath(), sourceCommit,
                ".story/" + storyId + "/specification/{specification.result.properties,candidate-requirement.md|clarification.questions.md}");
    }

    public static Submitted submitSpecification(Path workspace, String storyId) throws IOException {
        requireWorkspace(workspace);
        Path root = workspace.resolve(".story").resolve(storyId).resolve(HOST_AUDIT_DIR);
        Path prepared = root.resolve("prepare-specification.md");
        if (!Files.isRegularFile(prepared)) {
            throw new StageGateException("Host Specification submit requires Bridge prepare first: " + prepared);
        }
        String expectedCommit = property(prepared, "source_commit");
        String observedCommit = WorkspaceGit.headSha(workspace, new ProcessInvoker.RealProcessInvoker());
        if (Strings.isBlank(expectedCommit) || !expectedCommit.equals(observedCommit)) {
            throw new StageGateException("Host Specification changed HEAD — restart from a clean workspace");
        }
        requireCurrentStoryAndHostOnly(workspace, storyId);
        SpecificationRecords.Outcome outcome = SpecificationRecords.requireOutcome(workspace, storyId);
        appendAudit(root, "submit-specification.md", "stage=SPECIFICATION\nsource_commit=" + observedCommit
                + "\nhost=terminal-host\nvalidated=1\noutcome=" + outcome.name() + "\nnext="
                + (outcome == SpecificationRecords.Outcome.CANDIDATE
                        ? "HUMAN_SPEC_FREEZE" : "HUMAN_SPEC_CLARIFICATION") + "\n");
        return new Submitted("SPECIFICATION", storyId, root, 1,
                outcome == SpecificationRecords.Outcome.CANDIDATE
                        ? "HUMAN_SPEC_FREEZE" : "HUMAN_SPEC_CLARIFICATION");
    }

    private static void requireWorkspace(Path workspace) {
        if (workspace == null || !Files.isDirectory(workspace)) {
            throw new StageGateException("Host Bridge requires an existing customer workspace");
        }
    }

    private static String requireScope(String scope) {
        if (Strings.isBlank(scope)) {
            throw new StageGateException("Host Discovery scope required (repository or module:<id>)");
        }
        return scope.trim();
    }

    private static void requireRepositoryCleanForDiscovery(Path workspace) throws IOException {
        List<String> dirty = WorkspaceGit.productionCleanGateDirtyPathsAllowCompletedStories(
                workspace, new ProcessInvoker.RealProcessInvoker());
        for (String path : dirty) {
            if (!HostProfileInstaller.isManagedArtifactPath(workspace, path)) {
                throw new StageGateException("Host Discovery requires clean customer workspace except installed "
                        + "host profile; found: " + dirty);
            }
        }
    }

    private static void requireOnlyDiscoveryCandidateAndHostInstall(Path workspace, Path candidateRoot)
            throws IOException {
        String candidatePrefix = workspace.relativize(candidateRoot).toString().replace('\\', '/') + "/";
        List<String> dirty = WorkspaceGit.productionCleanGateDirtyPathsAllowCompletedStories(
                workspace, new ProcessInvoker.RealProcessInvoker());
        for (String path : dirty) {
            String normalized = path.replace('\\', '/');
            if (!normalized.startsWith(candidatePrefix)
                    && !HostProfileInstaller.isManagedArtifactPath(workspace, normalized)) {
                throw new StageGateException("Host Discovery write-scope violation: " + normalized);
            }
        }
    }

    private static void requireCurrentStoryAndHostOnly(Path workspace, String storyId) throws IOException {
        if (Strings.isBlank(storyId)) {
            throw new StageGateException("story id required");
        }
        List<String> dirty = WorkspaceGit.productionCleanGateDirtyPathsForStory(
                workspace, storyId, new ProcessInvoker.RealProcessInvoker());
        for (String path : dirty) {
            if (!HostProfileInstaller.isManagedArtifactPath(workspace, path)) {
                throw new StageGateException("Host Specification requires only current Story artifacts; found: "
                        + dirty);
            }
        }
    }

    private static String property(Path path, String key) throws IOException {
        for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
            if (line.startsWith(key + "=")) {
                return line.substring(key.length() + 1).trim();
            }
        }
        return "";
    }

    private static void appendAudit(Path root, String fileName, String content) throws IOException {
        Files.createDirectories(root);
        String body = "# AI4SE Host Bridge\n\n- at: " + Instant.now().toString() + "\n\n```properties\n"
                + content + "```\n";
        Files.write(root.resolve(fileName), body.getBytes(StandardCharsets.UTF_8));
    }

    /** Prepared bounded package, returned to a host command without leaking repository contents. */
    public static final class Prepared {
        private final String stage;
        private final String subject;
        private final Path packageDir;
        private final Path manifest;
        private final String sourceCommit;
        private final String outputAllowed;

        Prepared(String stage, String subject, Path packageDir, Path manifest, String sourceCommit,
                String outputAllowed) {
            this.stage = stage;
            this.subject = subject;
            this.packageDir = packageDir;
            this.manifest = manifest;
            this.sourceCommit = sourceCommit;
            this.outputAllowed = outputAllowed;
        }

        public String stage() { return stage; }
        public String subject() { return subject; }
        public Path packageDir() { return packageDir; }
        public Path manifest() { return manifest; }
        public String sourceCommit() { return sourceCommit; }
        public String outputAllowed() { return outputAllowed; }
    }

    /** Validated candidate handoff; a human decision is still required. */
    public static final class Submitted {
        private final String stage;
        private final String subject;
        private final Path evidenceRoot;
        private final int artifactCount;
        private final String next;

        Submitted(String stage, String subject, Path evidenceRoot, int artifactCount, String next) {
            this.stage = stage;
            this.subject = subject;
            this.evidenceRoot = evidenceRoot;
            this.artifactCount = artifactCount;
            this.next = next;
        }

        public String stage() { return stage; }
        public String subject() { return subject; }
        public Path evidenceRoot() { return evidenceRoot; }
        public int artifactCount() { return artifactCount; }
        public String next() { return next; }
    }
}
