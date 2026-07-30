package com.ai4se.runtime.demo.analysis;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Map + Search analyzer — Facts only. No Graph / Context / Plan / Patch.
 */
public final class MapSearchAnalyzer {

    private static final int MAX_FILES = 400;
    private static final int TOP_K = 8;

    public RepositoryFacts analyze(Path workspaceRoot, String requirement) throws IOException {
        List<String> modules = new ArrayList<String>();
        List<String> buildFiles = new ArrayList<String>();
        List<String> facts = new ArrayList<String>();
        final List<Scored> scored = new ArrayList<Scored>();
        final List<String> keywords = keywords(requirement);
        final int[] visited = new int[] {0};

        Files.walkFileTree(workspaceRoot, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                String name = dir.getFileName() == null ? "" : dir.getFileName().toString();
                if ("target".equals(name) || ".git".equals(name) || "node_modules".equals(name)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (visited[0] >= MAX_FILES) {
                    return FileVisitResult.TERMINATE;
                }
                visited[0]++;
                Path rel = workspaceRoot.relativize(file);
                String path = rel.toString().replace('\\', '/');
                String lower = path.toLowerCase(Locale.ROOT);
                if (lower.endsWith("pom.xml") || lower.endsWith("package.json") || lower.endsWith("build.gradle")) {
                    buildFiles.add(path);
                    facts.add("build-file:" + path);
                }
                if (path.contains("/src/main/java/") && path.endsWith(".java")) {
                    String module = moduleOf(path);
                    if (!modules.contains(module)) {
                        modules.add(module);
                    }
                }
                int score = scorePath(path, keywords);
                String excerpt = "";
                if (isText(path) && attrs.size() < 64 * 1024) {
                    String body = new String(Files.readAllBytes(file), Charset.forName("UTF-8"));
                    score += scoreBody(body, keywords);
                    excerpt = firstLine(body);
                }
                if (score > 0) {
                    scored.add(new Scored(path, excerpt, score));
                }
                return FileVisitResult.CONTINUE;
            }
        });

        Collections.sort(scored, new Comparator<Scored>() {
            @Override
            public int compare(Scored a, Scored b) {
                return Integer.compare(b.score, a.score);
            }
        });
        List<RepositoryFacts.Hit> hits = new ArrayList<RepositoryFacts.Hit>();
        for (int i = 0; i < scored.size() && i < TOP_K; i++) {
            Scored s = scored.get(i);
            hits.add(new RepositoryFacts.Hit(s.path, s.excerpt, s.score));
            facts.add("hit:" + s.path + " score=" + s.score);
        }
        facts.add("modules=" + modules.size());
        facts.add("files-scanned=" + visited[0]);

        Map<String, String> meta = new LinkedHashMap<String, String>();
        meta.put("strategy", "map+search");
        meta.put("truncated", visited[0] >= MAX_FILES ? "true" : "false");
        meta.put("keywords", keywords.toString());
        return new RepositoryFacts(modules, buildFiles, hits, facts, meta);
    }

    private static List<String> keywords(String requirement) {
        List<String> keys = new ArrayList<String>();
        String raw = requirement == null ? "" : requirement.toLowerCase(Locale.ROOT);
        String[] seeds = new String[] {
                "order", "订单", "timeout", "超时", "config", "配置", "api", "接口",
                "properties", "application",
                "promotion", "促销", "满减", "满折", "优惠", "discount", "ladder", "阶梯"
        };
        for (String seed : seeds) {
            if (raw.contains(seed.toLowerCase(Locale.ROOT)) || raw.contains(seed)) {
                keys.add(seed.toLowerCase(Locale.ROOT));
            }
        }
        if (keys.isEmpty()) {
            keys.add("api");
        }
        if (raw.contains("timeout") || raw.contains("超时")) {
            if (!keys.contains("timeout")) {
                keys.add("timeout");
            }
            if (!keys.contains("config") && !keys.contains("配置")) {
                keys.add("config");
            }
        }
        return keys;
    }

    private static int scorePath(String path, List<String> keywords) {
        String lower = path.toLowerCase(Locale.ROOT);
        int score = 0;
        for (String k : keywords) {
            if (lower.contains(k)) {
                score += 5;
            }
        }
        if (lower.endsWith(".java") || lower.endsWith(".properties")) {
            score += 1;
        }
        return score;
    }

    private static int scoreBody(String body, List<String> keywords) {
        String lower = body.toLowerCase(Locale.ROOT);
        int score = 0;
        for (String k : keywords) {
            if (lower.contains(k)) {
                score += 3;
            }
        }
        return score;
    }

    private static boolean isText(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        return lower.endsWith(".java")
                || lower.endsWith(".md")
                || lower.endsWith(".properties")
                || lower.endsWith(".xml")
                || lower.endsWith(".yml")
                || lower.endsWith(".yaml")
                || lower.endsWith(".txt");
    }

    private static String firstLine(String body) {
        int nl = body.indexOf('\n');
        String line = nl < 0 ? body : body.substring(0, nl);
        line = line.trim();
        if (line.length() > 120) {
            return line.substring(0, 120) + "...";
        }
        return line;
    }

    private static String moduleOf(String path) {
        // src/main/java/com/example/order/Foo.java → com.example.order
        String marker = "/src/main/java/";
        int idx = path.indexOf(marker);
        if (idx < 0) {
            return "unknown";
        }
        String rest = path.substring(idx + marker.length());
        int slash = rest.lastIndexOf('/');
        if (slash <= 0) {
            return rest.replace(".java", "").replace('/', '.');
        }
        return rest.substring(0, slash).replace('/', '.');
    }

    private static final class Scored {
        final String path;
        final String excerpt;
        final int score;

        Scored(String path, String excerpt, int score) {
            this.path = path;
            this.excerpt = excerpt;
            this.score = score;
        }
    }
}
