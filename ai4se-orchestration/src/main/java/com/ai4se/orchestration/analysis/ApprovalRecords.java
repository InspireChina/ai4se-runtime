package com.ai4se.orchestration.analysis;

import com.ai4se.runtime.common.util.Strings;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

/** Plan Approval record — required before Development (may be manual). */
public final class ApprovalRecords {

    public static final String FILE = "approval.md";

    private ApprovalRecords() {
    }

    public static void approvePlan(Path workspace, String storyId, String approver, String note)
            throws IOException {
        approvePlan(workspace, storyId, approver, note, "human");
    }

    public static void approvePlan(
            Path workspace, String storyId, String approver, String note, String mode)
            throws IOException {
        if (Strings.isBlank(approver)) {
            throw new StageGateException("Plan Approval requires approver");
        }
        PlanRecords.requireFormalPlanWithAllowed(workspace, storyId);
        Path dir = PlanRecords.planningDir(workspace, storyId);
        Files.createDirectories(dir);
        String modeValue = Strings.isBlank(mode) ? "human" : mode.trim();
        String body = ""
                + "# Plan Approval\n\n"
                + "- decision: APPROVED\n"
                + "- mode: " + modeValue + "\n"
                + "- approver: " + approver.trim() + "\n"
                + "- at: " + Instant.now().toString() + "\n"
                + "- note: " + (Strings.isBlank(note) ? "" : note.trim()) + "\n";
        Files.write(dir.resolve(FILE), body.getBytes(StandardCharsets.UTF_8));
    }

    public static boolean isApproved(Path workspace, String storyId) throws IOException {
        Path path = PlanRecords.planningDir(workspace, storyId).resolve(FILE);
        if (!Files.isRegularFile(path)) {
            return false;
        }
        String text = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        return text.contains("decision: APPROVED");
    }

    public static void requireApproved(Path workspace, String storyId) throws IOException {
        if (!isApproved(workspace, storyId)) {
            throw new StageGateException(
                    "Plan not approved — cannot enter Development (record approval first)");
        }
    }
}
