package com.ai4se.context.codegraph;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Bounded subgraph selection for Story Analysis/Planning context. */
public final class CodeGraphQuery {

    public static final int DEFAULT_DEPTH = 2;
    public static final int DEFAULT_NODE_BUDGET = 120;
    public static final int DEFAULT_EDGE_BUDGET = 200;

    private static final Pattern TOKEN = Pattern.compile("[A-Za-z][A-Za-z0-9_]{2,}");

    private CodeGraphQuery() {
    }

    public static Subgraph select(
            CodeGraphSnapshot snapshot, Set<String> seeds, int depth, int nodeBudget, int edgeBudget) {
        if (snapshot == null) {
            return Subgraph.empty();
        }
        Map<String, GraphNode> byId = new LinkedHashMap<String, GraphNode>();
        for (GraphNode n : snapshot.nodes()) {
            byId.put(n.id(), n);
        }
        Map<String, List<GraphEdge>> out = new LinkedHashMap<String, List<GraphEdge>>();
        Map<String, List<GraphEdge>> in = new LinkedHashMap<String, List<GraphEdge>>();
        for (GraphEdge e : snapshot.edges()) {
            List<GraphEdge> o = out.get(e.fromId());
            if (o == null) {
                o = new ArrayList<GraphEdge>();
                out.put(e.fromId(), o);
            }
            o.add(e);
            List<GraphEdge> i = in.get(e.toId());
            if (i == null) {
                i = new ArrayList<GraphEdge>();
                in.put(e.toId(), i);
            }
            i.add(e);
        }

        Set<String> selectedNodes = new LinkedHashSet<String>();
        Set<String> selectedEdgeIds = new LinkedHashSet<String>();
        List<GraphEdge> selectedEdges = new ArrayList<GraphEdge>();
        List<String> truncated = new ArrayList<String>();

        Set<String> frontier = new LinkedHashSet<String>();
        for (String seed : seeds == null ? Collections.<String>emptySet() : seeds) {
            if (byId.containsKey(seed)) {
                frontier.add(seed);
                selectedNodes.add(seed);
            } else {
                // soft seed: match by name/path token
                for (GraphNode n : snapshot.nodes()) {
                    if (n.id().contains(seed) || n.name().equalsIgnoreCase(seed)
                            || n.sourcePath().contains(seed)) {
                        frontier.add(n.id());
                        selectedNodes.add(n.id());
                    }
                }
            }
        }

        int maxDepth = Math.max(1, Math.min(depth, 2));
        for (int d = 0; d < maxDepth; d++) {
            Set<String> next = new LinkedHashSet<String>();
            for (String id : frontier) {
                expand(out.get(id), selectedNodes, selectedEdgeIds, selectedEdges, next, nodeBudget, edgeBudget, truncated);
                expand(in.get(id), selectedNodes, selectedEdgeIds, selectedEdges, next, nodeBudget, edgeBudget, truncated);
                if (selectedNodes.size() >= nodeBudget || selectedEdges.size() >= edgeBudget) {
                    truncated.add("budget reached at depth " + (d + 1));
                    break;
                }
            }
            frontier = next;
            if (frontier.isEmpty() || !truncated.isEmpty()) {
                break;
            }
        }

        List<GraphNode> nodes = new ArrayList<GraphNode>();
        for (String id : selectedNodes) {
            GraphNode n = byId.get(id);
            if (n != null) {
                nodes.add(n);
            }
        }
        Collections.sort(nodes);
        Collections.sort(selectedEdges);
        return new Subgraph(nodes, selectedEdges, new ArrayList<String>(seeds), truncated);
    }

    /** Build seeds from requirement text, module hints and knowledge tags. */
    public static Set<String> seedsFromText(CodeGraphSnapshot snapshot, String text, List<String> hints) {
        Set<String> seeds = new LinkedHashSet<String>();
        if (hints != null) {
            for (String h : hints) {
                if (h != null && !h.trim().isEmpty()) {
                    seeds.add(h.trim());
                }
            }
        }
        if (text == null || text.isEmpty() || snapshot == null) {
            return seeds;
        }
        Matcher m = TOKEN.matcher(text);
        Set<String> tokens = new LinkedHashSet<String>();
        while (m.find()) {
            tokens.add(m.group());
        }
        for (GraphNode n : snapshot.nodes()) {
            String name = n.name();
            if (tokens.contains(name)) {
                seeds.add(n.id());
                continue;
            }
            String simple = name;
            int dot = simple.lastIndexOf('.');
            if (dot >= 0) {
                simple = simple.substring(dot + 1);
            }
            if (tokens.contains(simple)) {
                seeds.add(n.id());
            }
            for (String t : tokens) {
                if (t.length() >= 5 && n.sourcePath().toLowerCase(Locale.ROOT).contains(t.toLowerCase(Locale.ROOT))) {
                    seeds.add(n.id());
                    break;
                }
            }
        }
        return seeds;
    }

    private static void expand(
            List<GraphEdge> edges,
            Set<String> selectedNodes,
            Set<String> selectedEdgeIds,
            List<GraphEdge> selectedEdges,
            Set<String> next,
            int nodeBudget,
            int edgeBudget,
            List<String> truncated) {
        if (edges == null) {
            return;
        }
        for (GraphEdge e : edges) {
            if (selectedEdgeIds.contains(e.id())) {
                continue;
            }
            if (selectedEdges.size() >= edgeBudget || selectedNodes.size() >= nodeBudget) {
                truncated.add("edge/node budget exceeded");
                return;
            }
            selectedEdgeIds.add(e.id());
            selectedEdges.add(e);
            if (selectedNodes.add(e.fromId())) {
                next.add(e.fromId());
            }
            if (selectedNodes.add(e.toId())) {
                next.add(e.toId());
            }
        }
    }

    public static final class Subgraph {
        private final List<GraphNode> nodes;
        private final List<GraphEdge> edges;
        private final List<String> seeds;
        private final List<String> truncatedReasons;

        public Subgraph(
                List<GraphNode> nodes,
                List<GraphEdge> edges,
                List<String> seeds,
                List<String> truncatedReasons) {
            this.nodes = nodes == null
                    ? Collections.<GraphNode>emptyList()
                    : Collections.unmodifiableList(new ArrayList<GraphNode>(nodes));
            this.edges = edges == null
                    ? Collections.<GraphEdge>emptyList()
                    : Collections.unmodifiableList(new ArrayList<GraphEdge>(edges));
            this.seeds = seeds == null
                    ? Collections.<String>emptyList()
                    : Collections.unmodifiableList(new ArrayList<String>(seeds));
            this.truncatedReasons = truncatedReasons == null
                    ? Collections.<String>emptyList()
                    : Collections.unmodifiableList(new ArrayList<String>(truncatedReasons));
        }

        public static Subgraph empty() {
            return new Subgraph(null, null, null, null);
        }

        public List<GraphNode> nodes() {
            return nodes;
        }

        public List<GraphEdge> edges() {
            return edges;
        }

        public List<String> seeds() {
            return seeds;
        }

        public List<String> truncatedReasons() {
            return truncatedReasons;
        }

        public String toMarkdown() {
            StringBuilder sb = new StringBuilder();
            sb.append("# Impact Subgraph (deterministic)\n\n");
            sb.append("- seeds: ").append(seeds.isEmpty() ? "(none)" : join(seeds)).append('\n');
            sb.append("- nodes: ").append(nodes.size()).append('\n');
            sb.append("- edges: ").append(edges.size()).append('\n');
            if (!truncatedReasons.isEmpty()) {
                sb.append("- truncated: ").append(join(truncatedReasons)).append('\n');
            }
            sb.append("\n## Nodes\n\n");
            for (GraphNode n : nodes) {
                sb.append("- ").append(n.kind()).append(" `").append(n.id()).append("` @ ")
                        .append(n.sourcePath()).append(':').append(n.line())
                        .append(" (").append(n.confidence()).append(")\n");
            }
            sb.append("\n## Edges\n\n");
            for (GraphEdge e : edges) {
                sb.append("- ").append(e.kind()).append(" `").append(e.fromId()).append("` → `")
                        .append(e.toId()).append("` @ ").append(e.sourcePath()).append(':')
                        .append(e.line()).append(" (").append(e.confidence()).append(")\n");
            }
            sb.append("\n## Policy\n\n");
            sb.append("- Graph edges are navigation evidence, not business truth.\n");
            sb.append("- Absence of an edge is not proof of no impact.\n");
            return sb.toString();
        }

        private static String join(List<String> items) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < items.size(); i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(items.get(i));
            }
            return sb.toString();
        }
    }
}
