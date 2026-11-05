package com.ai4se.context.codegraph;

import com.ai4se.context.story.StoryRequirement;
import com.ai4se.context.story.StoryRequirementReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Writes bounded impact subgraph slices for Analysis/Planning packages and seeds
 * {@code planning/impact/direct-references.md}.
 */
public final class ImpactSubgraphWriter {

    private ImpactSubgraphWriter() {
    }

    public static CodeGraphQuery.Subgraph writeForStory(
            Path workspace, String storyId, Path slicesDir, List<String> p1) throws IOException {
        CodeGraphControl.RefreshResult refresh = CodeGraphControl.ensureFresh(workspace);
        CodeGraphSnapshot snapshot = refresh.snapshot;
        StoryRequirement requirement = StoryRequirementReader.read(workspace, storyId);
        StringBuilder seedText = new StringBuilder();
        seedText.append(requirement.goal()).append('\n')
                .append(requirement.inScope()).append('\n')
                .append(requirement.raw()).append('\n');
        Path reqPath = StoryRequirementReader.requirementPath(workspace, storyId);
        if (Files.isRegularFile(reqPath)) {
            seedText.append(new String(Files.readAllBytes(reqPath), StandardCharsets.UTF_8));
        }
        for (String a : requirement.acceptance()) {
            seedText.append('\n').append(a);
        }
        List<String> hints = new ArrayList<String>();
        hints.addAll(requirement.acceptance());
        Set<String> seeds = CodeGraphQuery.seedsFromText(snapshot, seedText.toString(), hints);
        CodeGraphQuery.Subgraph subgraph = CodeGraphQuery.select(
                snapshot,
                seeds,
                CodeGraphQuery.DEFAULT_DEPTH,
                CodeGraphQuery.DEFAULT_NODE_BUDGET,
                CodeGraphQuery.DEFAULT_EDGE_BUDGET);

        Files.createDirectories(slicesDir);
        Path subgraphSlice = slicesDir.resolve("impact-subgraph.md");
        Files.write(subgraphSlice, subgraph.toMarkdown().getBytes(StandardCharsets.UTF_8));
        if (p1 != null) {
            p1.add("slices/impact-subgraph.md");
        }

        Path refreshMeta = slicesDir.resolve("code-graph-refresh.properties");
        String meta = "mode=" + refresh.mode.name() + "\n"
                + "elapsed_ms=" + refresh.elapsedMs + "\n"
                + "source_digest=" + snapshot.sourceDigest() + "\n"
                + "node_count=" + snapshot.nodes().size() + "\n"
                + "edge_count=" + snapshot.edges().size() + "\n"
                + "subgraph_nodes=" + subgraph.nodes().size() + "\n"
                + "subgraph_edges=" + subgraph.edges().size() + "\n";
        Files.write(refreshMeta, meta.getBytes(StandardCharsets.UTF_8));
        if (p1 != null) {
            p1.add("slices/code-graph-refresh.properties");
        }

        // Minimal necessary source re-read: up to 8 unique source files from subgraph
        Set<String> sourcePaths = new LinkedHashSet<String>();
        for (GraphNode n : subgraph.nodes()) {
            if (n.sourcePath() != null && !n.sourcePath().isEmpty()) {
                sourcePaths.add(n.sourcePath());
            }
            if (sourcePaths.size() >= 8) {
                break;
            }
        }
        StringBuilder slices = new StringBuilder("# Related source slices (bounded)\n\n");
        for (String rel : sourcePaths) {
            Path src = workspace.resolve(rel).normalize();
            if (!src.startsWith(workspace.toAbsolutePath().normalize()) || !Files.isRegularFile(src)) {
                continue;
            }
            byte[] bytes = Files.readAllBytes(src);
            String text = new String(bytes, StandardCharsets.UTF_8);
            String[] lines = text.split("\\R", -1);
            int max = Math.min(lines.length, 120);
            slices.append("## ").append(rel).append("\n\n```\n");
            for (int i = 0; i < max; i++) {
                slices.append(lines[i]).append('\n');
            }
            if (lines.length > max) {
                slices.append("… truncated …\n");
            }
            slices.append("```\n\n");
        }
        if (sourcePaths.isEmpty()) {
            slices.append("- none matched within budget\n");
        }
        Files.write(slicesDir.resolve("impact-source-slices.md"),
                slices.toString().getBytes(StandardCharsets.UTF_8));
        if (p1 != null) {
            p1.add("slices/impact-source-slices.md");
        }

        writeDirectReferencesDraft(workspace, storyId, subgraph);
        return subgraph;
    }

    public static void writeDirectReferencesDraft(
            Path workspace, String storyId, CodeGraphQuery.Subgraph subgraph) throws IOException {
        Path impactDir = workspace.resolve(".story").resolve(storyId).resolve("planning").resolve("impact");
        Files.createDirectories(impactDir);
        StringBuilder sb = new StringBuilder();
        sb.append("# Direct References\n\n");
        sb.append("## Deterministic graph edges\n\n");
        if (subgraph.edges().isEmpty()) {
            sb.append("- (none in selected subgraph — this is not proof of no impact)\n");
        } else {
            for (GraphEdge e : subgraph.edges()) {
                sb.append("- edge: ").append(e.kind())
                        .append(" | from: ").append(e.fromId())
                        .append(" | to: ").append(e.toId())
                        .append(" | evidence: ").append(e.sourcePath()).append(':').append(e.line())
                        .append(" | confidence: ").append(e.confidence())
                        .append(" | extractor: ").append(e.extractor())
                        .append('\n');
            }
        }
        sb.append("\n## Model-derived scenarios\n\n");
        sb.append("- (filled by Planning behavioral scenarios; must cite source evidence)\n");
        sb.append("\n## Unknown\n\n");
        if (subgraph.truncatedReasons().isEmpty()) {
            sb.append("- dynamic/reflective edges remain unknown unless proven by tests/probes\n");
        } else {
            for (String t : subgraph.truncatedReasons()) {
                sb.append("- ").append(t).append('\n');
            }
            sb.append("- dynamic/reflective edges remain unknown unless proven by tests/probes\n");
        }
        Files.write(impactDir.resolve("direct-references.md"), sb.toString().getBytes(StandardCharsets.UTF_8));
    }
}
