package com.ai4se.context.codegraph.extractors;

import com.ai4se.context.codegraph.GraphEdge;
import com.ai4se.context.codegraph.GraphNode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** MyBatis / Mapper XML → namespace, statements, and table name literals. */
public final class MapperXmlExtractor {

    private static final String EXTRACTOR = "mapper-xml-1";
    private static final Pattern NAMESPACE = Pattern.compile("namespace\\s*=\\s*\"([^\"]+)\"");
    private static final Pattern TABLE = Pattern.compile(
            "(?i)\\b(?:from|into|update|join|table)\\s+[`\"'\\[]?([a-zA-Z_][\\w]*)[`\"'\\]]?");

    private MapperXmlExtractor() {
    }

    public static ExtractionResult extract(String relativePath, String content, String digest) {
        if (content == null || !relativePath.endsWith(".xml")) {
            return ExtractionResult.empty();
        }
        String lower = content.toLowerCase();
        if (!lower.contains("<mapper") && !lower.contains("mybatis")) {
            return ExtractionResult.empty();
        }
        List<GraphNode> nodes = new ArrayList<GraphNode>();
        List<GraphEdge> edges = new ArrayList<GraphEdge>();
        List<String> unknowns = new ArrayList<String>();

        String fileId = "file:" + relativePath;
        nodes.add(new GraphNode(fileId, "source_file", relativePath, relativePath, 1, EXTRACTOR,
                "high", digest, attrs("language", "xml")));

        Matcher ns = NAMESPACE.matcher(content);
        String namespace = null;
        if (ns.find()) {
            namespace = ns.group(1);
            String mapperType = "type:" + namespace;
            nodes.add(new GraphNode(mapperType, "repository", simple(namespace), relativePath,
                    1, EXTRACTOR, "high", digest, attrs("namespace", namespace)));
            edges.add(new GraphEdge("mapperfile:" + relativePath, "declares", fileId, mapperType,
                    relativePath, 1, EXTRACTOR, "high", digest, Collections.<String, String>emptyMap()));
        } else {
            unknowns.add(relativePath + ": mapper xml without namespace");
        }

        Set<String> tables = new LinkedHashSet<String>();
        String[] lines = content.split("\\R", -1);
        for (int i = 0; i < lines.length; i++) {
            Matcher t = TABLE.matcher(lines[i]);
            while (t.find()) {
                String table = t.group(1);
                String tl = table.toLowerCase();
                if ("select".equals(tl) || "where".equals(tl) || "set".equals(tl)
                        || "values".equals(tl) || "dual".equals(tl)) {
                    continue;
                }
                tables.add(table);
                String tableId = "table:" + table;
                nodes.add(new GraphNode(tableId, "database_table", table, relativePath, i + 1,
                        EXTRACTOR, "medium", digest, Collections.<String, String>emptyMap()));
                if (namespace != null) {
                    edges.add(new GraphEdge(
                            "maps:" + namespace + ":" + table + ":" + (i + 1),
                            "maps_table",
                            "type:" + namespace,
                            tableId,
                            relativePath,
                            i + 1,
                            EXTRACTOR,
                            "medium",
                            digest,
                            attrs("table", table)));
                }
            }
        }
        if (tables.isEmpty()) {
            unknowns.add(relativePath + ": no static table names extracted from mapper xml");
        }
        return new ExtractionResult(nodes, edges, unknowns);
    }

    private static String simple(String fqn) {
        int dot = fqn.lastIndexOf('.');
        return dot >= 0 ? fqn.substring(dot + 1) : fqn;
    }

    private static Map<String, String> attrs(String... kv) {
        Map<String, String> m = new LinkedHashMap<String, String>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(kv[i], kv[i + 1]);
        }
        return m;
    }
}
