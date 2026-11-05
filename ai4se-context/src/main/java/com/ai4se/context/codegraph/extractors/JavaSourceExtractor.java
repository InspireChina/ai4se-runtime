package com.ai4se.context.codegraph.extractors;

import com.ai4se.context.codegraph.GraphEdge;
import com.ai4se.context.codegraph.GraphNode;
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

/**
 * Deterministic Java extractor (no AST library). Resolves only statically obvious relations;
 * unresolved or ambiguous calls are recorded as unknowns or low-confidence edges.
 */
public final class JavaSourceExtractor {

    private static final String EXTRACTOR = "java-source-1";
    private static final Pattern PACKAGE = Pattern.compile("^\\s*package\\s+([\\w.]+)\\s*;");
    private static final Pattern IMPORT = Pattern.compile("^\\s*import\\s+(static\\s+)?([\\w.*]+)\\s*;");
    private static final Pattern TYPE_DECL = Pattern.compile(
            "^\\s*(?:public\\s+|protected\\s+|private\\s+|abstract\\s+|final\\s+|strictfp\\s+)*"
                    + "(class|interface|enum)\\s+(\\w+)(?:\\s+extends\\s+([\\w.]+))?"
                    + "(?:\\s+implements\\s+([^{]+))?");
    private static final Pattern METHOD = Pattern.compile(
            "^\\s*(?:public\\s+|protected\\s+|private\\s+|static\\s+|final\\s+|synchronized\\s+|abstract\\s+|native\\s+|default\\s+)*"
                    + "(?:<[^>]+>\\s+)?([\\w.<>,\\[\\]?\\s]+)\\s+(\\w+)\\s*\\(([^;{}]*)\\)\\s*(?:throws\\s+[^{;]+)?\\s*(\\{|;|$)");
    private static final Pattern ANNOTATION = Pattern.compile("@(\\w+)(?:\\s*\\(([^)]*)\\))?");
    private static final Pattern MAPPING = Pattern.compile(
            "@(?:Request|Get|Post|Put|Delete|Patch)Mapping\\s*\\(\\s*(?:value\\s*=\\s*|path\\s*=\\s*)?\"([^\"]+)\"");
    private static final Pattern SIMPLE_CALL = Pattern.compile(
            "(?<![.\\w])(\\w+)\\s*\\.\\s*(\\w+)\\s*\\(");
    private static final Pattern THIS_CALL = Pattern.compile("\\bthis\\.(\\w+)\\s*\\(");
    private static final Pattern FIELD = Pattern.compile(
            "^\\s*(?:public\\s+|protected\\s+|private\\s+|static\\s+|final\\s+|transient\\s+|volatile\\s+)*"
                    + "([\\w.<>,\\[\\]?]+)\\s+(\\w+)\\s*(?:=|;)");

    private JavaSourceExtractor() {
    }

    public static ExtractionResult extract(String relativePath, String content, String digest) {
        if (content == null) {
            return ExtractionResult.empty();
        }
        List<GraphNode> nodes = new ArrayList<GraphNode>();
        List<GraphEdge> edges = new ArrayList<GraphEdge>();
        List<String> unknowns = new ArrayList<String>();

        String pkg = "";
        List<String> imports = new ArrayList<String>();
        Map<String, String> simpleToFqn = new LinkedHashMap<String, String>();
        String moduleId = moduleIdFor(relativePath);
        String fileId = "file:" + relativePath;
        nodes.add(node(fileId, "source_file", relativePath, relativePath, 1, "high", digest,
                attrs("language", "java", "module", moduleId)));
        edges.add(edge("belongs:" + fileId, "belongs_to", fileId, moduleId, relativePath, 1,
                "high", digest, Collections.<String, String>emptyMap()));

        String[] lines = content.split("\\R", -1);
        String typeKind = null;
        String typeName = null;
        String typeId = null;
        int typeLine = 0;
        boolean controller = false;
        boolean service = false;
        boolean repository = false;
        boolean mapper = false;
        boolean entity = false;
        boolean dto = false;
        boolean test = relativePath.contains("/test/") || relativePath.endsWith("Test.java")
                || relativePath.endsWith("Tests.java");
        String classBasePath = null;
        List<String> pendingAnnotations = new ArrayList<String>();
        Map<String, String> fields = new LinkedHashMap<String, String>();
        Set<String> methodIds = new LinkedHashSet<String>();
        String currentMethodId = null;
        int braceDepth = 0;
        int typeOpenDepth = -1; // braceDepth value when the type body opened; -1 = outside type

        for (int i = 0; i < lines.length; i++) {
            String raw = lines[i];
            String line = stripLineComment(raw);
            int lineNo = i + 1;
            int lineOpen = countChar(line, '{');
            int lineClose = countChar(line, '}');
            Matcher pkgM = PACKAGE.matcher(line);
            if (pkgM.find()) {
                pkg = pkgM.group(1);
                continue;
            }
            Matcher imp = IMPORT.matcher(line);
            if (imp.find()) {
                String fqn = imp.group(2);
                imports.add(fqn);
                if (!fqn.endsWith(".*")) {
                    String simple = fqn.substring(fqn.lastIndexOf('.') + 1);
                    simpleToFqn.put(simple, fqn);
                    String importTarget = "type:" + fqn;
                    edges.add(edge(
                            "import:" + relativePath + ":" + lineNo + ":" + fqn,
                            "import",
                            fileId,
                            importTarget,
                            relativePath,
                            lineNo,
                            "high",
                            digest,
                            attrs("fqn", fqn)));
                } else {
                    unknowns.add(relativePath + ":" + lineNo + ": wildcard import left unresolved: " + fqn);
                }
                continue;
            }

            Matcher ann = ANNOTATION.matcher(line);
            while (ann.find()) {
                pendingAnnotations.add(ann.group(1));
                String a = ann.group(1);
                if ("RestController".equals(a) || "Controller".equals(a)) {
                    controller = true;
                }
                if ("Service".equals(a)) {
                    service = true;
                }
                if ("Repository".equals(a)) {
                    repository = true;
                }
                if ("Mapper".equals(a)) {
                    mapper = true;
                }
                if ("Entity".equals(a) || "Table".equals(a)) {
                    entity = true;
                }
                if ("Data".equals(a) || "Builder".equals(a) || a.endsWith("DTO") || a.endsWith("VO")) {
                    dto = true;
                }
                // Endpoint path is recorded once with the owning method/type below; annotation
                // collection only flags controller/service stereotypes here.
            }

            Matcher type = TYPE_DECL.matcher(line);
            if (type.find() && typeId == null) {
                typeKind = type.group(1);
                typeName = type.group(2);
                typeLine = lineNo;
                String fqn = pkg.isEmpty() ? typeName : pkg + "." + typeName;
                typeId = "type:" + fqn;
                classBasePath = relativePath;
                String kind = resolveTypeKind(typeKind, controller, service, repository, mapper, entity, dto, test);
                Map<String, String> ta = attrs(
                        "fqn", fqn,
                        "type_kind", typeKind,
                        "module", moduleId);
                if (test) {
                    ta = attrs(ta, "role", "test");
                }
                nodes.add(node(typeId, kind, typeName, relativePath, lineNo, "high", digest, ta));
                edges.add(edge("declares:" + typeId, "declares", fileId, typeId, relativePath, lineNo,
                        "high", digest, Collections.<String, String>emptyMap()));
                String ext = type.group(3);
                if (ext != null && !ext.trim().isEmpty()) {
                    String target = resolveTypeRef(ext.trim(), pkg, simpleToFqn);
                    edges.add(edge("extends:" + typeId + ":" + target, "extends", typeId, target,
                            relativePath, lineNo, confidenceForResolved(target), digest,
                            Collections.<String, String>emptyMap()));
                }
                String impl = type.group(4);
                if (impl != null) {
                    for (String part : impl.split(",")) {
                        String name = part.trim();
                        if (name.isEmpty() || name.startsWith("{")) {
                            continue;
                        }
                        name = name.replace('{', ' ').trim();
                        if (name.isEmpty()) {
                            continue;
                        }
                        String target = resolveTypeRef(name, pkg, simpleToFqn);
                        edges.add(edge("implements:" + typeId + ":" + target, "implements", typeId, target,
                                relativePath, lineNo, confidenceForResolved(target), digest,
                                Collections.<String, String>emptyMap()));
                    }
                }
                pendingAnnotations.clear();
                if (lineOpen > 0) {
                    typeOpenDepth = braceDepth; // body opens on this line before depth update
                } else {
                    typeOpenDepth = -2; // awaiting opening brace on a later line
                }
                // fall through so braceDepth updates and methods on later lines are seen
            }

            if (typeId != null) {
                if (typeOpenDepth == -2 && lineOpen > 0) {
                    typeOpenDepth = braceDepth;
                }
                Matcher field = FIELD.matcher(line);
                if (field.find() && !line.contains("(")) {
                    String fType = field.group(1).trim();
                    String fName = field.group(2).trim();
                    if (!isKeyword(fType) && !isKeyword(fName)) {
                        fields.put(fName, resolveTypeRef(simpleName(fType), pkg, simpleToFqn));
                    }
                }
                Matcher method = METHOD.matcher(line);
                if (method.find() && typeName != null) {
                    String ret = method.group(1).trim();
                    String mName = method.group(2).trim();
                    if (!isKeyword(ret) && !"if".equals(mName) && !"for".equals(mName)
                            && !"while".equals(mName) && !"switch".equals(mName)
                            && !"catch".equals(mName) && !"return".equals(mName)
                            && !"new".equals(mName) && !mName.equals(typeName)) {
                        String methodId = "method:" + (pkg.isEmpty() ? typeName : pkg + "." + typeName)
                                + "#" + mName;
                        if (!methodIds.contains(methodId)) {
                            methodIds.add(methodId);
                            String mKind = test ? "test_class" : "java_method";
                            // methods keep java_method; test flagged on type
                            mKind = "java_method";
                            nodes.add(node(methodId, mKind, mName, relativePath, lineNo, "high", digest,
                                    attrs("owner", typeId, "return", ret)));
                            edges.add(edge("owns:" + methodId, "owns", typeId, methodId, relativePath, lineNo,
                                    "high", digest, Collections.<String, String>emptyMap()));
                            if (test && mName.startsWith("test")) {
                                // leave target discovery to caller patterns
                            }
                            currentMethodId = methodId;
                        } else {
                            currentMethodId = methodId;
                        }
                        for (String a : pendingAnnotations) {
                            if (a.endsWith("Mapping") && currentMethodId != null) {
                                Matcher map = MAPPING.matcher(line);
                                // mapping may be on previous annotation lines — handled separately below
                            }
                        }
                        pendingAnnotations.clear();
                    }
                }
                // endpoint mapping on its own annotation line already handled; also capture path on method
                Matcher mapOnLine = MAPPING.matcher(raw);
                if (mapOnLine.find() && typeId != null) {
                    String path = mapOnLine.group(1);
                    String epId = "endpoint:" + relativePath + ":" + lineNo + ":" + path;
                    nodes.add(node(epId, "controller_endpoint", path, relativePath, lineNo, "high", digest,
                            attrs("owner", typeId)));
                    String from = currentMethodId != null ? currentMethodId : typeId;
                    edges.add(edge("exposes:" + epId, "exposes", from, epId, relativePath, lineNo,
                            "high", digest, Collections.<String, String>emptyMap()));
                }

                if (currentMethodId != null) {
                    Matcher thisCall = THIS_CALL.matcher(line);
                    while (thisCall.find()) {
                        String callee = "method:" + (pkg.isEmpty() ? typeName : pkg + "." + typeName)
                                + "#" + thisCall.group(1);
                        edges.add(edge(
                                "calls:" + currentMethodId + ":" + callee + ":" + lineNo,
                                "calls",
                                currentMethodId,
                                callee,
                                relativePath,
                                lineNo,
                                "medium",
                                digest,
                                attrs("style", "this")));
                    }
                    Matcher call = SIMPLE_CALL.matcher(line);
                    while (call.find()) {
                        String receiver = call.group(1);
                        String methodName = call.group(2);
                        if (isKeyword(receiver) || "super".equals(receiver)) {
                            continue;
                        }
                        String targetType = fields.get(receiver);
                        if (targetType == null) {
                            targetType = simpleToFqn.get(receiver);
                        }
                        if (targetType == null && Character.isUpperCase(receiver.charAt(0))) {
                            targetType = resolveTypeRef(receiver, pkg, simpleToFqn);
                        }
                        if (targetType == null) {
                            unknowns.add(relativePath + ":" + lineNo
                                    + ": unresolved call receiver '" + receiver + "." + methodName + "()'");
                            continue;
                        }
                        String callee = "method:" + stripGenerics(targetType) + "#" + methodName;
                        String conf = fields.containsKey(receiver) ? "medium" : "low";
                        edges.add(edge(
                                "calls:" + currentMethodId + ":" + callee + ":" + lineNo,
                                "calls",
                                currentMethodId,
                                callee,
                                relativePath,
                                lineNo,
                                conf,
                                digest,
                                attrs("receiver", receiver)));
                        // layered hints
                        if (service && (targetType.toLowerCase(Locale.ROOT).contains("mapper")
                                || targetType.toLowerCase(Locale.ROOT).contains("repository"))) {
                            edges.add(edge(
                                    "uses_repo:" + currentMethodId + ":" + targetType + ":" + lineNo,
                                    "uses",
                                    typeId,
                                    "type:" + stripGenerics(targetType),
                                    relativePath,
                                    lineNo,
                                    "medium",
                                    digest,
                                    attrs("layer", "service_to_repository")));
                        }
                        if (controller && targetType.toLowerCase(Locale.ROOT).contains("service")) {
                            edges.add(edge(
                                    "uses_svc:" + typeId + ":" + targetType + ":" + lineNo,
                                    "uses",
                                    typeId,
                                    "type:" + stripGenerics(targetType),
                                    relativePath,
                                    lineNo,
                                    "medium",
                                    digest,
                                    attrs("layer", "controller_to_service")));
                        }
                    }
                }
            }

            braceDepth += lineOpen - lineClose;
            if (typeId != null && typeOpenDepth >= 0 && braceDepth <= typeOpenDepth) {
                // closed the type body (constructors/methods may open+close braces on one line
                // without leaving the enclosing type — only the type's own depth ends it)
                typeId = null;
                typeName = null;
                currentMethodId = null;
                fields.clear();
                typeOpenDepth = -1;
                pendingAnnotations.clear();
            }
        }

        if (typeName == null && content.contains("class ")) {
            unknowns.add(relativePath + ": type declaration not fully parsed");
        }
        // silence unused
        if (classBasePath == null && typeLine == 0) {
            // no type in file (package-info etc.)
        }
        return new ExtractionResult(nodes, edges, unknowns);
    }

    private static String resolveTypeKind(
            String typeKind,
            boolean controller,
            boolean service,
            boolean repository,
            boolean mapper,
            boolean entity,
            boolean dto,
            boolean test) {
        if (test) {
            return "test_class";
        }
        if (controller) {
            return "controller";
        }
        if (service) {
            return "service";
        }
        if (repository || mapper) {
            return "repository";
        }
        if (entity) {
            return "entity";
        }
        if (dto) {
            return "dto";
        }
        if ("interface".equals(typeKind)) {
            return "java_interface";
        }
        if ("enum".equals(typeKind)) {
            return "java_enum";
        }
        return "java_class";
    }

    private static String resolveTypeRef(String name, String pkg, Map<String, String> simpleToFqn) {
        String n = stripGenerics(name.trim());
        if (n.contains(".")) {
            return n;
        }
        if (simpleToFqn.containsKey(n)) {
            return simpleToFqn.get(n);
        }
        if (!pkg.isEmpty()) {
            return pkg + "." + n;
        }
        return n;
    }

    private static String confidenceForResolved(String target) {
        return target.contains(".") ? "high" : "low";
    }

    private static String moduleIdFor(String relativePath) {
        String p = relativePath.replace('\\', '/');
        int src = p.indexOf("/src/");
        if (src > 0) {
            return "module:" + p.substring(0, src);
        }
        int slash = p.indexOf('/');
        if (slash > 0) {
            return "module:" + p.substring(0, slash);
        }
        return "module:root";
    }

    private static String stripGenerics(String s) {
        int lt = s.indexOf('<');
        if (lt > 0) {
            return s.substring(0, lt).trim();
        }
        return s.replace("[]", "").trim();
    }

    private static String simpleName(String type) {
        String t = stripGenerics(type);
        int dot = t.lastIndexOf('.');
        return dot >= 0 ? t.substring(dot + 1) : t;
    }

    private static String stripLineComment(String line) {
        boolean inString = false;
        for (int i = 0; i < line.length() - 1; i++) {
            char c = line.charAt(i);
            if (c == '"' && (i == 0 || line.charAt(i - 1) != '\\')) {
                inString = !inString;
            } else if (!inString && c == '/' && line.charAt(i + 1) == '/') {
                return line.substring(0, i);
            }
        }
        return line;
    }

    private static int countChar(String s, char c) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == c) {
                n++;
            }
        }
        return n;
    }

    private static boolean isKeyword(String s) {
        String x = s.trim();
        return "if".equals(x) || "for".equals(x) || "while".equals(x) || "switch".equals(x)
                || "return".equals(x) || "new".equals(x) || "try".equals(x) || "catch".equals(x)
                || "else".equals(x) || "class".equals(x) || "void".equals(x);
    }

    private static GraphNode node(
            String id, String kind, String name, String path, int line, String confidence, String digest,
            Map<String, String> attrs) {
        return new GraphNode(id, kind, name, path, line, EXTRACTOR, confidence, digest, attrs);
    }

    private static GraphEdge edge(
            String id, String kind, String from, String to, String path, int line, String confidence,
            String digest, Map<String, String> attrs) {
        return new GraphEdge(id, kind, from, to, path, line, EXTRACTOR, confidence, digest, attrs);
    }

    private static Map<String, String> attrs(String... kv) {
        Map<String, String> m = new LinkedHashMap<String, String>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(kv[i], kv[i + 1]);
        }
        return m;
    }

    private static Map<String, String> attrs(Map<String, String> base, String k, String v) {
        Map<String, String> m = new LinkedHashMap<String, String>(base);
        m.put(k, v);
        return m;
    }
}
