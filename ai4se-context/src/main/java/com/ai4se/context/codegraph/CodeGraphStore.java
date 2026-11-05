package com.ai4se.context.codegraph;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

/** Stable, auditable persistence for the Code Graph contract files. */
public final class CodeGraphStore {

    private CodeGraphStore() {
    }

    public static void write(Path workspace, CodeGraphSnapshot snapshot) throws IOException {
        Path root = CodeGraphPaths.root(workspace);
        Files.createDirectories(root);
        List<GraphNode> nodes = new ArrayList<GraphNode>(snapshot.nodes());
        List<GraphEdge> edges = new ArrayList<GraphEdge>(snapshot.edges());
        Collections.sort(nodes);
        Collections.sort(edges);

        writeLines(CodeGraphPaths.nodes(workspace), toNodeLines(nodes));
        writeLines(CodeGraphPaths.edges(workspace), toEdgeLines(edges));
        writeCoverage(workspace, snapshot);
        writeUnknowns(workspace, snapshot.unknowns());
        writeManifest(workspace, snapshot, nodes, edges);
        writeSourceInventory(workspace, snapshot, nodes);
        writeRepositoryGraphSummary(workspace, snapshot, nodes, edges);
    }

    public static CodeGraphSnapshot read(Path workspace) throws IOException {
        Path manifestPath = CodeGraphPaths.manifest(workspace);
        if (!Files.isRegularFile(manifestPath)
                || !Files.isRegularFile(CodeGraphPaths.nodes(workspace))
                || !Files.isRegularFile(CodeGraphPaths.edges(workspace))) {
            throw new CodeGraphException("Code Graph missing under " + CodeGraphPaths.INDEX_REL);
        }
        Properties manifest = new Properties();
        try (BufferedReader reader = Files.newBufferedReader(manifestPath, StandardCharsets.UTF_8)) {
            manifest.load(reader);
        }
        List<GraphNode> nodes = new ArrayList<GraphNode>();
        for (String line : Files.readAllLines(CodeGraphPaths.nodes(workspace), StandardCharsets.UTF_8)) {
            if (!line.trim().isEmpty()) {
                nodes.add(GraphNode.fromJsonLine(line));
            }
        }
        List<GraphEdge> edges = new ArrayList<GraphEdge>();
        for (String line : Files.readAllLines(CodeGraphPaths.edges(workspace), StandardCharsets.UTF_8)) {
            if (!line.trim().isEmpty()) {
                edges.add(GraphEdge.fromJsonLine(line));
            }
        }
        List<String> unknowns = new ArrayList<String>();
        Path unknownsPath = CodeGraphPaths.unknowns(workspace);
        if (Files.isRegularFile(unknownsPath)) {
            for (String line : Files.readAllLines(unknownsPath, StandardCharsets.UTF_8)) {
                if (line.startsWith("- ")) {
                    unknowns.add(line.substring(2).trim());
                }
            }
        }
        Map<String, String> digests = new LinkedHashMap<String, String>();
        for (String name : new TreeMap<String, String>(asMap(manifest)).keySet()) {
            if (name.startsWith("file.")) {
                digests.put(name.substring("file.".length()).replace('\\', '/'),
                        manifest.getProperty(name));
            }
        }
        Map<String, Integer> coverage = new LinkedHashMap<String, Integer>();
        for (String name : manifest.stringPropertyNames()) {
            if (name.startsWith("coverage.")) {
                coverage.put(name.substring("coverage.".length()),
                        parseInt(manifest.getProperty(name)));
            }
        }
        return new CodeGraphSnapshot(nodes, edges, unknowns, digests, coverage, manifest);
    }

    public static boolean exists(Path workspace) {
        return Files.isRegularFile(CodeGraphPaths.manifest(workspace))
                && Files.isRegularFile(CodeGraphPaths.nodes(workspace))
                && Files.isRegularFile(CodeGraphPaths.edges(workspace));
    }

    private static void writeManifest(
            Path workspace, CodeGraphSnapshot snapshot, List<GraphNode> nodes, List<GraphEdge> edges)
            throws IOException {
        Properties p = new Properties();
        p.setProperty("extractor_version", CodeGraphPaths.EXTRACTOR_VERSION);
        p.setProperty("scanned_at_epoch_ms", Long.toString(System.currentTimeMillis()));
        p.setProperty("node_count", Integer.toString(nodes.size()));
        p.setProperty("edge_count", Integer.toString(edges.size()));
        p.setProperty("unknown_count", Integer.toString(snapshot.unknowns().size()));
        p.setProperty("source_digest", snapshot.sourceDigest());
        p.setProperty("graph_digest", DigestUtil.contentDigest(
                concat(toNodeLines(nodes), toEdgeLines(edges))));
        for (Map.Entry<String, Integer> e : snapshot.coverage().entrySet()) {
            p.setProperty("coverage." + e.getKey(), Integer.toString(e.getValue()));
        }
        // Stable file digest keys
        TreeMap<String, String> digests = new TreeMap<String, String>(snapshot.fileDigests());
        for (Map.Entry<String, String> e : digests.entrySet()) {
            p.setProperty("file." + e.getKey(), e.getValue());
        }
        // Write with sorted keys for determinism of file content except scanned_at
        StringBuilder sb = new StringBuilder();
        TreeMap<String, String> sorted = new TreeMap<String, String>(asMap(p));
        for (Map.Entry<String, String> e : sorted.entrySet()) {
            sb.append(e.getKey()).append('=').append(escapeProp(e.getValue())).append('\n');
        }
        Files.write(CodeGraphPaths.manifest(workspace), sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static void writeCoverage(Path workspace, CodeGraphSnapshot snapshot) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("# Code Graph Coverage\n\n");
        sb.append("- extractor_version: ").append(CodeGraphPaths.EXTRACTOR_VERSION).append('\n');
        sb.append("- source_digest: ").append(snapshot.sourceDigest()).append('\n');
        sb.append("- node_count: ").append(snapshot.nodes().size()).append('\n');
        sb.append("- edge_count: ").append(snapshot.edges().size()).append('\n');
        sb.append("\n## Files by language\n\n");
        TreeMap<String, Integer> cov = new TreeMap<String, Integer>(snapshot.coverage());
        for (Map.Entry<String, Integer> e : cov.entrySet()) {
            sb.append("- ").append(e.getKey()).append(": ").append(e.getValue()).append('\n');
        }
        sb.append("\n## Limits\n\n");
        sb.append("- Static scan cannot prove reflective, dynamic proxy, or runtime-configured edges.\n");
        sb.append("- JS/Vue edges are conservative string/import observations only.\n");
        sb.append("- Unsupported languages are counted under coverage.unsupported and do not invent relations.\n");
        Files.write(CodeGraphPaths.coverage(workspace), sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static void writeUnknowns(Path workspace, List<String> unknowns) throws IOException {
        StringBuilder sb = new StringBuilder("# Code Graph Unknowns\n\n");
        if (unknowns == null || unknowns.isEmpty()) {
            sb.append("- none\n");
        } else {
            List<String> sorted = new ArrayList<String>(unknowns);
            Collections.sort(sorted);
            for (String u : sorted) {
                sb.append("- ").append(u).append('\n');
            }
        }
        Files.write(CodeGraphPaths.unknowns(workspace), sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static void writeSourceInventory(
            Path workspace, CodeGraphSnapshot snapshot, List<GraphNode> nodes) throws IOException {
        Path out = CodeGraphPaths.sourceInventory(workspace);
        Files.createDirectories(out.getParent());
        Map<String, Integer> byKind = new TreeMap<String, Integer>();
        for (GraphNode n : nodes) {
            Integer c = byKind.get(n.kind());
            byKind.put(n.kind(), c == null ? 1 : c + 1);
        }
        // Compact inventory for Discovery/Analysis P1 — full per-file list is derived detail.
        StringBuilder sb = new StringBuilder();
        sb.append("# Generated by AI4SE Code Graph — deterministic inventory, not business knowledge\n");
        sb.append("extractor_version: ").append(CodeGraphPaths.EXTRACTOR_VERSION).append('\n');
        sb.append("source_digest: ").append(snapshot.sourceDigest()).append('\n');
        sb.append("file_count: ").append(snapshot.fileDigests().size()).append('\n');
        sb.append("node_count: ").append(nodes.size()).append('\n');
        sb.append("edge_count: ").append(snapshot.edges().size()).append('\n');
        sb.append("unknown_count: ").append(snapshot.unknowns().size()).append('\n');
        sb.append("code_graph_index: ").append(CodeGraphPaths.INDEX_REL).append('\n');
        sb.append("files_detail: .ai4se/repository/source-inventory-files.yaml\n");
        sb.append("kinds:\n");
        for (Map.Entry<String, Integer> e : byKind.entrySet()) {
            sb.append("  ").append(e.getKey()).append(": ").append(e.getValue()).append('\n');
        }
        sb.append("coverage:\n");
        for (Map.Entry<String, Integer> e : new TreeMap<String, Integer>(snapshot.coverage()).entrySet()) {
            sb.append("  ").append(e.getKey()).append(": ").append(e.getValue()).append('\n');
        }
        sb.append("note: Per-file digests live in files_detail and code-graph manifest; do not dump the full repo into model context.\n");
        Files.write(out, sb.toString().getBytes(StandardCharsets.UTF_8));

        StringBuilder detail = new StringBuilder();
        detail.append("# Per-file source digests (derived; not P1 by default)\n");
        detail.append("source_digest: ").append(snapshot.sourceDigest()).append('\n');
        detail.append("files:\n");
        for (String path : new TreeMap<String, String>(snapshot.fileDigests()).keySet()) {
            detail.append("  - path: ").append(path).append('\n');
            detail.append("    digest: ").append(snapshot.fileDigests().get(path)).append('\n');
            detail.append("    language: ").append(languageGuess(path)).append('\n');
        }
        Files.write(workspace.resolve(".ai4se/repository/source-inventory-files.yaml"),
                detail.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static void writeRepositoryGraphSummary(
            Path workspace, CodeGraphSnapshot snapshot, List<GraphNode> nodes, List<GraphEdge> edges)
            throws IOException {
        Path out = CodeGraphPaths.repositoryGraph(workspace);
        Files.createDirectories(out.getParent());
        StringBuilder sb = new StringBuilder();
        sb.append("# Deterministic repository graph summary for Discovery/Analysis\n");
        sb.append("extractor_version: ").append(CodeGraphPaths.EXTRACTOR_VERSION).append('\n');
        sb.append("source_digest: ").append(snapshot.sourceDigest()).append('\n');
        sb.append("nodes: ").append(nodes.size()).append('\n');
        sb.append("edges: ").append(edges.size()).append('\n');
        sb.append("index_path: ").append(CodeGraphPaths.INDEX_REL).append('\n');
        sb.append("note: Full nodes/edges live in code-graph jsonl; this file is a compact P1 pointer.\n");
        sb.append("top_kinds:\n");
        Map<String, Integer> byKind = new TreeMap<String, Integer>();
        for (GraphNode n : nodes) {
            Integer c = byKind.get(n.kind());
            byKind.put(n.kind(), c == null ? 1 : c + 1);
        }
        int shown = 0;
        for (Map.Entry<String, Integer> e : byKind.entrySet()) {
            sb.append("  - ").append(e.getKey()).append(": ").append(e.getValue()).append('\n');
            if (++shown >= 20) {
                break;
            }
        }
        Files.write(out, sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static List<String> toNodeLines(List<GraphNode> nodes) {
        List<String> lines = new ArrayList<String>(nodes.size());
        for (GraphNode n : nodes) {
            lines.add(n.toJsonLine());
        }
        return lines;
    }

    private static List<String> toEdgeLines(List<GraphEdge> edges) {
        List<String> lines = new ArrayList<String>(edges.size());
        for (GraphEdge e : edges) {
            lines.add(e.toJsonLine());
        }
        return lines;
    }

    private static void writeLines(Path path, List<String> lines) throws IOException {
        try (BufferedWriter w = new BufferedWriter(new OutputStreamWriter(
                Files.newOutputStream(path), StandardCharsets.UTF_8))) {
            for (String line : lines) {
                w.write(line);
                w.write('\n');
            }
        }
    }

    private static List<String> concat(List<String> a, List<String> b) {
        List<String> out = new ArrayList<String>(a.size() + b.size());
        out.addAll(a);
        out.addAll(b);
        return out;
    }

    private static Map<String, String> asMap(Properties p) {
        Map<String, String> m = new LinkedHashMap<String, String>();
        for (String name : p.stringPropertyNames()) {
            m.put(name, p.getProperty(name));
        }
        return m;
    }

    private static String escapeProp(String v) {
        return v == null ? "" : v.replace("\\", "\\\\").replace("\n", "\\n");
    }

    private static int parseInt(String s) {
        try {
            return Integer.parseInt(s);
        } catch (Exception e) {
            return 0;
        }
    }

    private static String languageGuess(String path) {
        String p = path.toLowerCase(Locale.ROOT);
        if (p.endsWith(".java")) {
            return "java";
        }
        if (p.endsWith(".vue")) {
            return "vue";
        }
        if (p.endsWith(".js") || p.endsWith(".ts")) {
            return "javascript";
        }
        if (p.endsWith(".xml")) {
            return "xml";
        }
        if (p.endsWith(".yml") || p.endsWith(".yaml") || p.endsWith(".properties")) {
            return "config";
        }
        return "unsupported";
    }
}
