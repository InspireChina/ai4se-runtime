package com.ai4se.orchestration.production;

import com.ai4se.orchestration.evidence.PathwayEvidenceWriter.SpineDisclosure;
import com.ai4se.orchestration.pathway.PathwayRunner.PathwayResult;
import com.ai4se.orchestration.workflow.StoryWorkflowState;
import java.nio.file.Path;

/**
 * Product terminal for a production Story run.
 *
 * <p>M1 success ends at {@link #TERMINAL_AWAITING_HUMAN_ACCEPTANCE} — never forged human acceptance.
 */
public final class ProductionRunResult {

    public static final String TERMINAL_AWAITING_HUMAN_ACCEPTANCE = "AWAITING_HUMAN_ACCEPTANCE";

    public final String storyId;
    public final String terminalStatus;
    public final String commitShaOrNull;
    public final Path evidenceRoot;
    public final StoryWorkflowState finalState;
    public final SpineDisclosure spine;
    public final int maxDevelopmentRounds;

    public ProductionRunResult(
            String storyId,
            String terminalStatus,
            String commitShaOrNull,
            Path evidenceRoot,
            StoryWorkflowState finalState,
            SpineDisclosure spine,
            int maxDevelopmentRounds) {
        this.storyId = storyId;
        this.terminalStatus = terminalStatus;
        this.commitShaOrNull = commitShaOrNull;
        this.evidenceRoot = evidenceRoot;
        this.finalState = finalState;
        this.spine = spine;
        this.maxDevelopmentRounds = maxDevelopmentRounds;
    }

    static ProductionRunResult fromPathway(PathwayResult pathway, int maxDevelopmentRounds) {
        return new ProductionRunResult(
                pathway.storyId,
                TERMINAL_AWAITING_HUMAN_ACCEPTANCE,
                pathway.commitShaOrNull,
                pathway.evidenceRoot,
                pathway.finalState,
                pathway.spine,
                maxDevelopmentRounds);
    }
}
