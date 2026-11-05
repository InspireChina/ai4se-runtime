package com.ai4se.context.codegraph;

import com.ai4se.context.codegraph.extractors.ExtractionResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * Incremental Code Graph refresh: rescan only added/changed/deleted source files, then re-check
 * direct neighbors referenced by those files' prior edges.
 */
public final class CodeGraphIncremental {

    private CodeGraphIncremental() {
    }

    public static CodeGraphSnapshot refresh(Path workspace, CodeGraphSnapshot previous) throws IOException {
        if (previous == null) {
            return CodeGraphBuilder.build(workspace);
        }
        Path root = workspace.toAbsolutePath().normalize();
        List<Path> currentFiles = SourceWalker.listSourceFiles(workspace);
        Map<String, String> currentDigests = new LinkedHashMap<String, String>();
        for (Path file : currentFiles) {
            String rel = root.relativize(file.toAbsolutePath().normalize()).toString().replace('\\', '/');
            currentDigests.put(rel, DigestUtil.fileDigest(file));
        }

        Set<String> changed = new LinkedHashSet<String>();
        Set<String> deleted = new LinkedHashSet<String>();
        for (Map.Entry<String, String> e : previous.fileDigests().entrySet()) {
            String path = e.getKey();
            if (!currentDigests.containsKey(path)) {
                deleted.add(path);
                changed.add(path);
            } else if (!e.getValue().equals(currentDigests.get(path))) {
                changed.add(path);
            }
        }
        for (String path : currentDigests.keySet()) {
            if (!previous.fileDigests().containsKey(path)) {
                changed.add(path);
            }
        }

        if (changed.isEmpty()) {
            return previous;
        }

        // Neighbor re-check: files that had edges into/from changed files
        Set<String> neighborFiles = new LinkedHashSet<String>();
        for (GraphEdge edge : previous.edges()) {
            if (changed.contains(edge.sourcePath())) {
                continue;
            }
            // If an edge targets a node whose source_path is changed, re-extract the neighbor file
            // so reverse links stay honest after symbol moves.
            for (GraphNode n : previous.nodes()) {
                if ((n.id().equals(edge.toId()) || n.id().equals(edge.fromId()))
                        && changed.contains(n.sourcePath())
                        && !n.sourcePath().isEmpty()) {
                    neighborFiles.add(edge.sourcePath());
                }
            }
        }
        changed.addAll(neighborFiles);

        List<GraphNode> nodes = new ArrayList<GraphNode>();
        List<GraphEdge> edges = new ArrayList<GraphEdge>();
        List<String> unknowns = new ArrayList<String>();
        for (GraphNode n : previous.nodes()) {
            if (!changed.contains(n.sourcePath()) && !"module".equals(n.kind())) {
                nodes.add(n);
            }
        }
        for (GraphEdge e : previous.edges()) {
            if (!changed.contains(e.sourcePath())) {
                edges.add(e);
            }
        }
        for (String u : previous.unknowns()) {
            boolean drop = false;
            for (String path : changed) {
                if (u.startsWith(path + ":") || u.startsWith(path + " ")) {
                    drop = true;
                    break;
                }
            }
            if (!drop) {
                unknowns.add(u);
            }
        }

        Map<String, Integer> coverage = new LinkedHashMap<String, Integer>();
        Set<String> moduleIds = new LinkedHashSet<String>();
        for (String path : currentDigests.keySet()) {
            String lang = languageOf(path);
            bump(coverage, lang);
            if (!changed.contains(path) || deleted.contains(path)) {
                continue;
            }
            if (!Files.isRegularFile(workspace.resolve(path))) {
                continue;
            }
            ExtractionResult result = CodeGraphBuilder.extractFile(workspace, path);
            nodes.addAll(result.nodes());
            edges.addAll(result.edges());
            unknowns.addAll(result.unknowns());
            for (GraphNode n : result.nodes()) {
                String mod = n.attrs().get("module");
                if (mod != null) {
                    moduleIds.add(mod);
                }
                if (n.id().startsWith("module:")) {
                    moduleIds.add(n.id());
                }
            }
        }
        // Preserve prior modules still referenced + new ones
        for (GraphNode n : previous.nodes()) {
            if ("module".equals(n.kind())) {
                moduleIds.add(n.id());
            }
        }
        for (String moduleId : moduleIds) {
            String name = moduleId.startsWith("module:") ? moduleId.substring("module:".length()) : moduleId;
            nodes.add(new GraphNode(
                    moduleId, "module", name, "", 0, "code-graph-builder", "high", "",
                    java.util.Collections.<String, String>emptyMap()));
        }

        nodes = dedupeNodes(nodes);
        edges = dedupeEdges(edges);
        Properties manifest = new Properties();
        manifest.setProperty("extractor_version", CodeGraphPaths.EXTRACTOR_VERSION);
        manifest.setProperty("refresh_mode", "incremental");
        manifest.setProperty("changed_file_count", Integer.toString(changed.size()));
        return new CodeGraphSnapshot(nodes, edges, unknowns, currentDigests, coverage, manifest);
    }

    private static String languageOf(String path) {
        String p = path.toLowerCase();
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

    private static void bump(Map<String, Integer> coverage, String lang) {
        Integer c = coverage.get(lang);
        coverage.put(lang, c == null ? 1 : c + 1);
    }

    private static List<GraphNode> dedupeNodes(List<GraphNode> nodes) {
        Map<String, GraphNode> map = new LinkedHashMap<String, GraphNode>();
        for (GraphNode n : nodes) {
            if (!map.containsKey(n.id())) {
                map.put(n.id(), n);
            }
        }
        return new ArrayList<GraphNode>(map.values());
    }

    private static List<GraphEdge> dedupeEdges(List<GraphEdge> edges) {
        Map<String, GraphEdge> map = new LinkedHashMap<String, GraphEdge>();
        for (GraphEdge e : edges) {
            if (!map.containsKey(e.id())) {
                map.put(e.id(), e);
            }
        }
        return new ArrayList<GraphEdge>(map.values());
    }
}
