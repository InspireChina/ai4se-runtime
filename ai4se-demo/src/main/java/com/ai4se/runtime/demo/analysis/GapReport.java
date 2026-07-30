package com.ai4se.runtime.demo.analysis;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Gap Detection output. */
public final class GapReport {

    public enum Status { CLEAR, ASSUMABLE, BLOCKED }

    private final Status status;
    private final List<String> known;
    private final List<String> unknown;
    private final List<String> assumptions;
    private final List<String> risks;
    private final List<String> decisionNeeded;
    private final int blockingGapCount;
    private final int assumableGapCount;

    public GapReport(
            Status status,
            List<String> known,
            List<String> unknown,
            List<String> assumptions,
            List<String> risks,
            List<String> decisionNeeded,
            int blockingGapCount,
            int assumableGapCount) {
        this.status = status;
        this.known = copy(known);
        this.unknown = copy(unknown);
        this.assumptions = copy(assumptions);
        this.risks = copy(risks);
        this.decisionNeeded = copy(decisionNeeded);
        this.blockingGapCount = blockingGapCount;
        this.assumableGapCount = assumableGapCount;
    }

    private static List<String> copy(List<String> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<String>(source));
    }

    public Status getStatus() { return status; }
    public List<String> getKnown() { return known; }
    public List<String> getUnknown() { return unknown; }
    public List<String> getAssumptions() { return assumptions; }
    public List<String> getRisks() { return risks; }
    public List<String> getDecisionNeeded() { return decisionNeeded; }
    public int getBlockingGapCount() { return blockingGapCount; }
    public int getAssumableGapCount() { return assumableGapCount; }

    public boolean mayPlan() {
        return status == Status.CLEAR || status == Status.ASSUMABLE;
    }
}
