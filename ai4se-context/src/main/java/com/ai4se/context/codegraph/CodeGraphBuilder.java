package com.ai4se.context.codegraph;

import com.ai4se.context.codegraph.extractors.ConfigKeyExtractor;
import com.ai4se.context.codegraph.extractors.ExtractionResult;
import com.ai4se.context.codegraph.extractors.JavaSourceExtractor;
import com.ai4se.context.codegraph.extractors.JsVueExtractor;
import com.ai4se.context.codegraph.extractors.MapperXmlExtractor;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/** Full-workspace deterministic Code Graph scan. */
public final class CodeGraphBuilder {

    private CodeGraphBuilder() {
    }

    public static CodeGraphSnapshot build(Path workspace) throws IOException {
        List<Path> files = SourceWalker.listSourceFiles(workspace);
        List<GraphNode> nodes = new ArrayList<GraphNode>();
        List<GraphEdge> edges = new ArrayList<GraphEdge>();
        List<String> unknowns = new ArrayList<String>();
        Map<String, String> digests = new LinkedHashMap<String, String>();
        Map<String, Integer> coverage = new LinkedHashMap<String, Integer>();
        Set<String> moduleIds = new LinkedHashSet<String>();

        Path root = workspace.toAbsolutePath().normalize();
        for (Path file : files) {
            String rel = root.relativize(file.toAbsolutePath().normalize()).toString().replace('\\', '/');
            byte[] bytes = Files.readAllBytes(file);
            String digest = DigestUtil.sha256Hex(bytes);
            digests.put(rel, digest);
            String lang = SourceWalker.languageOf(file);
            bump(coverage, lang);
            String text = new String(bytes, StandardCharsets.UTF_8);
            ExtractionResult result = extract(rel, lang, text, digest);
            nodes.addAll(result.nodes());
            edges.addAll(result.edges());
            unknowns.addAll(result.unknowns());
            for (GraphNode n : result.nodes()) {
                if (n.id().startsWith("module:")) {
                    moduleIds.add(n.id());
                }
                String mod = n.attrs().get("module");
                if (mod != null) {
                    moduleIds.add(mod);
                }
            }
        }
        for (String moduleId : moduleIds) {
            String name = moduleId.startsWith("module:") ? moduleId.substring("module:".length()) : moduleId;
            nodes.add(new GraphNode(
                    moduleId,
                    "module",
                    name,
                    "",
                    0,
                    "code-graph-builder",
                    "high",
                    "",
                    java.util.Collections.<String, String>emptyMap()));
        }
        // Deduplicate nodes/edges by id while keeping first occurrence in sorted order later
        nodes = dedupeNodes(nodes);
        edges = dedupeEdges(edges);

        Properties manifest = new Properties();
        manifest.setProperty("extractor_version", CodeGraphPaths.EXTRACTOR_VERSION);
        return new CodeGraphSnapshot(nodes, edges, unknowns, digests, coverage, manifest);
    }

    public static ExtractionResult extractFile(Path workspace, String relativePath) throws IOException {
        Path file = workspace.resolve(relativePath).normalize();
        if (!file.startsWith(workspace.toAbsolutePath().normalize()) || !Files.isRegularFile(file)) {
            return ExtractionResult.empty();
        }
        byte[] bytes = Files.readAllBytes(file);
        String digest = DigestUtil.sha256Hex(bytes);
        String lang = SourceWalker.languageOf(file);
        String text = new String(bytes, StandardCharsets.UTF_8);
        return extract(relativePath.replace('\\', '/'), lang, text, digest);
    }

    private static ExtractionResult extract(String rel, String lang, String text, String digest) {
        if ("java".equals(lang)) {
            return JavaSourceExtractor.extract(rel, text, digest);
        }
        if ("javascript".equals(lang) || "vue".equals(lang)) {
            return JsVueExtractor.extract(rel, text, digest);
        }
        if ("xml".equals(lang)) {
            return MapperXmlExtractor.extract(rel, text, digest);
        }
        if ("config".equals(lang)) {
            return ConfigKeyExtractor.extract(rel, text, digest);
        }
        List<String> unknowns = new ArrayList<String>();
        unknowns.add(rel + ": unsupported language left without forged relations (" + lang + ")");
        return new ExtractionResult(null, null, unknowns);
    }

    private static void bump(Map<String, Integer> coverage, String lang) {
        String key = lang == null ? "unsupported" : lang.toLowerCase(Locale.ROOT);
        Integer c = coverage.get(key);
        coverage.put(key, c == null ? 1 : c + 1);
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
