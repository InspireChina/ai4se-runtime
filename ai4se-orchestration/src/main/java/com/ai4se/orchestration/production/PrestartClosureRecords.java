package com.ai4se.orchestration.production;

import com.ai4se.execution.support.ProcessInvoker;
import com.ai4se.orchestration.analysis.StageGateException;
import com.ai4se.orchestration.run.ProductionTerminal;
import com.ai4se.orchestration.run.RunLedger;
import com.ai4se.orchestration.support.WorkspaceGit;
import com.ai4se.orchestration.workflow.StoryWorkflowMachine;
import com.ai4se.orchestration.workflow.StoryWorkflowState;
import com.ai4se.orchestration.workflow.WorkflowStage;
import com.ai4se.orchestration.workflow.WorkflowStatus;
import com.ai4se.runtime.common.util.Strings;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Immutable evidence that a Story was deliberately closed before any unattended mutation. */
public final class PrestartClosureRecords {

    public static final String FILE = "planning/closed-before-unattended.properties";

    private PrestartClosureRecords() {
    }

    public static Path close(
            Path workspace, String storyId, String actor, String reason, ProcessInvoker invoker)
            throws IOException {
        if (workspace == null || Strings.isBlank(storyId)) {
            throw new StageGateException("close-prestart requires workspace and story");
        }
        if (Strings.isBlank(actor) || Strings.isBlank(reason)) {
            throw new StageGateException("close-prestart requires non-empty actor and reason");
        }
        if (invoker == null) {
            throw new StageGateException("close-prestart requires process invoker");
        }
        StoryWorkflowState workflow = StoryWorkflowMachine.load(workspace, storyId);
        if ((workflow.stage() != WorkflowStage.ANALYSIS && workflow.stage() != WorkflowStage.PLANNING)
                || workflow.status() != WorkflowStatus.STOPPED) {
            throw new StageGateException("close-prestart only permits a stopped pre-Development Story");
        }
        RunLedger ledger = RunLedger.openExisting(workspace, storyId);
        RunLedger.RunStateSnapshot state = ledger.readState();
        if (!ProductionTerminal.STOPPED_NEEDS_PLAN_APPROVAL.name().equals(state.terminalOrNull)
                && !ProductionTerminal.FAILED_POLICY.name().equals(state.terminalOrNull)) {
            throw new StageGateException("close-prestart requires a pre-Development terminal");
        }
        if (!WorkspaceGit.businessChangedPaths(workspace, invoker).isEmpty()) {
            throw new StageGateException("close-prestart refuses after business source has changed");
        }
        Path record = workspace.resolve(".story").resolve(storyId).resolve(FILE);
        String text = "status=CLOSED_BEFORE_UNATTENDED\n"
                + "story_id=" + storyId.trim() + "\n"
                + "actor=" + actor.trim() + "\n"
                + "reason=" + reason.trim().replace('\n', ' ') + "\n"
                + "business_mutation=none\n"
                + "previous_terminal=" + state.terminalOrNull + "\n";
        if (Files.exists(record)) {
            String existing = new String(Files.readAllBytes(record), StandardCharsets.UTF_8);
            if (!existing.equals(text)) {
                throw new StageGateException("close-prestart record already exists with different content");
            }
            return record;
        }
        Files.createDirectories(record.getParent());
        Files.write(record, text.getBytes(StandardCharsets.UTF_8));
        return record;
    }

    /** A narrow syntactic check used only to unblock a serial successor's immutable evidence. */
    public static boolean isClosedBeforeUnattended(Path storyRoot) {
        if (storyRoot == null) {
            return false;
        }
        Path record = storyRoot.resolve(FILE);
        if (!Files.isRegularFile(record)) {
            return false;
        }
        try {
            String text = new String(Files.readAllBytes(record), StandardCharsets.UTF_8);
            return text.contains("status=CLOSED_BEFORE_UNATTENDED\n")
                    && text.contains("business_mutation=none\n")
                    && (text.contains("previous_terminal=" + ProductionTerminal.STOPPED_NEEDS_PLAN_APPROVAL.name())
                            || text.contains("previous_terminal=" + ProductionTerminal.FAILED_POLICY.name()));
        } catch (IOException ignored) {
            return false;
        }
    }
}
