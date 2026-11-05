package com.ai4se.orchestration.production;

import com.ai4se.orchestration.evidence.PathwayEvidenceWriter.SpineDisclosure;
import com.ai4se.orchestration.pathway.PathwayRunner.PathwayResult;
import com.ai4se.orchestration.run.ProductionTerminal;
import com.ai4se.orchestration.workflow.StoryWorkflowState;
import java.nio.file.Path;

/**
 * Product terminal for a production Story run.
 *
 * <p>M1 success ends at {@link ProductionTerminal#AWAITING_HUMAN_ACCEPTANCE} — never forged human
 * acceptance. Failure/stop terminals carry machine exit codes (PR3).
 */
public final class ProductionRunResult {

    public static final String TERMINAL_AWAITING_HUMAN_ACCEPTANCE =
            ProductionTerminal.AWAITING_HUMAN_ACCEPTANCE.name();

    public final String storyId;
    public final String terminalStatus;
    public final ProductionTerminal terminal;
    public final int exitCode;
    public final String commitShaOrNull;
    public final Path evidenceRoot;
    public final StoryWorkflowState finalState;
    public final SpineDisclosure spine;
    public final int maxDevelopmentRounds;
    public final String detailOrNull;
    public final Path runDirOrNull;

    public ProductionRunResult(
            String storyId,
            ProductionTerminal terminal,
            String commitShaOrNull,
            Path evidenceRoot,
            StoryWorkflowState finalState,
            SpineDisclosure spine,
            int maxDevelopmentRounds,
            String detailOrNull,
            Path runDirOrNull) {
        this.storyId = storyId;
        this.terminal = terminal == null
                ? ProductionTerminal.FAILED_POLICY
                : terminal;
        this.terminalStatus = this.terminal.name();
        this.exitCode = this.terminal.exitCode;
        this.commitShaOrNull = commitShaOrNull;
        this.evidenceRoot = evidenceRoot;
        this.finalState = finalState;
        this.spine = spine;
        this.maxDevelopmentRounds = maxDevelopmentRounds;
        this.detailOrNull = detailOrNull;
        this.runDirOrNull = runDirOrNull;
    }

    public boolean succeeded() {
        return terminal.isSuccess();
    }

    static ProductionRunResult fromPathway(
            PathwayResult pathway, int maxDevelopmentRounds, Path runDir) {
        return new ProductionRunResult(
                pathway.storyId,
                ProductionTerminal.AWAITING_HUMAN_ACCEPTANCE,
                pathway.commitShaOrNull,
                pathway.evidenceRoot,
                pathway.finalState,
                pathway.spine,
                maxDevelopmentRounds,
                null,
                runDir);
    }

    static ProductionRunResult stopped(
            String storyId,
            ProductionTerminal terminal,
            int maxDevelopmentRounds,
            String detail,
            Path runDir,
            StoryWorkflowState stateOrNull) {
        return new ProductionRunResult(
                storyId,
                terminal,
                null,
                null,
                stateOrNull,
                null,
                maxDevelopmentRounds,
                detail,
                runDir);
    }
}
