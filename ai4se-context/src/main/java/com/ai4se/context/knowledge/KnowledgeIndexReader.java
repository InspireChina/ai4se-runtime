package com.ai4se.context.knowledge;

import com.ai4se.context.story.StoryRequirement;
import com.ai4se.runtime.common.util.Strings;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads {@code .ai4se/index/knowledge.yaml} and resolves loadable hits for Context Builder.
 * Problem class: knowledge write-only / read side missing — Builder must consume index IDs.
 */
public final class KnowledgeIndexReader {

    private static final Pattern STALE_SECTION = Pattern.compile(
            "(?ms)^##\\s+Marked Stale\\s*$([\\s\\S]*?)(?=^##\\s+|\\z)");
    private static final Pattern STALE_ID = Pattern.compile("(?m)^\\s*-\\s*(?:id:\\s*)?([A-Za-z0-9._-]+)\\s*$");

    private KnowledgeIndexReader() {
    }

    public static Path indexPath(Path workspace) {
        return workspace.resolve(".ai4se").resolve("index").resolve("knowledge.yaml");
    }

    /**
     * Resolve active entries matching story tags/refs or keyword overlap with goal/acceptance.
     * Only active/verified entries with a body are loadable. Candidate, stale and retired entries
     * are deliberately excluded: a context package must never treat a model draft or knowledge
     * invalidated by a later delivery as repository truth.
     */
    public static List<KnowledgeHit> resolveHits(Path workspace, String storyId, StoryRequirement requirement)
            throws IOException {
        Path index = indexPath(workspace);
        if (!Files.isRegularFile(index)) {
            return Collections.emptyList();
        }
        List<IndexEntry> entries = parseIndex(new String(Files.readAllBytes(index), StandardCharsets.UTF_8));
        Set<String> journalStale = staleIdsFromStoryJournals(workspace);
        Set<String> needles = needles(storyId, requirement);
        List<KnowledgeHit> hits = new ArrayList<KnowledgeHit>();
        for (IndexEntry e : entries) {
            if (!e.isLoadable() || journalStale.contains(e.id)
                    || Strings.isBlank(e.id) || Strings.isBlank(e.path)) {
                continue;
            }
            if (!matches(e, storyId, needles)) {
                continue;
            }
            Path body = workspace.resolve(e.path.replace('\\', '/'));
            if (!Files.isRegularFile(body)) {
                continue;
            }
            hits.add(new KnowledgeHit(e.id, e.path, e.kind, e.tags, e.refs, e.sourcePaths));
        }
        return Collections.unmodifiableList(hits);
    }

    /**
     * Returns relevant stale knowledge as metadata only.  A stale body must not be injected as
     * a current fact; the caller uses its source paths to direct a fresh read of current HEAD.
     */
    public static List<KnowledgeHit> resolveStaleHits(
            Path workspace, String storyId, StoryRequirement requirement) throws IOException {
        Path index = indexPath(workspace);
        if (!Files.isRegularFile(index)) {
            return Collections.emptyList();
        }
        Set<String> journalStale = staleIdsFromStoryJournals(workspace);
        Set<String> needles = needles(storyId, requirement);
        List<KnowledgeHit> hits = new ArrayList<KnowledgeHit>();
        for (IndexEntry e : parseIndex(new String(Files.readAllBytes(index), StandardCharsets.UTF_8))) {
            boolean stale = "stale".equals(e.status) || journalStale.contains(e.id);
            if (!stale || Strings.isBlank(e.id) || !matches(e, storyId, needles)) {
                continue;
            }
            hits.add(new KnowledgeHit(e.id, e.path, e.kind, e.tags, e.refs, e.sourcePaths));
        }
        return Collections.unmodifiableList(hits);
    }

    private static Set<String> staleIdsFromStoryJournals(Path workspace) throws IOException {
        Path stories = workspace.resolve(".story");
        if (!Files.isDirectory(stories)) {
            return Collections.emptySet();
        }
        Set<String> out = new LinkedHashSet<String>();
        try (java.util.stream.Stream<Path> paths = Files.list(stories)) {
            java.util.Iterator<Path> iterator = paths.filter(Files::isDirectory).iterator();
            while (iterator.hasNext()) {
                Path journal = iterator.next().resolve("lifecycle/knowledge-stale.md");
                if (!Files.isRegularFile(journal)) {
                    continue;
                }
                Matcher section = STALE_SECTION.matcher(
                        new String(Files.readAllBytes(journal), StandardCharsets.UTF_8));
                if (!section.find()) {
                    continue;
                }
                Matcher ids = STALE_ID.matcher(section.group(1));
                while (ids.find()) {
                    out.add(ids.group(1));
                }
            }
        }
        return Collections.unmodifiableSet(out);
    }

    private static Set<String> needles(String storyId, StoryRequirement requirement) {
        Set<String> out = new LinkedHashSet<String>();
        if (!Strings.isBlank(storyId)) {
            out.add(storyId.toLowerCase(Locale.ROOT));
            out.add("story-" + storyId.toLowerCase(Locale.ROOT));
        }
        if (requirement != null) {
            addTokens(out, requirement.goal());
            if (requirement.acceptance() != null) {
                for (String a : requirement.acceptance()) {
                    addTokens(out, a);
                }
            }
        }
        return out;
    }

    private static void addTokens(Set<String> out, String text) {
        if (Strings.isBlank(text)) {
            return;
        }
        String[] parts = text.toLowerCase(Locale.ROOT).split("[^a-z0-9_\\u4e00-\\u9fff]+");
        for (String p : parts) {
            if (p.length() >= 3) {
                out.add(p);
            }
        }
    }

    private static boolean matches(IndexEntry e, String storyId, Set<String> needles) {
        String blob = (e.id + " " + e.path + " " + e.kind + " " + e.tags + " " + e.refs)
                .toLowerCase(Locale.ROOT);
        if (!Strings.isBlank(storyId)) {
            String sid = storyId.toLowerCase(Locale.ROOT);
            if (blob.contains("story-" + sid) || blob.contains(".story/" + sid)
                    || e.refs.toLowerCase(Locale.ROOT).contains(sid)) {
                return true;
            }
        }
        for (String n : needles) {
            if (n.length() >= 3 && blob.contains(n)) {
                return true;
            }
        }
        // learning entries with no tags still usable when kind=learning and story tag missing:
        // only match via needles — if empty needles, return false
        return false;
    }

    static List<IndexEntry> parseIndex(String yaml) {
        List<IndexEntry> out = new ArrayList<IndexEntry>();
        if (Strings.isBlank(yaml) || yaml.contains("entries: []")) {
            // still parse list items that may appear after stub
        }
        IndexEntry current = null;
        for (String raw : yaml.split("\n")) {
            String line = raw.replace("\t", "    ");
            String t = line.trim();
            if (t.startsWith("- id:") || t.startsWith("-id:")) {
                if (current != null && !Strings.isBlank(current.id)) {
                    out.add(current);
                }
                current = new IndexEntry();
                current.id = afterColon(t);
            } else if (current == null) {
                continue;
            } else if (t.startsWith("id:")) {
                current.id = afterColon(t);
            } else if (t.startsWith("path:")) {
                current.path = afterColon(t);
            } else if (t.startsWith("kind:")) {
                current.kind = afterColon(t);
            } else if (t.startsWith("tags:")) {
                current.tags = afterColon(t);
            } else if (t.startsWith("refs:")) {
                current.refs = afterColon(t);
            } else if (t.startsWith("status:")) {
                current.status = afterColon(t).toLowerCase(Locale.ROOT);
            } else if (t.startsWith("source_paths:")) {
                current.sourcePaths = afterColon(t);
            } else if (t.startsWith("source_commit:")) {
                current.sourceCommit = afterColon(t);
            }
        }
        if (current != null && !Strings.isBlank(current.id)) {
            out.add(current);
        }
        return out;
    }

    private static String afterColon(String line) {
        int i = line.indexOf(':');
        if (i < 0) {
            return "";
        }
        return line.substring(i + 1).trim();
    }

    public static final class KnowledgeHit {
        private final String id;
        private final String path;
        private final String kind;
        private final String tags;
        private final String refs;
        private final String sourcePaths;

        public KnowledgeHit(String id, String path, String kind, String tags) {
            this(id, path, kind, tags, "", "");
        }

        public KnowledgeHit(
                String id, String path, String kind, String tags, String refs, String sourcePaths) {
            this.id = id;
            this.path = path;
            this.kind = kind == null ? "" : kind;
            this.tags = tags == null ? "" : tags;
            this.refs = refs == null ? "" : refs;
            this.sourcePaths = sourcePaths == null ? "" : sourcePaths;
        }

        public String id() {
            return id;
        }

        public String path() {
            return path;
        }

        public String kind() {
            return kind;
        }

        public String tags() {
            return tags;
        }

        public String refs() {
            return refs;
        }

        public String sourcePaths() {
            return sourcePaths;
        }
    }

    static final class IndexEntry {
        String id = "";
        String path = "";
        String kind = "";
        String tags = "";
        String refs = "";
        String sourcePaths = "";
        String sourceCommit = "";
        String status = "active";

        boolean isLoadable() {
            return "active".equals(status) || "verified".equals(status);
        }
    }
}
