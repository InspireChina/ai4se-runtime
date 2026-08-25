package com.ai4se.orchestration.lifecycle;

import com.ai4se.context.discovery.DiscoveryCandidateReader;
import com.ai4se.orchestration.acceptance.HumanAcceptanceRecords;
import com.ai4se.orchestration.analysis.StageGateException;
import com.ai4se.orchestration.support.WorkspaceGit;
import com.ai4se.runtime.common.util.Strings;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * W10 · S6 Knowledge Lifecycle (minimal) — manage customer-repo knowledge only after human ACCEPTED.
 * Success: index/file change <b>or</b> explicit {@code lifecycle: noop} with reason.
 * Does not Repo Scan; does not invent business insights as Facts.
 */
public final class KnowledgeLifecycleControl {

    public static final String DIR = "lifecycle";
    public static final String NOOP_FILE = "noop.md";
    public static final String APPLIED_FILE = "applied.md";
    public static final String KNOWLEDGE_DIR = ".ai4se/knowledge";
    public static final String INDEX = ".ai4se/index/knowledge.yaml";

    private KnowledgeLifecycleControl() {
    }

    public static Path lifecycleDir(Path workspace, String storyId) {
        return workspace.resolve(".story").resolve(storyId).resolve(DIR);
    }

    /** True if any lifecycle artifact already exists for this Story. */
    public static boolean hasLifecycleArtifact(Path workspace, String storyId) {
        Path dir = lifecycleDir(workspace, storyId);
        return Files.isRegularFile(dir.resolve(NOOP_FILE))
                || Files.isRegularFile(dir.resolve(APPLIED_FILE));
    }

    /**
     * W10 gate: refuse to write 06 before human ACCEPTED.
     * Also used as negative必验 — calling apply/noop without acceptance throws.
     */
    public static void requireMayRun(Path workspace, String storyId) throws IOException {
        HumanAcceptanceRecords.requireAccepted(workspace, storyId);
    }

    /**
     * Explicit noop — no Knowledge/Learning change; reason required.
     */
    public static Path noop(Path workspace, String storyId, String reason) throws IOException {
        requireMayRun(workspace, storyId);
        if (Strings.isBlank(reason)) {
            throw new StageGateException("lifecycle noop requires reason");
        }
        Path dir = lifecycleDir(workspace, storyId);
        Files.createDirectories(dir);
        String body = ""
                + "# Knowledge Lifecycle\n\n"
                + "- lifecycle: noop\n"
                + "- story_id: " + storyId + "\n"
                + "- reason: " + reason.trim() + "\n"
                + "- at: " + Instant.now() + "\n"
                + "- repo_scan: false\n"
                + "- host: customer-repo only\n";
        Path path = dir.resolve(NOOP_FILE);
        Files.write(path, body.getBytes(StandardCharsets.UTF_8));
        return path;
    }

    /**
     * Minimal write-back: append a Learning event under {@code .ai4se/learning/} and index pointer.
     * Not AI insight-as-Facts; caller supplies the learning text (human or audited draft).
     */
    public static Path applyLearning(
            Path workspace, String storyId, String learningId, String learningBody)
            throws IOException {
        requireMayRun(workspace, storyId);
        HumanAcceptanceRecords.requireMayApplyLearning(workspace, storyId);
        if (Strings.isBlank(learningId) || Strings.isBlank(learningBody)) {
            throw new StageGateException("applyLearning requires learningId and body");
        }
        String id = learningId.trim().toLowerCase(Locale.ROOT).replace(' ', '-');
        Path learningDir = workspace.resolve(".ai4se").resolve("learning");
        Files.createDirectories(learningDir);
        Path learningFile = learningDir.resolve(id + ".md");
        String fileBody = ""
                + "# Learning: " + id + "\n\n"
                + "- story_id: " + storyId + "\n"
                + "- at: " + Instant.now() + "\n\n"
                + learningBody.trim() + "\n";
        Files.write(learningFile, fileBody.getBytes(StandardCharsets.UTF_8));

        Path index = workspace.resolve(".ai4se").resolve("index").resolve("knowledge.yaml");
        if (!Files.isRegularFile(index)) {
            throw new StageGateException("Missing .ai4se/index/knowledge.yaml — onboard slots first");
        }
        String entry = ""
                + "- id: " + id + "\n"
                + "  path: .ai4se/learning/" + id + ".md\n"
                + "  kind: learning\n"
                + "  tags: [story-" + storyId + "]\n"
                + "  refs: [.story/" + storyId + "]\n"
                + "  status: active\n";
        Files.write(index, entry.getBytes(StandardCharsets.UTF_8), StandardOpenOption.APPEND);

        Path dir = lifecycleDir(workspace, storyId);
        Files.createDirectories(dir);
        String applied = ""
                + "# Knowledge Lifecycle\n\n"
                + "- lifecycle: applied\n"
                + "- story_id: " + storyId + "\n"
                + "- learning_id: " + id + "\n"
                + "- path: .ai4se/learning/" + id + ".md\n"
                + "- index: .ai4se/index/knowledge.yaml\n"
                + "- at: " + Instant.now() + "\n"
                + "- repo_scan: false\n"
                + "- host: customer-repo only\n";
        Path path = dir.resolve(APPLIED_FILE);
        Files.write(path, applied.getBytes(StandardCharsets.UTF_8));
        return path;
    }

    public static void requireCompleted(Path workspace, String storyId) throws IOException {
        if (!hasLifecycleArtifact(workspace, storyId)) {
            throw new StageGateException(
                    "Knowledge Lifecycle missing — need applied change or lifecycle: noop");
        }
    }

    /**
     * Promotes a source-validated Discovery candidate after an explicit named human decision.
     * This is intentionally separate from post-delivery lifecycle: semantic repository knowledge
     * must exist before Stories, while delivery learning still requires customer acceptance.
     */
    public static List<Path> approveDiscoveryCandidate(
            Path workspace,
            String candidateId,
            String actor,
            com.ai4se.execution.support.ProcessInvoker invoker) throws IOException {
        if (workspace == null || invoker == null || Strings.isBlank(actor)) {
            throw new StageGateException("approve-knowledge requires workspace, actor and process invoker");
        }
        String head = WorkspaceGit.headSha(workspace, invoker);
        DiscoveryCandidateReader.Candidate candidate = DiscoveryCandidateReader.readAndValidate(
                workspace, candidateId, null, head);
        requireOnlyCandidateDirtyPaths(workspace, candidate.id(), invoker);
        Path index = workspace.resolve(INDEX);
        if (!Files.isRegularFile(index)) {
            throw new StageGateException("Missing " + INDEX + " — onboard slots first");
        }
        String indexText = new String(Files.readAllBytes(index), StandardCharsets.UTF_8);
        Path knowledgeDir = workspace.resolve(KNOWLEDGE_DIR);
        Files.createDirectories(knowledgeDir);
        List<Path> promoted = new ArrayList<Path>();
        StringBuilder entries = new StringBuilder();
        for (DiscoveryCandidateReader.Document doc : candidate.documents()) {
            Path target = knowledgeDir.resolve(doc.id() + ".md");
            if (Files.exists(target)) {
                throw new StageGateException("Knowledge id already exists; create a reviewed replacement candidate: "
                        + doc.id());
            }
            if (indexText.contains("- id: " + doc.id() + "\n")) {
                throw new StageGateException("Knowledge index already contains id: " + doc.id());
            }
        }
        for (DiscoveryCandidateReader.Document doc : candidate.documents()) {
            Path target = knowledgeDir.resolve(doc.id() + ".md");
            Path root = workspace.resolve(".ai4se/knowledge-candidates")
                    .resolve(candidate.id());
            Files.copy(root.resolve(doc.path()), target);
            String digest = DiscoveryCandidateReader.sourceDigest(workspace, doc.sourcePaths());
            entries.append("- id: ").append(doc.id()).append('\n')
                    .append("  path: ").append(KNOWLEDGE_DIR).append('/').append(doc.id()).append(".md\n")
                    .append("  kind: ").append(doc.kind()).append('\n')
                    .append("  tags: ").append(yamlList(doc.tags())).append('\n')
                    .append("  refs: ").append(yamlList(doc.refs())).append('\n')
                    .append("  source_paths: ").append(yamlList(doc.sourcePaths())).append('\n')
                    .append("  source_commit: ").append(candidate.sourceCommit()).append('\n')
                    .append("  source_sha256: ").append(digest).append('\n')
                    .append("  status: verified\n");
            promoted.add(target);
        }
        Files.write(index, entries.toString().getBytes(StandardCharsets.UTF_8), StandardOpenOption.APPEND);
        Path candidateRoot = workspace.resolve(".ai4se/knowledge-candidates").resolve(candidate.id());
        String approval = "# Discovery Knowledge Approval\n\n"
                + "- candidate_id: " + candidate.id() + "\n"
                + "- actor: " + actor.trim() + "\n"
                + "- source_commit: " + candidate.sourceCommit() + "\n"
                + "- approved_at: " + Instant.now() + "\n"
                + "- promoted_count: " + promoted.size() + "\n"
                + "- index: " + INDEX + "\n";
        Files.write(candidateRoot.resolve("approved.md"), approval.getBytes(StandardCharsets.UTF_8));
        return Collections.unmodifiableList(promoted);
    }

    private static void requireOnlyCandidateDirtyPaths(
            Path workspace, String candidateId, com.ai4se.execution.support.ProcessInvoker invoker)
            throws IOException {
        String prefix = ".ai4se/knowledge-candidates/" + candidateId + "/";
        for (String changed : WorkspaceGit.productionCleanGateDirtyPaths(workspace, invoker)) {
            if (!changed.replace('\\', '/').startsWith(prefix)) {
                throw new StageGateException(
                        "approve-knowledge refuses source/index changes after Discovery; only candidate may be dirty: "
                                + changed);
            }
        }
    }

    /**
     * Marks only verified knowledge whose explicit source paths overlap an accepted Delivery diff.
     * It never rewrites document bodies or creates new knowledge. The resulting index change is
     * deliberately visible for a human review/commit before another clean production run.
     */
    public static List<String> markStaleForChangedFiles(
            Path workspace, String storyId, List<String> changedFiles) throws IOException {
        if (changedFiles == null || changedFiles.isEmpty()) {
            return Collections.emptyList();
        }
        Path index = workspace.resolve(INDEX);
        if (!Files.isRegularFile(index)) {
            return Collections.emptyList();
        }
        String original = new String(Files.readAllBytes(index), StandardCharsets.UTF_8);
        List<IndexBlock> blocks = parseIndexBlocks(original);
        List<String> staleIds = new ArrayList<String>();
        for (IndexBlock block : blocks) {
            if (!"verified".equals(block.value("status")) && !"active".equals(block.value("status"))) {
                continue;
            }
            if (overlaps(block.list("source_paths"), changedFiles)) {
                block.set("status", "stale");
                staleIds.add(block.value("id"));
            }
        }
        if (staleIds.isEmpty()) {
            return Collections.emptyList();
        }
        StringBuilder rebuilt = new StringBuilder();
        for (IndexBlock block : blocks) {
            rebuilt.append(block.render());
        }
        Files.write(index, rebuilt.toString().getBytes(StandardCharsets.UTF_8));
        Path dir = lifecycleDir(workspace, storyId);
        Files.createDirectories(dir);
        StringBuilder report = new StringBuilder("# Knowledge Staleness Mark\n\n")
                .append("- story_id: ").append(storyId).append('\n')
                .append("- at: ").append(Instant.now()).append('\n')
                .append("- index: ").append(INDEX).append("\n\n## Changed Files\n\n");
        for (String changed : changedFiles) {
            report.append("- ").append(changed).append('\n');
        }
        report.append("\n## Marked Stale\n\n");
        for (String id : staleIds) {
            report.append("- ").append(id).append('\n');
        }
        Files.write(dir.resolve("knowledge-stale.md"), report.toString().getBytes(StandardCharsets.UTF_8));
        return Collections.unmodifiableList(staleIds);
    }

    /**
     * Records delivery evidence that knowledge is stale without mutating the verified index.
     *
     * <p>This is the production-safe serial-queue variant.  Mutating {@code knowledge.yaml}
     * before the business commit leaves an unrelated dirty control file and prevents the next
     * Story from passing its clean-worktree gate.  The journal is owned by the completed Story;
     * later Context Builders derive an effective stale state from it and treat current source as
     * authoritative.  A human-approved refresh remains the only operation that changes the
     * verified index.
     */
    public static List<String> recordStaleEvidenceForChangedFiles(
            Path workspace, String storyId, List<String> changedFiles) throws IOException {
        if (changedFiles == null || changedFiles.isEmpty()) {
            return Collections.emptyList();
        }
        Path index = workspace.resolve(INDEX);
        if (!Files.isRegularFile(index)) {
            return Collections.emptyList();
        }
        List<IndexBlock> blocks = parseIndexBlocks(
                new String(Files.readAllBytes(index), StandardCharsets.UTF_8));
        List<String> staleIds = new ArrayList<String>();
        Map<String, List<String>> sourcePaths = new LinkedHashMap<String, List<String>>();
        for (IndexBlock block : blocks) {
            if (!"verified".equals(block.value("status")) && !"active".equals(block.value("status"))) {
                continue;
            }
            if (overlaps(block.list("source_paths"), changedFiles)) {
                String id = block.value("id");
                if (!Strings.isBlank(id)) {
                    staleIds.add(id);
                    sourcePaths.put(id, block.list("source_paths"));
                }
            }
        }
        if (staleIds.isEmpty()) {
            return Collections.emptyList();
        }
        Path dir = lifecycleDir(workspace, storyId);
        Files.createDirectories(dir);
        StringBuilder report = new StringBuilder("# Knowledge Staleness Evidence\n\n")
                .append("- story_id: ").append(storyId).append('\n')
                .append("- at: ").append(Instant.now()).append('\n')
                .append("- index_mutated: false\n")
                .append("- authority: current_head_and_current_source\n\n## Changed Files\n\n");
        for (String changed : changedFiles) {
            report.append("- ").append(changed).append('\n');
        }
        report.append("\n## Marked Stale\n\n");
        for (String id : staleIds) {
            report.append("- id: ").append(id).append('\n');
            for (String source : sourcePaths.get(id)) {
                report.append("  source_path: ").append(source).append('\n');
            }
        }
        Files.write(dir.resolve("knowledge-stale.md"), report.toString().getBytes(StandardCharsets.UTF_8));
        return Collections.unmodifiableList(staleIds);
    }

    /** Human-readable, read-only inventory used before a Story or refresh decision. */
    public static String formatKnowledgeStatus(Path workspace) throws IOException {
        Path index = workspace.resolve(INDEX);
        if (!Files.isRegularFile(index)) {
            throw new StageGateException("Missing " + INDEX + " — onboard slots first");
        }
        Map<String, Integer> counts = new LinkedHashMap<String, Integer>();
        StringBuilder entries = new StringBuilder();
        for (IndexBlock block : parseIndexBlocks(new String(Files.readAllBytes(index), StandardCharsets.UTF_8))) {
            String id = block.value("id");
            if (Strings.isBlank(id)) {
                continue;
            }
            String status = Strings.isBlank(block.value("status")) ? "active" : block.value("status");
            counts.put(status, Integer.valueOf(counts.containsKey(status) ? counts.get(status).intValue() + 1 : 1));
            entries.append("- ").append(id).append(": ").append(status)
                    .append(" [").append(block.value("kind")).append("]\n");
        }
        StringBuilder out = new StringBuilder("knowledge_status\n");
        for (Map.Entry<String, Integer> count : counts.entrySet()) {
            out.append(count.getKey()).append('=').append(count.getValue()).append('\n');
        }
        return out.append(entries).toString();
    }

    private static boolean overlaps(List<String> sources, List<String> changed) {
        for (String source : sources) {
            String normalizedSource = source.replace('\\', '/');
            for (String candidate : changed) {
                String normalizedChanged = candidate.replace('\\', '/');
                if (normalizedSource.equals(normalizedChanged)
                        || normalizedChanged.startsWith(normalizedSource + "/")
                        || normalizedSource.startsWith(normalizedChanged + "/")) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String yamlList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "[]";
        }
        StringBuilder out = new StringBuilder("[");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                out.append(", ");
            }
            out.append(values.get(i));
        }
        return out.append(']').toString();
    }

    /** Small YAML block model; the knowledge index contract intentionally supports only flat fields. */
    private static List<IndexBlock> parseIndexBlocks(String source) {
        List<IndexBlock> blocks = new ArrayList<IndexBlock>();
        IndexBlock current = null;
        for (String raw : source.split("(?<=\\n)", -1)) {
            if (raw.startsWith("- id:")) {
                if (current != null) {
                    blocks.add(current);
                }
                current = new IndexBlock();
            }
            if (current == null) {
                current = new IndexBlock();
            }
            current.lines.add(raw);
        }
        if (current != null) {
            blocks.add(current);
        }
        return blocks;
    }

    private static final class IndexBlock {
        private final List<String> lines = new ArrayList<String>();

        String value(String key) {
            for (String raw : lines) {
                String line = raw.trim();
                if ("id".equals(key) && line.startsWith("- id:")) {
                    return line.substring("- id:".length()).trim();
                }
                if (line.startsWith(key + ":")) {
                    return line.substring(key.length() + 1).trim();
                }
            }
            return "";
        }

        List<String> list(String key) {
            String raw = value(key);
            if (Strings.isBlank(raw)) {
                return Collections.emptyList();
            }
            String bare = raw;
            if (bare.startsWith("[") && bare.endsWith("]")) {
                bare = bare.substring(1, bare.length() - 1);
            }
            List<String> out = new ArrayList<String>();
            for (String piece : bare.split(",")) {
                String item = piece.trim().replaceAll("^['\"]|['\"]$", "");
                if (!item.isEmpty()) {
                    out.add(item);
                }
            }
            return out;
        }

        void set(String key, String value) {
            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i).trim().startsWith(key + ":")) {
                    lines.set(i, "  " + key + ": " + value + "\n");
                    return;
                }
            }
            int at = lines.size();
            while (at > 0 && lines.get(at - 1).trim().isEmpty()) {
                at--;
            }
            lines.add(at, "  " + key + ": " + value + "\n");
        }

        String render() {
            StringBuilder out = new StringBuilder();
            for (String line : lines) {
                out.append(line);
            }
            return out.toString();
        }
    }
}
