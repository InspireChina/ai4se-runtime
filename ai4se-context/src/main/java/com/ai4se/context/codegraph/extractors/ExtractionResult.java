package com.ai4se.context.codegraph.extractors;

import com.ai4se.context.codegraph.GraphEdge;
import com.ai4se.context.codegraph.GraphNode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Nodes/edges produced by one file extractor, plus honest unknowns. */
public final class ExtractionResult {

    private final List<GraphNode> nodes;
    private final List<GraphEdge> edges;
    private final List<String> unknowns;

    public ExtractionResult(List<GraphNode> nodes, List<GraphEdge> edges, List<String> unknowns) {
        this.nodes = nodes == null
                ? Collections.<GraphNode>emptyList()
                : Collections.unmodifiableList(new ArrayList<GraphNode>(nodes));
        this.edges = edges == null
                ? Collections.<GraphEdge>emptyList()
                : Collections.unmodifiableList(new ArrayList<GraphEdge>(edges));
        this.unknowns = unknowns == null
                ? Collections.<String>emptyList()
                : Collections.unmodifiableList(new ArrayList<String>(unknowns));
    }

    public static ExtractionResult empty() {
        return new ExtractionResult(null, null, null);
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
}
