package com.ai4se.runtime.demo.analysis;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Analyzer output only — raw/semi-raw repository facts. */
public final class RepositoryFacts {

    private final List<String> modules;
    private final List<String> buildFiles;
    private final List<Hit> hits;
    private final List<String> facts;
    private final Map<String, String> meta;

    public RepositoryFacts(
            List<String> modules,
            List<String> buildFiles,
            List<Hit> hits,
            List<String> facts,
            Map<String, String> meta) {
        this.modules = immutable(modules);
        this.buildFiles = immutable(buildFiles);
        this.hits = hits == null
                ? Collections.<Hit>emptyList()
                : Collections.unmodifiableList(new ArrayList<Hit>(hits));
        this.facts = immutable(facts);
        this.meta = meta == null
                ? Collections.<String, String>emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<String, String>(meta));
    }

    private static List<String> immutable(List<String> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<String>(source));
    }

    public List<String> getModules() { return modules; }
    public List<String> getBuildFiles() { return buildFiles; }
    public List<Hit> getHits() { return hits; }
    public List<String> getFacts() { return facts; }
    public Map<String, String> getMeta() { return meta; }

    public static final class Hit {
        private final String path;
        private final String excerpt;
        private final int score;

        public Hit(String path, String excerpt, int score) {
            this.path = path;
            this.excerpt = excerpt;
            this.score = score;
        }

        public String getPath() { return path; }
        public String getExcerpt() { return excerpt; }
        public int getScore() { return score; }
    }
}
