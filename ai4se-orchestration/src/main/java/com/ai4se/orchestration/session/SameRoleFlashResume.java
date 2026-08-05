package com.ai4se.orchestration.session;

import com.ai4se.orchestration.analysis.PlanRecords;
import com.ai4se.orchestration.analysis.StageGateException;
import com.ai4se.orchestration.development.DevPackageBuilder;
import com.ai4se.orchestration.verification.DefectPackageWriter;
import com.ai4se.orchestration.verification.VerifyPackageBuilder;
import com.ai4se.orchestration.workflow.StoryWorkflowMachine;
import com.ai4se.orchestration.workflow.StoryWorkflowState;
import com.ai4se.orchestration.workflow.WorkflowStage;
import com.ai4se.orchestration.workflow.WorkflowStatus;
import com.ai4se.runtime.common.util.Strings;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Appendix A · 闪断 Resume（同角色）.
 * After process death, Control reloads {@code .story} state, records Session {@code resume},
 * and proves Allowed / Defect durable artifacts were not lost. Does not use Runtime Checkpoint
 * as the product Resume story (ADR-0016).
 */
public final class SameRoleFlashResume {

    public static final String AUDIT_FILE = "flash-resume.md";

    private SameRoleFlashResume() {
    }

    /**
     * Resume same-role work after an interrupt. Requires workflow RUNNING.
     * Rebuilds the current-stage package so the next Adapter call is not empty-context.
     */
    public static ResumeSnapshot resumeAfterInterrupt(
            Path workspace, String storyId, String reason) throws IOException {
        if (Strings.isBlank(reason)) {
            throw new StageGateException("Flash resume requires reason");
        }
        StoryWorkflowState state = StoryWorkflowMachine.load(workspace, storyId);
        if (state.status() != WorkflowStatus.RUNNING) {
            throw new StageGateException(
                    "Flash resume only when RUNNING, was " + state.status());
        }

        WorkflowStage stage = state.stage();
        List<String> allowed = Collections.emptyList();
        Path defectOrNull = DefectPackageWriter.latest(workspace, storyId);

        // Durable gates by stage — interrupt must not wipe Plan/Defect from disk
        if (stage == WorkflowStage.DEVELOPMENT
                || stage == WorkflowStage.VERIFICATION
                || stage == WorkflowStage.REVIEW
                || stage == WorkflowStage.DELIVERY) {
            allowed = PlanRecords.readAllowedFiles(workspace, storyId);
            if (allowed.isEmpty()) {
                throw new StageGateException(
                        "Flash resume lost Allowed Files under .story — cannot continue");
            }
        }
        if (stage == WorkflowStage.DEVELOPMENT && defectOrNull != null) {
            // re-Dev after FAIL: Defect must still be on disk
            if (!Files.isRegularFile(defectOrNull)) {
                throw new StageGateException("Flash resume lost Defect Package");
            }
        }

        SessionDecisionRecorder.SessionDecision decision =
                SessionDecisionRecorder.resumeSameRole(
                        workspace, storyId, stage, "flash-interrupt: " + reason.trim());

        Path rebuiltPackage = rebuildPackageForStage(workspace, storyId, stage, defectOrNull);
        if (stage == WorkflowStage.DEVELOPMENT && defectOrNull != null) {
            DevPackageBuilder.requirePresent(workspace, storyId);
        }

        Path audit = writeAudit(
                workspace, storyId, stage, decision, allowed, defectOrNull, rebuiltPackage, reason);
        return new ResumeSnapshot(
                storyId,
                stage,
                decision.sessionId,
                decision.hop,
                allowed,
                defectOrNull,
                rebuiltPackage,
                audit);
    }

    private static Path rebuildPackageForStage(
            Path workspace, String storyId, WorkflowStage stage, Path defectOrNull)
            throws IOException {
        switch (stage) {
            case DEVELOPMENT:
                return DevPackageBuilder.build(workspace, storyId);
            case VERIFICATION:
                int round = VerifyPackageBuilder.nextRound(workspace, storyId);
                return VerifyPackageBuilder.build(
                        workspace, storyId, round, "mvn -q test", defectOrNull);
            default:
                // Analysis/Planning/Review/Delivery: no package rebuild required for this Wave
                return null;
        }
    }

    private static Path writeAudit(
            Path workspace,
            String storyId,
            WorkflowStage stage,
            SessionDecisionRecorder.SessionDecision decision,
            List<String> allowed,
            Path defectOrNull,
            Path rebuiltPackage,
            String reason) throws IOException {
        Path dir = SessionDecisionRecorder.sessionsDir(workspace, storyId);
        Files.createDirectories(dir);
        Path path = dir.resolve(AUDIT_FILE);
        StringBuilder sb = new StringBuilder();
        sb.append("# Flash Resume Audit\n\n");
        sb.append("- story_id: ").append(storyId).append('\n');
        sb.append("- stage: ").append(stage.name()).append('\n');
        sb.append("- decision: resume\n");
        sb.append("- session_id: ").append(decision.sessionId).append('\n');
        sb.append("- hop: ").append(decision.hop).append('\n');
        sb.append("- reason: ").append(reason.trim()).append('\n');
        sb.append("- at: ").append(Instant.now()).append('\n');
        sb.append("- checkpoint_product: false\n");
        sb.append("- source_of_truth: .story/\n\n");
        sb.append("## retained\n\n");
        sb.append("- allowed_files: ").append(allowed.isEmpty() ? "(n/a for stage)" : allowed).append('\n');
        sb.append("- defect: ").append(defectOrNull == null ? "(none)" : defectOrNull).append('\n');
        sb.append("- rebuilt_package: ")
                .append(rebuiltPackage == null ? "(none)" : rebuiltPackage)
                .append('\n');
        Files.write(path, sb.toString().getBytes(StandardCharsets.UTF_8));
        return path;
    }

    public static final class ResumeSnapshot {
        public final String storyId;
        public final WorkflowStage stage;
        public final String sessionId;
        public final int hop;
        public final List<String> allowedFiles;
        public final Path defectOrNull;
        public final Path rebuiltPackageOrNull;
        public final Path auditFile;

        public ResumeSnapshot(
                String storyId,
                WorkflowStage stage,
                String sessionId,
                int hop,
                List<String> allowedFiles,
                Path defectOrNull,
                Path rebuiltPackageOrNull,
                Path auditFile) {
            this.storyId = storyId;
            this.stage = stage;
            this.sessionId = sessionId;
            this.hop = hop;
            this.allowedFiles = Collections.unmodifiableList(new ArrayList<String>(allowedFiles));
            this.defectOrNull = defectOrNull;
            this.rebuiltPackageOrNull = rebuiltPackageOrNull;
            this.auditFile = auditFile;
        }
    }
}
