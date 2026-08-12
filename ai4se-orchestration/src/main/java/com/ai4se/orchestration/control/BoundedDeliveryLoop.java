package com.ai4se.orchestration.control;

import com.ai4se.execution.api.ModelCliAdapter;
import com.ai4se.execution.model.RoleModelConfig;
import com.ai4se.execution.support.ProcessInvoker;
import com.ai4se.orchestration.analysis.StageGateException;
import com.ai4se.orchestration.development.DevAdapterExecution;
import com.ai4se.orchestration.development.DevelopmentRecords;
import com.ai4se.orchestration.support.WorkspaceGit;
import com.ai4se.orchestration.verification.DefectPackageWriter;
import com.ai4se.orchestration.verification.VerificationControl;
import com.ai4se.orchestration.verification.VerificationControl.VerificationRecord;
import com.ai4se.orchestration.verification.VerificationOutcome;
import com.ai4se.orchestration.workflow.StoryWorkflowMachine;
import com.ai4se.orchestration.workflow.StoryWorkflowState;
import com.ai4se.orchestration.workflow.WorkflowStage;
import com.ai4se.runtime.common.util.Strings;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/**
 * Production bounded Dev↔Verify loop (M1 PR2).
 *
 * <p>Does not pre-judge first PASS/FAIL (unlike fixture Script V3/V4). PASS only from
 * Verification Contract. Each round uses a fresh adapter turn; cross-round memory is Git /
 * {@code .story} / Packages only.
 *
 * <p>{@code roundsAlreadyUsed} continues numbering after resume — the hard ceiling is still
 * {@code maxDevelopmentRounds} for the whole Story run (not reset per process).
 */
public final class BoundedDeliveryLoop {

    private BoundedDeliveryLoop() {
    }

    public static BoundedLoopResult run(
            Path workspace,
            String storyId,
            int maxDevelopmentRounds,
            ModelCliAdapter devAdapter,
            Duration adapterTimeout,
            RoleModelConfig roleModels,
            List<String> verifyCommands,
            String changeNote,
            ProcessInvoker invoker) throws IOException {
        return run(
                workspace,
                storyId,
                maxDevelopmentRounds,
                0,
                devAdapter,
                adapterTimeout,
                roleModels,
                verifyCommands,
                changeNote,
                invoker);
    }

    /**
     * @param roundsAlreadyUsed absolute Development rounds already consumed in prior process
     *     invocations; next round index is {@code roundsAlreadyUsed + 1}
     */
    public static BoundedLoopResult run(
            Path workspace,
            String storyId,
            int maxDevelopmentRounds,
            int roundsAlreadyUsed,
            ModelCliAdapter devAdapter,
            Duration adapterTimeout,
            RoleModelConfig roleModels,
            List<String> verifyCommands,
            String changeNote,
            ProcessInvoker invoker) throws IOException {
        if (workspace == null || Strings.isBlank(storyId)) {
            throw new StageGateException("BoundedDeliveryLoop requires workspace and storyId");
        }
        if (maxDevelopmentRounds < 1) {
            throw new StageGateException("maxDevelopmentRounds must be >= 1");
        }
        if (roundsAlreadyUsed < 0) {
            throw new StageGateException("roundsAlreadyUsed must be >= 0");
        }
        if (devAdapter == null) {
            throw new StageGateException("BoundedDeliveryLoop requires Development adapter");
        }
        if (invoker == null) {
            throw new StageGateException("ProcessInvoker required");
        }
        if (verifyCommands == null || verifyCommands.isEmpty()) {
            throw new StageGateException("BoundedDeliveryLoop requires verify commands from entries");
        }
        StoryWorkflowState state = StoryWorkflowMachine.load(workspace, storyId);
        if (state.stage() != WorkflowStage.DEVELOPMENT || !state.isRunnable()) {
            throw new StageGateException(
                    "BoundedDeliveryLoop starts at DEVELOPMENT RUNNING, was "
                            + state.stage() + "/" + state.status());
        }

        if (roundsAlreadyUsed >= maxDevelopmentRounds) {
            return new BoundedLoopResult(
                    RunStopReason.FAILED_VERIFICATION_BUDGET,
                    roundsAlreadyUsed,
                    null,
                    DefectPackageWriter.latest(workspace, storyId));
        }

        FailureFingerprint prevFingerprint = null;
        String prevDiffHash = null;
        VerificationRecord lastVerify = null;
        Path lastDefect = DefectPackageWriter.latest(workspace, storyId);
        RoleModelConfig models = roleModels == null ? RoleModelConfig.empty() : roleModels;
        String baseNote = Strings.isBlank(changeNote) ? "implement within Allowed" : changeNote.trim();

        for (int round = roundsAlreadyUsed + 1; round <= maxDevelopmentRounds; round++) {
            try {
                DevAdapterExecution.submitDevPackage(
                        workspace, storyId, round, devAdapter, adapterTimeout, models);
            } catch (StageGateException e) {
                return new BoundedLoopResult(
                        RunStopReason.FAILED_ADAPTER, round, lastVerify, lastDefect);
            }

            String note = round <= 1 ? baseNote : baseNote + " (after defect)";
            try {
                DevelopmentRecords.recordObservedChanges(workspace, storyId, note, invoker);
            } catch (StageGateException e) {
                return new BoundedLoopResult(
                        RunStopReason.FAILED_POLICY, round, lastVerify, lastDefect);
            }

            String diffHash = WorkspaceGit.businessWorkingTreeDigest(workspace, invoker);

            StoryWorkflowMachine.advance(workspace, storyId); // → VERIFICATION

            VerificationRecord rec;
            try {
                rec = VerificationControl.run(workspace, storyId, verifyCommands, invoker);
            } catch (StageGateException e) {
                if (e.getMessage() != null && e.getMessage().contains("ENV_FAIL")) {
                    return new BoundedLoopResult(
                            RunStopReason.FAILED_ENVIRONMENT, round, lastVerify, lastDefect);
                }
                throw e;
            }
            lastVerify = rec;

            if (rec.outcome == VerificationOutcome.PASS) {
                return new BoundedLoopResult(
                        RunStopReason.PASSED_VERIFICATION, round, rec, lastDefect);
            }

            // FAIL: VerificationControl already wrote Defect + returned to DEVELOPMENT.
            lastDefect = rec.defectOrNull != null
                    ? rec.defectOrNull
                    : DefectPackageWriter.latest(workspace, storyId);
            FailureFingerprint fp = FailureFingerprint.fromVerificationFail(rec);

            if (prevFingerprint != null
                    && prevFingerprint.equals(fp)
                    && prevDiffHash != null
                    && prevDiffHash.equals(diffHash)) {
                return new BoundedLoopResult(
                        RunStopReason.FAILED_NO_PROGRESS, round, rec, lastDefect);
            }
            if (round == maxDevelopmentRounds) {
                return new BoundedLoopResult(
                        RunStopReason.FAILED_VERIFICATION_BUDGET, round, rec, lastDefect);
            }
            prevFingerprint = fp;
            prevDiffHash = diffHash;
            // stage is DEVELOPMENT RUNNING for the next round
        }
        return new BoundedLoopResult(
                RunStopReason.FAILED_VERIFICATION_BUDGET,
                maxDevelopmentRounds,
                lastVerify,
                lastDefect);
    }
}
