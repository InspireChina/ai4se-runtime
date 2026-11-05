package com.ai4se.orchestration.impact;

import com.ai4se.context.codegraph.CodeGraphControl;
import com.ai4se.context.codegraph.CodeGraphQuery;
import com.ai4se.context.codegraph.CodeGraphSnapshot;
import com.ai4se.context.codegraph.GraphEdge;
import com.ai4se.context.codegraph.GraphNode;
import com.ai4se.orchestration.analysis.PlanRecords;
import com.ai4se.orchestration.analysis.StageGateException;
import com.ai4se.orchestration.development.DevelopmentRecords;
import com.ai4se.runtime.common.util.Strings;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * After Development records a real business diff and before final Verification: refresh the
 * Code Graph incrementally, discover reverse dependents, and either extend regression selection
 * within the approved surface or stop safely when the frozen scope is breached.
 */
public final class PostDevImpactControl {

    private PostDevImpactControl() {
    }

    public static Result inspect(Path workspace, String storyId) throws IOException {
        List<String> changed = DevelopmentRecords.readChangedFiles(workspace, storyId);
        List<String> allowed = PlanRecords.readAllowedFiles(workspace, storyId);
        CodeGraphControl.RefreshResult refresh = CodeGraphControl.ensureFresh(workspace);
        CodeGraphSnapshot snapshot = refresh.snapshot;

        Set<String> seeds = new LinkedHashSet<String>();
        for (String path : changed) {
            seeds.add(path);
            for (GraphNode n : snapshot.nodes()) {
                if (path.equals(n.sourcePath())) {
                    seeds.add(n.id());
                }
            }
        }
        CodeGraphQuery.Subgraph subgraph = CodeGraphQuery.select(
                snapshot, seeds, 1, 80, 120);

        List<String> newCallers = new ArrayList<String>();
        List<String> outOfScope = new ArrayList<String>();
        List<String> extraRegression = new ArrayList<String>();

        Path impactDir = PlanRecords.planningDir(workspace, storyId).resolve("impact");
        Files.createDirectories(impactDir);
        Set<String> plannedPaths = readPlannedReferencePaths(impactDir.resolve("direct-references.md"));

        for (GraphEdge e : subgraph.edges()) {
            if (!"calls".equals(e.kind()) && !"uses".equals(e.kind()) && !"import".equals(e.kind())) {
                continue;
            }
            GraphNode from = find(snapshot, e.fromId());
            if (from == null || Strings.isBlank(from.sourcePath())) {
                continue;
            }
            // Reverse dependents: callers of changed symbols living outside changed files
            if (changed.contains(nodePath(snapshot, e.toId()))
                    && !changed.contains(from.sourcePath())) {
                String ref = from.sourcePath();
                if (!plannedPaths.contains(ref)) {
                    newCallers.add(ref + " → " + e.toId() + " @" + e.sourcePath() + ":" + e.line());
                }
                if (!isAllowed(ref, allowed)) {
                    outOfScope.add(ref);
                } else if (looksLikeTest(ref)) {
                    extraRegression.add("ENTRY_TEST # post-dev reverse impact " + ref);
                }
            }
        }

        if (!outOfScope.isEmpty()) {
            writeReport(impactDir, refresh, changed, newCallers, outOfScope, extraRegression, true);
            throw new StageGateException(
                    "Post-dev impact discovered business files outside Allowed Files: " + outOfScope
                            + " — refusing to silently expand write scope");
        }

        if (!extraRegression.isEmpty()) {
            appendRegression(impactDir.resolve("regression-selection.md"), extraRegression);
        }
        writeReport(impactDir, refresh, changed, newCallers, outOfScope, extraRegression, false);
        return new Result(refresh.mode.name(), newCallers, extraRegression, false);
    }

    public static final class Result {
        public final String refreshMode;
        public final List<String> newlyDiscoveredCallers;
        public final List<String> addedRegression;
        public final boolean stopped;

        public Result(
                String refreshMode,
                List<String> newlyDiscoveredCallers,
                List<String> addedRegression,
                boolean stopped) {
            this.refreshMode = refreshMode;
            this.newlyDiscoveredCallers = newlyDiscoveredCallers;
            this.addedRegression = addedRegression;
            this.stopped = stopped;
        }
    }

    private static GraphNode find(CodeGraphSnapshot snapshot, String id) {
        for (GraphNode n : snapshot.nodes()) {
            if (n.id().equals(id)) {
                return n;
            }
        }
        return null;
    }

    private static String nodePath(CodeGraphSnapshot snapshot, String id) {
        GraphNode n = find(snapshot, id);
        return n == null ? "" : n.sourcePath();
    }

    private static boolean isAllowed(String path, List<String> allowed) {
        String p = path.replace('\\', '/');
        for (String a : allowed) {
            String prefix = a.replace('\\', '/');
            if (p.equals(prefix) || p.startsWith(prefix.endsWith("/") ? prefix : prefix + "/")
                    || prefix.endsWith("/") && p.startsWith(prefix)
                    || p.startsWith(prefix)) {
                // Allowed Files are typically exact files; also accept directory prefixes
                if (p.equals(prefix) || prefix.endsWith("/") || !prefix.contains(".")) {
                    if (p.equals(prefix) || p.startsWith(prefix.endsWith("/") ? prefix : prefix + "/")) {
                        return true;
                    }
                }
                if (p.equals(prefix)) {
                    return true;
                }
            }
        }
        // exact match only for file-level Allowed
        for (String a : allowed) {
            if (p.equals(a.replace('\\', '/'))) {
                return true;
            }
        }
        return false;
    }

    private static boolean looksLikeTest(String path) {
        String p = path.replace('\\', '/').toLowerCase(Locale.ROOT);
        return p.contains("/test/") || p.endsWith("test.java") || p.endsWith("tests.java");
    }

    private static Set<String> readPlannedReferencePaths(Path directRefs) throws IOException {
        Set<String> out = new LinkedHashSet<String>();
        if (!Files.isRegularFile(directRefs)) {
            return out;
        }
        for (String line : Files.readAllLines(directRefs, StandardCharsets.UTF_8)) {
            int ev = line.indexOf("evidence:");
            if (ev < 0) {
                continue;
            }
            String rest = line.substring(ev + "evidence:".length()).trim();
            int bar = rest.indexOf('|');
            if (bar > 0) {
                rest = rest.substring(0, bar).trim();
            }
            int colon = rest.lastIndexOf(':');
            if (colon > 0 && colon > rest.lastIndexOf('/')) {
                rest = rest.substring(0, colon);
            }
            if (!rest.isEmpty()) {
                out.add(rest.replace('\\', '/'));
            }
        }
        return out;
    }

    private static void appendRegression(Path regressionFile, List<String> extras) throws IOException {
        String existing = Files.isRegularFile(regressionFile)
                ? new String(Files.readAllBytes(regressionFile), StandardCharsets.UTF_8)
                : "# Regression Selection\n\n";
        StringBuilder sb = new StringBuilder(existing);
        if (!existing.endsWith("\n")) {
            sb.append('\n');
        }
        sb.append("\n## Post-dev deterministic additions\n\n");
        for (String e : extras) {
            sb.append("- ").append(e).append('\n');
        }
        Files.write(regressionFile, sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static void writeReport(
            Path impactDir,
            CodeGraphControl.RefreshResult refresh,
            List<String> changed,
            List<String> newCallers,
            List<String> outOfScope,
            List<String> extraRegression,
            boolean stopped) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("# Post-Development Impact Check\n\n");
        sb.append("- refresh_mode: ").append(refresh.mode.name()).append('\n');
        sb.append("- elapsed_ms: ").append(refresh.elapsedMs).append('\n');
        sb.append("- source_digest: ").append(refresh.snapshot.sourceDigest()).append('\n');
        sb.append("- stopped: ").append(stopped).append('\n');
        sb.append("\n## Actual diff files\n\n");
        for (String c : changed) {
            sb.append("- ").append(c).append('\n');
        }
        sb.append("\n## Newly discovered callers (within graph)\n\n");
        if (newCallers.isEmpty()) {
            sb.append("- none\n");
        } else {
            for (String c : newCallers) {
                sb.append("- ").append(c).append('\n');
            }
        }
        sb.append("\n## Out-of-allowed discoveries\n\n");
        if (outOfScope.isEmpty()) {
            sb.append("- none\n");
        } else {
            for (String c : outOfScope) {
                sb.append("- ").append(c).append('\n');
            }
        }
        sb.append("\n## Added regression selections\n\n");
        if (extraRegression.isEmpty()) {
            sb.append("- none\n");
        } else {
            for (String c : extraRegression) {
                sb.append("- ").append(c).append('\n');
            }
        }
        sb.append("\n## Policy\n\n");
        sb.append("- Model cannot expand Allowed Files.\n");
        sb.append("- Graph absence is not proof of no impact; dynamic edges stay Unknown.\n");
        Files.write(impactDir.resolve("post-dev-impact.md"),
                sb.toString().getBytes(StandardCharsets.UTF_8));
    }
}
