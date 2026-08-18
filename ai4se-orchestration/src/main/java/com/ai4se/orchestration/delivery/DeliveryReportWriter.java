package com.ai4se.orchestration.delivery;

import com.ai4se.orchestration.analysis.EffectiveConstraintBundle;
import com.ai4se.orchestration.development.DevelopmentRecords;
import com.ai4se.orchestration.review.ReviewRecords;
import com.ai4se.orchestration.verification.DefectPackageWriter;
import com.ai4se.orchestration.verification.VerificationControl;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Human-readable handoff written only after PASS review and local delivery. */
public final class DeliveryReportWriter {

    public static final String FILE = "delivery-report.md";

    private DeliveryReportWriter() {
    }

    public static Path write(Path workspace, String storyId, String commitShaOrNull) throws IOException {
        Path dir = DeliveryRecords.deliveryDir(workspace, storyId);
        Files.createDirectories(dir);
        List<String> changed = DevelopmentRecords.readChangedFiles(workspace, storyId);
        boolean proven = VerificationControl.allAcceptanceProven(workspace, storyId);
        int defects = countDefects(workspace, storyId);
        StringBuilder body = new StringBuilder("# Delivery Report\n\n");
        body.append("- story_id: ").append(storyId).append('\n');
        body.append("- local_commit: ").append(commitShaOrNull == null ? "(awaiting human commit)" : commitShaOrNull)
                .append('\n');
        body.append("- review: ").append(ReviewRecords.readDecision(workspace, storyId)).append('\n');
        body.append("- acceptance_all_proven: ").append(proven).append('\n');
        body.append("- defect_rounds: ").append(defects).append('\n');
        body.append("- constraints: ").append(EffectiveConstraintBundle.markdownPath(workspace, storyId)).append('\n');
        body.append("\n## Changed Files\n\n");
        for (String path : changed) {
            body.append("- ").append(path).append('\n');
        }
        body.append("\n## Verification and Human Acceptance\n\n")
                .append("- Verification evidence: .story/").append(storyId).append("/verification/\n")
                .append("- Frozen acceptance probes determine AC proof; command success alone is not acceptance.\n")
                .append("- This report is not a push or release authorization. Human acceptance remains required.\n")
                .append("- Rollback: revert the local commit after the normal customer review procedure.\n");
        Path report = dir.resolve(FILE);
        Files.write(report, body.toString().getBytes(StandardCharsets.UTF_8));
        return report;
    }

    private static int countDefects(Path workspace, String storyId) throws IOException {
        Path dir = DefectPackageWriter.defectsDir(workspace, storyId);
        if (!Files.isDirectory(dir)) {
            return 0;
        }
        int count = 0;
        for (Path ignored : Files.newDirectoryStream(dir, "defect-round-*.md")) {
            count++;
        }
        return count;
    }
}
