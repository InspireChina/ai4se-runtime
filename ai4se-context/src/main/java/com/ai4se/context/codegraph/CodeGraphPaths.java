package com.ai4se.context.codegraph;

import java.nio.file.Path;

/** Stable file contract for the lightweight deterministic Code Graph. */
public final class CodeGraphPaths {

    public static final String EXTRACTOR_VERSION = "code-graph-1.0.0";
    public static final String INDEX_REL = ".ai4se/index/code-graph";
    public static final String MANIFEST = "manifest.properties";
    public static final String NODES = "nodes.jsonl";
    public static final String EDGES = "edges.jsonl";
    public static final String COVERAGE = "coverage.md";
    public static final String UNKNOWNS = "unknowns.md";
    public static final String SOURCE_INVENTORY_REL = ".ai4se/repository/source-inventory.yaml";
    public static final String REPOSITORY_GRAPH_REL = ".ai4se/index/repository-graph.yaml";

    private CodeGraphPaths() {
    }

    public static Path root(Path workspace) {
        return workspace.resolve(INDEX_REL);
    }

    public static Path manifest(Path workspace) {
        return root(workspace).resolve(MANIFEST);
    }

    public static Path nodes(Path workspace) {
        return root(workspace).resolve(NODES);
    }

    public static Path edges(Path workspace) {
        return root(workspace).resolve(EDGES);
    }

    public static Path coverage(Path workspace) {
        return root(workspace).resolve(COVERAGE);
    }

    public static Path unknowns(Path workspace) {
        return root(workspace).resolve(UNKNOWNS);
    }

    public static Path sourceInventory(Path workspace) {
        return workspace.resolve(SOURCE_INVENTORY_REL);
    }

    public static Path repositoryGraph(Path workspace) {
        return workspace.resolve(REPOSITORY_GRAPH_REL);
    }
}
