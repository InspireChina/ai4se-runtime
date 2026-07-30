package com.ai4se.runtime.demo.analysis;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Pipeline-built context for this requirement — not Facts, not Plan. */
public final class RepositoryContext {

    private final List<String> candidateFiles;
    private final List<String> relevantModules;
    private final List<String> topK;
    private final String confidence;
    private final List<String> unknown;
    private final List<String> needClarification;

    public RepositoryContext(
            List<String> candidateFiles,
            List<String> relevantModules,
            List<String> topK,
            String confidence,
            List<String> unknown,
            List<String> needClarification) {
        this.candidateFiles = copy(candidateFiles);
        this.relevantModules = copy(relevantModules);
        this.topK = copy(topK);
        this.confidence = confidence == null ? "low" : confidence;
        this.unknown = copy(unknown);
        this.needClarification = copy(needClarification);
    }

    private static List<String> copy(List<String> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<String>(source));
    }

    public List<String> getCandidateFiles() { return candidateFiles; }
    public List<String> getRelevantModules() { return relevantModules; }
    public List<String> getTopK() { return topK; }
    public String getConfidence() { return confidence; }
    public List<String> getUnknown() { return unknown; }
    public List<String> getNeedClarification() { return needClarification; }
}
