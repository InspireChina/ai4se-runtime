package com.ai4se.context.codegraph;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/** In-memory graph plus file digests used for incremental refresh. */
public final class CodeGraphSnapshot {

    private final List<GraphNode> nodes;
    private final List<GraphEdge> edges;
    private final List<String> unknowns;
    private final Map<String, String> fileDigests;
    private final Map<String, Integer> coverage;
    private final Properties manifest;

    public CodeGraphSnapshot(
            List<GraphNode> nodes,
            List<GraphEdge> edges,
            List<String> unknowns,
            Map<String, String> fileDigests,
            Map<String, Integer> coverage,
            Properties manifest) {
        this.nodes = freezeNodes(nodes);
        this.edges = freezeEdges(edges);
        this.unknowns = unknowns == null
                ? Collections.<String>emptyList()
                : Collections.unmodifiableList(new ArrayList<String>(unknowns));
        this.fileDigests = freezeMap(fileDigests);
        this.coverage = freezeIntMap(coverage);
        this.manifest = copy(manifest);
    }

    public List<GraphNode> nodes() {
        return nodes;
    }

    public List<GraphEdge> edges() {
        return edges;
    }

    public List<String> unknowns() {
        return unknowns;
    }

    public Map<String, String> fileDigests() {
        return fileDigests;
    }

    public Map<String, Integer> coverage() {
        return coverage;
    }

    public Properties manifest() {
        return copy(manifest);
    }

    public String sourceDigest() {
        return DigestUtil.stableMapDigest(fileDigests);
    }

    private static List<GraphNode> freezeNodes(List<GraphNode> nodes) {
        if (nodes == null) {
            return Collections.emptyList();
        }
        List<GraphNode> copy = new ArrayList<GraphNode>(nodes);
        Collections.sort(copy);
        return Collections.unmodifiableList(copy);
    }

    private static List<GraphEdge> freezeEdges(List<GraphEdge> edges) {
        if (edges == null) {
            return Collections.emptyList();
        }
        List<GraphEdge> copy = new ArrayList<GraphEdge>(edges);
        Collections.sort(copy);
        return Collections.unmodifiableList(copy);
    }

    private static Map<String, String> freezeMap(Map<String, String> map) {
        if (map == null || map.isEmpty()) {
            return Collections.emptyMap();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<String, String>(map));
    }

    private static Map<String, Integer> freezeIntMap(Map<String, Integer> map) {
        if (map == null || map.isEmpty()) {
            return Collections.emptyMap();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<String, Integer>(map));
    }

    private static Properties copy(Properties p) {
        Properties out = new Properties();
        if (p != null) {
            for (String name : p.stringPropertyNames()) {
                out.setProperty(name, p.getProperty(name));
            }
        }
        return out;
    }
}
