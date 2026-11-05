package com.ai4se.context.codegraph.extractors;

import com.ai4se.context.codegraph.GraphEdge;
import com.ai4se.context.codegraph.GraphNode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Conservative config key inventory from properties/yaml. */
public final class ConfigKeyExtractor {

    private static final String EXTRACTOR = "config-key-1";
    private static final Pattern PROP = Pattern.compile("^\\s*([A-Za-z0-9_.-]+)\\s*[=:]\\s*(.*)$");
    private static final Pattern YAML_KEY = Pattern.compile("^(\\s*)([A-Za-z0-9_.-]+)\\s*:\\s*(.*)$");

    private ConfigKeyExtractor() {
    }

    public static ExtractionResult extract(String relativePath, String content, String digest) {
        if (content == null) {
            return ExtractionResult.empty();
        }
        String lower = relativePath.toLowerCase();
        if (!(lower.endsWith(".properties") || lower.endsWith(".yml") || lower.endsWith(".yaml"))) {
            return ExtractionResult.empty();
        }
        // Skip large lock/build noise disguised as yaml
        if (lower.contains("package-lock") || lower.contains("pnpm-lock")) {
            return ExtractionResult.empty();
        }
        List<GraphNode> nodes = new ArrayList<GraphNode>();
        List<GraphEdge> edges = new ArrayList<GraphEdge>();
        List<String> unknowns = new ArrayList<String>();
        String fileId = "file:" + relativePath;
        nodes.add(new GraphNode(fileId, "source_file", relativePath, relativePath, 1, EXTRACTOR,
                "high", digest, attrs("language", "config")));

        String[] lines = content.split("\\R", -1);
        List<String> stack = new ArrayList<String>();
        List<Integer> indents = new ArrayList<Integer>();
        int count = 0;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.trim().isEmpty() || line.trim().startsWith("#") || line.trim().startsWith("!")) {
                continue;
            }
            if (lower.endsWith(".properties")) {
                Matcher m = PROP.matcher(line);
                if (!m.find()) {
                    continue;
                }
                addKey(nodes, edges, fileId, relativePath, i + 1, digest, m.group(1));
                count++;
            } else {
                Matcher m = YAML_KEY.matcher(line);
                if (!m.find()) {
                    continue;
                }
                int indent = m.group(1).length();
                while (!indents.isEmpty() && indents.get(indents.size() - 1) >= indent) {
                    indents.remove(indents.size() - 1);
                    stack.remove(stack.size() - 1);
                }
                stack.add(m.group(2));
                indents.add(indent);
                StringBuilder key = new StringBuilder();
                for (int s = 0; s < stack.size(); s++) {
                    if (s > 0) {
                        key.append('.');
                    }
                    key.append(stack.get(s));
                }
                addKey(nodes, edges, fileId, relativePath, i + 1, digest, key.toString());
                count++;
                if (m.group(3) != null && !m.group(3).trim().isEmpty() && !m.group(3).trim().startsWith("|")
                        && !m.group(3).trim().startsWith(">")) {
                    // leaf — pop for siblings at same indent handled next loop
                }
            }
            if (count > 5000) {
                unknowns.add(relativePath + ": config key extraction capped at 5000 keys");
                break;
            }
        }
        return new ExtractionResult(nodes, edges, unknowns);
    }

    private static void addKey(
            List<GraphNode> nodes,
            List<GraphEdge> edges,
            String fileId,
            String path,
            int line,
            String digest,
            String key) {
        String id = "config:" + key;
        nodes.add(new GraphNode(id, "config_key", key, path, line, EXTRACTOR, "high", digest,
                Collections.<String, String>emptyMap()));
        edges.add(new GraphEdge("cfgdecl:" + path + ":" + line + ":" + key, "declares", fileId, id,
                path, line, EXTRACTOR, "high", digest, Collections.<String, String>emptyMap()));
    }

    private static Map<String, String> attrs(String... kv) {
        Map<String, String> m = new LinkedHashMap<String, String>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(kv[i], kv[i + 1]);
        }
        return m;
    }
}
