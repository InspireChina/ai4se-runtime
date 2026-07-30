package com.ai4se.runtime.engine.support;

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
import java.util.List;
import java.util.Locale;

/**
 * S8a — Map + Search discovery (Engine support). No Graph / knowledge base / Claude.
 * Produces bounded hit-set evidence for StageGate {@link StageGate#KIND_HIT_SET}.
 */
public final class MapSearchDiscovery {

    public static final int DEFAULT_MAX_FILES = 400;
    public static final int DEFAULT_TOP_K = 8;
    public static final int DEFAULT_MAX_PAYLOAD_CHARS = 8192;

    public static final class Budget {
        public final int maxFiles;
        public final int topK;
        public final int maxPayloadChars;

        public Budget(int maxFiles, int topK, int maxPayloadChars) {
            this.maxFiles = maxFiles <= 0 ? DEFAULT_MAX_FILES : maxFiles;
            this.topK = topK <= 0 ? DEFAULT_TOP_K : topK;
            this.maxPayloadChars = maxPayloadChars <= 0 ? DEFAULT_MAX_PAYLOAD_CHARS : maxPayloadChars;
        }

        public static Budget defaults() {
            return new Budget(DEFAULT_MAX_FILES, DEFAULT_TOP_K, DEFAULT_MAX_PAYLOAD_CHARS);
        }
    }

    public static final class Hit {
        public final String path;
        public final String excerpt;
        public final int score;

        public Hit(String path, String excerpt, int score) {
            this.path = path;
            this.excerpt = excerpt == null ? "" : excerpt;
            this.score = score;
        }
    }

    public static final class Result {
        public final List<Hit> hits;
        public final List<String> gaps;
        public final List<String> clarifyQuestions;
        public final boolean truncated;
        public final int filesScanned;
        public final List<String> keywords;
        public final String payload;

        Result(
                List<Hit> hits,
                List<String> gaps,
                List<String> clarifyQuestions,
                boolean truncated,
                int filesScanned,
                List<String> keywords,
                String payload) {
            this.hits = Collections.unmodifiableList(new ArrayList<Hit>(hits));
            this.gaps = Collections.unmodifiableList(new ArrayList<String>(gaps));
            this.clarifyQuestions = Collections.unmodifiableList(new ArrayList<String>(clarifyQuestions));
            this.truncated = truncated;
            this.filesScanned = filesScanned;
            this.keywords = Collections.unmodifiableList(new ArrayList<String>(keywords));
            this.payload = payload;
        }

        public boolean needsClarification() {
            return !clarifyQuestions.isEmpty();
        }

        public List<String> hitPaths() {
            List<String> paths = new ArrayList<String>();
            for (Hit h : hits) {
                paths.add(h.path);
            }
            return paths;
        }
    }

    private MapSearchDiscovery() {
    }

    public static Result run(Path workspaceRoot, String requirement, Budget budget) throws IOException {
        Budget b = budget == null ? Budget.defaults() : budget;
        final List<String> keywords = keywords(requirement);
        final List<Scored> scored = new ArrayList<Scored>();
        final int[] visited = new int[] {0};
        final boolean[] hitCap = new boolean[] {false};

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
                if (visited[0] >= b.maxFiles) {
                    hitCap[0] = true;
                    return FileVisitResult.TERMINATE;
                }
                visited[0]++;
                Path rel = workspaceRoot.relativize(file);
                String path = rel.toString().replace('\\', '/');
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

        List<Hit> hits = new ArrayList<Hit>();
        for (int i = 0; i < scored.size() && i < b.topK; i++) {
            Scored s = scored.get(i);
            hits.add(new Hit(s.path, s.excerpt, s.score));
        }

        DiscoveryGaps.Outcome gapOut = DiscoveryGaps.detect(requirement, hits);
        String payload = formatPayload(hits, gapOut, hitCap[0], visited[0], keywords, b);
        boolean truncated = hitCap[0] || payload.length() > b.maxPayloadChars;
        if (payload.length() > b.maxPayloadChars) {
            payload = payload.substring(0, b.maxPayloadChars) + "\n# truncated-payload\n";
        }

        return new Result(
                hits,
                gapOut.gaps,
                gapOut.questions,
                truncated,
                visited[0],
                keywords,
                payload);
    }

    static String formatPayload(
            List<Hit> hits,
            DiscoveryGaps.Outcome gapOut,
            boolean fileCapHit,
            int filesScanned,
            List<String> keywords,
            Budget budget) {
        StringBuilder sb = new StringBuilder();
        sb.append("# discovery.hit-set\n");
        sb.append("strategy: map+search\n");
        sb.append("truncated: ").append(fileCapHit ? "true" : "false").append('\n');
        sb.append("files-scanned: ").append(filesScanned).append('\n');
        sb.append("max-files: ").append(budget.maxFiles).append('\n');
        sb.append("max-payload-chars: ").append(budget.maxPayloadChars).append('\n');
        sb.append("keywords: ").append(keywords).append('\n');
        sb.append('\n').append("## hits\n");
        for (Hit h : hits) {
            sb.append("- ").append(h.path)
                    .append(" | score=").append(h.score)
                    .append(" | ").append(h.excerpt).append('\n');
        }
        sb.append('\n').append("## related-paths\n");
        for (Hit h : hits) {
            sb.append(h.path).append('\n');
        }
        sb.append('\n').append("## gaps\n");
        if (gapOut.gaps.isEmpty()) {
            sb.append("(none)\n");
        } else {
            for (String g : gapOut.gaps) {
                sb.append("- ").append(g).append('\n');
            }
        }
        sb.append('\n').append("## clarify-questions\n");
        if (gapOut.questions.isEmpty()) {
            sb.append("(none)\n");
        } else {
            for (String q : gapOut.questions) {
                sb.append("- ").append(q).append('\n');
            }
        }
        return sb.toString();
    }

    static List<String> keywords(String requirement) {
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
        // Also pick unique path-like tokens from requirement (e.g. OrderApi)
        if (requirement != null) {
            for (String token : requirement.split("[^A-Za-z0-9_./-]+")) {
                if (token.length() >= 4 && Character.isUpperCase(token.charAt(0))) {
                    String lower = token.toLowerCase(Locale.ROOT);
                    if (!keys.contains(lower)) {
                        keys.add(lower);
                    }
                }
            }
        }
        if (keys.isEmpty()) {
            keys.add("api");
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
