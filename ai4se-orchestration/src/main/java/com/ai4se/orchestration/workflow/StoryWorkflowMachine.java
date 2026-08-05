package com.ai4se.orchestration.workflow;

import com.ai4se.orchestration.analysis.StageArtifactGate;
import com.ai4se.orchestration.analysis.StageGateException;
import com.ai4se.orchestration.session.SessionDecisionRecorder;
import com.ai4se.runtime.common.util.Strings;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Workflow + Control: linear stages; Stop with reason;
 * state under {@code .story/<id>/workflow-state.properties}.
 * <p>
 * W5/W6: {@link StageArtifactGate} enforces Discovery/Gap/Plan/Approval/Dev before advances.
 * W9: each stage hop records Session decision ({@code resume|new}) via {@link SessionDecisionRecorder}.
 * Does <b>not</b> interpret model chat as control.
 */
public final class StoryWorkflowMachine {

    public static final String STATE_FILE = "workflow-state.properties";

    private StoryWorkflowMachine() {
    }

    public static Path statePath(Path workspace, String storyId) {
        return workspace.resolve(".story").resolve(storyId).resolve(STATE_FILE);
    }

    /** Start (or resume file) at ANALYSIS / RUNNING. Requires Story directory. */
    public static StoryWorkflowState start(Path workspace, String storyId) throws IOException {
        Path storyDir = workspace.resolve(".story").resolve(storyId);
        if (!Files.isDirectory(storyDir)) {
            throw new IOException("Story not opened: " + storyDir);
        }
        StoryWorkflowState state = new StoryWorkflowState(
                storyId, WorkflowStage.ANALYSIS, WorkflowStatus.RUNNING, null);
        save(workspace, state);
        SessionDecisionRecorder.openNew(
                workspace, storyId, WorkflowStage.ANALYSIS, "workflow start → Analysis Session");
        return state;
    }

    public static StoryWorkflowState load(Path workspace, String storyId) throws IOException {
        Path path = statePath(workspace, storyId);
        if (!Files.isRegularFile(path)) {
            throw new IOException("Workflow state missing: " + path + " (call start first)");
        }
        Map<String, String> map = readProps(path);
        String stageRaw = map.get("stage");
        String statusRaw = map.get("status");
        if (Strings.isBlank(stageRaw) || Strings.isBlank(statusRaw)) {
            throw new IOException("Corrupt workflow state: " + path);
        }
        WorkflowStage stage = WorkflowStage.valueOf(stageRaw.trim().toUpperCase(Locale.ROOT));
        WorkflowStatus status = WorkflowStatus.valueOf(statusRaw.trim().toUpperCase(Locale.ROOT));
        String reason = map.get("stop_reason");
        if (reason != null && reason.trim().isEmpty()) {
            reason = null;
        }
        return new StoryWorkflowState(storyId, stage, status, reason);
    }

    /** Advance one legal step; W5/W6 artifact gates apply. */
    public static StoryWorkflowState advance(Path workspace, String storyId) throws IOException {
        StoryWorkflowState current = load(workspace, storyId);
        if (!current.isRunnable()) {
            throw new IllegalWorkflowTransitionException(
                    "Cannot advance when status=" + current.status()
                            + (current.stopReason() != null ? " reason=" + current.stopReason() : ""));
        }
        WorkflowStage next = nextStage(current.stage());
        if (next == null) {
            throw new IllegalWorkflowTransitionException(
                    "No further stage after " + current.stage());
        }
        try {
            StageArtifactGate.requireAdvanceAllowed(workspace, storyId, current.stage(), next);
        } catch (StageGateException gate) {
            throw new IllegalWorkflowTransitionException(gate.getMessage());
        }
        StoryWorkflowState updated = new StoryWorkflowState(
                storyId, next, WorkflowStatus.RUNNING, null);
        save(workspace, updated);
        SessionDecisionRecorder.onStageHop(
                workspace,
                storyId,
                current.stage(),
                next,
                "advance " + current.stage() + " → " + next);
        return updated;
    }

    /**
     * W7 Defect loop: Verification FAIL → Development (RUNNING), reason recorded in stop_reason field
     * as loop note is not Stop — we keep RUNNING at DEVELOPMENT.
     */
    public static StoryWorkflowState returnToDevelopment(Path workspace, String storyId, String reason)
            throws IOException {
        StoryWorkflowState current = load(workspace, storyId);
        if (current.stage() != WorkflowStage.VERIFICATION) {
            throw new IllegalWorkflowTransitionException(
                    "returnToDevelopment only from VERIFICATION, was " + current.stage());
        }
        if (Strings.isBlank(reason)) {
            throw new IllegalWorkflowTransitionException("Defect loop requires reason");
        }
        Path note = workspace.resolve(".story").resolve(storyId).resolve("verification")
                .resolve("loop-to-dev.md");
        Files.createDirectories(note.getParent());
        Files.write(
                note,
                ("# Loop to Development\n\n- reason: " + reason.trim() + "\n").getBytes(StandardCharsets.UTF_8));
        StoryWorkflowState updated = new StoryWorkflowState(
                storyId, WorkflowStage.DEVELOPMENT, WorkflowStatus.RUNNING, null);
        save(workspace, updated);
        SessionDecisionRecorder.onStageHop(
                workspace,
                storyId,
                WorkflowStage.VERIFICATION,
                WorkflowStage.DEVELOPMENT,
                "Verify FAIL → Development (Defect loop): " + reason.trim());
        return updated;
    }

    /**
     * Explicit Stop — legal Story endpoint with reason.
     * Not pathway-green; Control records why automatic progress ended.
     */
    public static StoryWorkflowState stop(Path workspace, String storyId, String reason)
            throws IOException {
        if (Strings.isBlank(reason)) {
            throw new IllegalWorkflowTransitionException("Stop requires a non-blank reason");
        }
        StoryWorkflowState current = load(workspace, storyId);
        if (current.status() == WorkflowStatus.STOPPED) {
            throw new IllegalWorkflowTransitionException("Already STOPPED: " + current.stopReason());
        }
        if (current.status() == WorkflowStatus.COMPLETED) {
            throw new IllegalWorkflowTransitionException("Already COMPLETED");
        }
        StoryWorkflowState updated = new StoryWorkflowState(
                storyId, current.stage(), WorkflowStatus.STOPPED, reason.trim());
        save(workspace, updated);
        return updated;
    }

    /** Mark Delivery complete after DeliveryRecords ready (W8). */
    public static StoryWorkflowState complete(Path workspace, String storyId) throws IOException {
        StoryWorkflowState current = load(workspace, storyId);
        if (!current.isRunnable()) {
            throw new IllegalWorkflowTransitionException(
                    "Cannot complete when status=" + current.status());
        }
        if (current.stage() != WorkflowStage.DELIVERY) {
            throw new IllegalWorkflowTransitionException(
                    "Complete only from DELIVERY, was " + current.stage());
        }
        try {
            com.ai4se.orchestration.delivery.DeliveryRecords.requireReady(workspace, storyId);
        } catch (com.ai4se.orchestration.analysis.StageGateException gate) {
            throw new IllegalWorkflowTransitionException(gate.getMessage());
        }
        StoryWorkflowState updated = new StoryWorkflowState(
                storyId, WorkflowStage.DELIVERY, WorkflowStatus.COMPLETED, null);
        save(workspace, updated);
        return updated;
    }

    /**
     * Rejects model-utterance style control. Control API is advance/stop only.
     */
    public static void rejectModelControlUtterance(String utterance) {
        if (Strings.isBlank(utterance)) {
            return;
        }
        String t = utterance.toLowerCase(Locale.ROOT);
        boolean looksLikeControl = t.contains("go to planning")
                || t.contains("skip to development")
                || t.contains("进入 planning")
                || t.contains("跳到开发")
                || t.contains("下一阶段");
        if (looksLikeControl) {
            throw new IllegalWorkflowTransitionException(
                    "Control flow is not in model utterances: " + utterance);
        }
    }

    static WorkflowStage nextStage(WorkflowStage stage) {
        switch (stage) {
            case ANALYSIS:
                return WorkflowStage.PLANNING;
            case PLANNING:
                return WorkflowStage.DEVELOPMENT;
            case DEVELOPMENT:
                return WorkflowStage.VERIFICATION;
            case VERIFICATION:
                return WorkflowStage.REVIEW;
            case REVIEW:
                return WorkflowStage.DELIVERY;
            case DELIVERY:
            default:
                return null;
        }
    }

    public static void save(Path workspace, StoryWorkflowState state) throws IOException {
        Path path = statePath(workspace, state.storyId());
        Files.createDirectories(path.getParent());
        StringBuilder sb = new StringBuilder();
        sb.append("# Story workflow state — written by 03 Control, not by the model\n");
        sb.append("story_id=").append(state.storyId()).append('\n');
        sb.append("stage=").append(state.stage().name()).append('\n');
        sb.append("status=").append(state.status().name()).append('\n');
        sb.append("stop_reason=");
        if (state.stopReason() != null) {
            sb.append(escape(state.stopReason()));
        }
        sb.append('\n');
        Files.write(path, sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\n", "\\n");
    }

    private static Map<String, String> readProps(Path path) throws IOException {
        Map<String, String> map = new LinkedHashMap<String, String>();
        for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
            String t = line.trim();
            if (t.isEmpty() || t.startsWith("#")) {
                continue;
            }
            int eq = t.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = t.substring(0, eq).trim();
            String value = t.substring(eq + 1).trim().replace("\\n", "\n").replace("\\\\", "\\");
            map.put(key, value);
        }
        return map;
    }
}
