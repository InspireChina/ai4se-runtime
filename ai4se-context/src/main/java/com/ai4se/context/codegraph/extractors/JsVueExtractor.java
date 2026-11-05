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

/**
 * Conservative JS/Vue indexer. Only records explicit imports, axios/fetch URL literals, and
 * component script references — never invents high-confidence call graphs from identifiers alone.
 */
public final class JsVueExtractor {

    private static final String EXTRACTOR = "js-vue-1";
    private static final Pattern IMPORT = Pattern.compile(
            "import\\s+(?:[\\w*{}$,\\s]+\\s+from\\s+)?['\"]([^'\"]+)['\"]");
    private static final Pattern REQUIRE = Pattern.compile(
            "require\\s*\\(\\s*['\"]([^'\"]+)['\"]\\s*\\)");
    private static final Pattern AXIOS = Pattern.compile(
            "axios\\s*\\.\\s*(?:get|post|put|delete|patch|request)\\s*\\(\\s*['`]([^'`\"]+)['`]");
    private static final Pattern FETCH = Pattern.compile(
            "fetch\\s*\\(\\s*['`]([^'`\"]+)['`]");
    private static final Pattern REQUEST = Pattern.compile(
            "(?:request|http)\\s*\\.\\s*(?:get|post|put|delete|patch)\\s*\\(\\s*['`]([^'`\"]+)['`]");
    private static final Pattern URL_PROP = Pattern.compile(
            "\\burl\\s*:\\s*['`]([^'`\"]+)['`]");
    private static final Pattern COMPONENT_NAME = Pattern.compile(
            "name\\s*:\\s*['\"]([^'\"]+)['\"]");

    private JsVueExtractor() {
    }

    public static ExtractionResult extract(String relativePath, String content, String digest) {
        if (content == null) {
            return ExtractionResult.empty();
        }
        List<GraphNode> nodes = new ArrayList<GraphNode>();
        List<GraphEdge> edges = new ArrayList<GraphEdge>();
        List<String> unknowns = new ArrayList<String>();

        boolean vue = relativePath.endsWith(".vue");
        String moduleId = "jsmodule:" + relativePath;
        nodes.add(new GraphNode(
                moduleId,
                vue ? "vue_module" : "js_module",
                relativePath,
                relativePath,
                1,
                EXTRACTOR,
                "high",
                digest,
                attrs("language", vue ? "vue" : "javascript")));

        String[] lines = content.split("\\R", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            int lineNo = i + 1;
            Matcher imp = IMPORT.matcher(line);
            while (imp.find()) {
                String target = imp.group(1);
                String toId = "jsmodule:" + normalizeRef(relativePath, target);
                edges.add(new GraphEdge(
                        "jsimport:" + relativePath + ":" + lineNo + ":" + target,
                        "import",
                        moduleId,
                        toId,
                        relativePath,
                        lineNo,
                        EXTRACTOR,
                        target.startsWith(".") ? "medium" : "low",
                        digest,
                        attrs("specifier", target)));
            }
            Matcher req = REQUIRE.matcher(line);
            while (req.find()) {
                String target = req.group(1);
                edges.add(new GraphEdge(
                        "jsrequire:" + relativePath + ":" + lineNo + ":" + target,
                        "import",
                        moduleId,
                        "jsmodule:" + normalizeRef(relativePath, target),
                        relativePath,
                        lineNo,
                        EXTRACTOR,
                        "low",
                        digest,
                        attrs("specifier", target, "style", "require")));
            }
            collectApi(line, relativePath, lineNo, moduleId, digest, nodes, edges, AXIOS);
            collectApi(line, relativePath, lineNo, moduleId, digest, nodes, edges, FETCH);
            collectApi(line, relativePath, lineNo, moduleId, digest, nodes, edges, REQUEST);
            collectApi(line, relativePath, lineNo, moduleId, digest, nodes, edges, URL_PROP);

            Matcher name = COMPONENT_NAME.matcher(line);
            if (vue && name.find()) {
                nodes.add(new GraphNode(
                        "vuecomponent:" + relativePath + ":" + name.group(1),
                        "vue_module",
                        name.group(1),
                        relativePath,
                        lineNo,
                        EXTRACTOR,
                        "medium",
                        digest,
                        attrs("component", name.group(1))));
            }
            if (line.contains("${") && (line.contains("axios") || line.contains("fetch")
                    || line.contains("url:"))) {
                unknowns.add(relativePath + ":" + lineNo
                        + ": dynamic frontend API URL template not resolved");
            }
        }
        return new ExtractionResult(nodes, edges, unknowns);
    }

    private static void collectApi(
            String line,
            String relativePath,
            int lineNo,
            String moduleId,
            String digest,
            List<GraphNode> nodes,
            List<GraphEdge> edges,
            Pattern pattern) {
        Matcher m = pattern.matcher(line);
        while (m.find()) {
            String path = m.group(1);
            if (path.contains("${")) {
                continue;
            }
            String epId = "frontend_api:" + path;
            nodes.add(new GraphNode(
                    epId,
                    "frontend_api",
                    path,
                    relativePath,
                    lineNo,
                    EXTRACTOR,
                    "medium",
                    digest,
                    Collections.<String, String>emptyMap()));
            edges.add(new GraphEdge(
                    "feapi:" + moduleId + ":" + path + ":" + lineNo,
                    "calls_api",
                    moduleId,
                    epId,
                    relativePath,
                    lineNo,
                    EXTRACTOR,
                    "medium",
                    digest,
                    attrs("path", path)));
            // conservative link to backend endpoint node id convention
            edges.add(new GraphEdge(
                    "fe2ep:" + path + ":" + lineNo + ":" + relativePath,
                    "references_endpoint",
                    epId,
                    "endpoint:" + path,
                    relativePath,
                    lineNo,
                    EXTRACTOR,
                    "low",
                    digest,
                    attrs("note", "path_string_match_only")));
        }
    }

    private static String normalizeRef(String fromPath, String specifier) {
        if (!specifier.startsWith(".")) {
            return specifier;
        }
        String dir = fromPath.replace('\\', '/');
        int slash = dir.lastIndexOf('/');
        String base = slash >= 0 ? dir.substring(0, slash) : "";
        String[] parts = (base + "/" + specifier).replace('\\', '/').split("/");
        List<String> stack = new ArrayList<String>();
        for (String p : parts) {
            if (p.isEmpty() || ".".equals(p)) {
                continue;
            }
            if ("..".equals(p)) {
                if (!stack.isEmpty()) {
                    stack.remove(stack.size() - 1);
                }
            } else {
                stack.add(p);
            }
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < stack.size(); i++) {
            if (i > 0) {
                sb.append('/');
            }
            sb.append(stack.get(i));
        }
        return sb.toString();
    }

    private static Map<String, String> attrs(String... kv) {
        Map<String, String> m = new LinkedHashMap<String, String>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(kv[i], kv[i + 1]);
        }
        return m;
    }
}
