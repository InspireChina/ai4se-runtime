package com.ai4se.orchestration.development;

import com.ai4se.execution.support.ProcessInvoker;
import com.ai4se.orchestration.analysis.PlanRecords;
import com.ai4se.orchestration.analysis.StageGateException;
import com.ai4se.orchestration.support.WorkspaceGit;
import com.ai4se.runtime.common.util.Strings;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * W6 Development artifacts: changed-file set + change note.
 * Preferred path: {@link #recordObservedChanges} (git porcelain).
 * Diff must be ⊆ Allowed Files; notes must not claim verification green.
 */
public final class DevelopmentRecords {

    public static final String DIR = "development";
    public static final String CHANGED_FILES = "changed-files.md";
    public static final String CHANGE_NOTE = "change-note.md";

    private DevelopmentRecords() {
    }

    public static Path developmentDir(Path workspace, String storyId) {
        return workspace.resolve(".story").resolve(storyId).resolve(DIR);
    }

    /**
     * Observe git status --porcelain and record paths ⊆ Allowed.
     */
    public static void recordObservedChanges(
            Path workspace,
            String storyId,
            String changeNote,
            ProcessInvoker invoker) throws IOException {
        List<String> observed = WorkspaceGit.changedPaths(workspace, invoker);
        List<String> business = new ArrayList<String>();
        for (String p : observed) {
            if (!WorkspaceGit.isIgnorableForMutation(p)) {
                business.add(p);
            }
        }
        persist(workspace, storyId, business, changeNote, true);
        DevPackageBuilder.build(workspace, storyId);
    }

    /**
     * Declared path list — unit tests of scope/self-green only.
     * Production / pathway flows must use {@link #recordObservedChanges}.
     */
    public static void recordDeclaredChanges(
            Path workspace,
            String storyId,
            List<String> changedFiles,
            String changeNote) throws IOException {
        persist(workspace, storyId, changedFiles, changeNote, false);
        DevPackageBuilder.build(workspace, storyId);
    }

    /** @deprecated use {@link #recordObservedChanges} or {@link #recordDeclaredChanges} */
    @Deprecated
    public static void recordChanges(
            Path workspace,
            String storyId,
            List<String> changedFiles,
            String changeNote) throws IOException {
        recordDeclaredChanges(workspace, storyId, changedFiles, changeNote);
    }

    private static void persist(
            Path workspace,
            String storyId,
            List<String> changedFiles,
            String changeNote,
            boolean observed) throws IOException {
        List<String> allowed = PlanRecords.readAllowedFiles(workspace, storyId);
        List<String> changed = normalize(changedFiles);
        if (changed.isEmpty()) {
            throw new StageGateException(
                    observed
                            ? "Observed git diff empty — Development requires at least one changed file"
                            : "Development requires at least one changed file");
        }
        List<String> violations = DiffScopeGuard.findViolations(changed, allowed);
        if (!violations.isEmpty()) {
            throw new StageGateException(
                    "Diff exceeds Allowed Files: " + join(violations));
        }
        String note = changeNote == null ? "" : changeNote;
        List<String> greenHits = SelfGreenScanner.findHits(note);
        if (!greenHits.isEmpty()) {
            throw new StageGateException(
                    "Development must not self-verify green: " + join(greenHits));
        }

        Path dir = developmentDir(workspace, storyId);
        Files.createDirectories(dir);
        StringBuilder filesBody = new StringBuilder();
        filesBody.append("# Changed Files\n\n");
        filesBody.append("- observed: ").append(observed).append('\n');
        filesBody.append('\n');
        for (String f : changed) {
            filesBody.append("- ").append(f).append('\n');
        }
        Files.write(dir.resolve(CHANGED_FILES), filesBody.toString().getBytes(StandardCharsets.UTF_8));

        String noteBody = ""
                + "# Change Note\n\n"
                + note.trim() + "\n\n"
                + "## Disclaimer\n\n"
                + "This stage does not claim Acceptance Pass. Next stage must be Verification.\n";
        Files.write(dir.resolve(CHANGE_NOTE), noteBody.getBytes(StandardCharsets.UTF_8));
    }

    public static boolean hasValidRecord(Path workspace, String storyId) throws IOException {
        Path dir = developmentDir(workspace, storyId);
        return Files.isRegularFile(dir.resolve(CHANGED_FILES))
                && Files.isRegularFile(dir.resolve(CHANGE_NOTE));
    }

    public static List<String> readChangedFiles(Path workspace, String storyId) throws IOException {
        Path path = developmentDir(workspace, storyId).resolve(CHANGED_FILES);
        if (!Files.isRegularFile(path)) {
            throw new StageGateException("Missing development/changed-files.md");
        }
        List<String> out = new ArrayList<String>();
        for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
            String t = line.trim();
            if (t.startsWith("- ") || t.startsWith("* ")) {
                String f = t.substring(2).trim();
                if (f.startsWith("observed:")) {
                    continue;
                }
                if (!f.isEmpty()) {
                    out.add(f);
                }
            }
        }
        return Collections.unmodifiableList(out);
    }

    public static void requireReadyForVerification(Path workspace, String storyId) throws IOException {
        if (!hasValidRecord(workspace, storyId)) {
            throw new StageGateException(
                    "Development incomplete — need changed-files + change-note before Verification");
        }
        DevPackageBuilder.requirePresent(workspace, storyId);
        List<String> allowed = PlanRecords.readAllowedFiles(workspace, storyId);
        List<String> changed = readChangedFiles(workspace, storyId);
        List<String> violations = DiffScopeGuard.findViolations(changed, allowed);
        if (!violations.isEmpty()) {
            throw new StageGateException("Diff exceeds Allowed Files: " + join(violations));
        }
        Path notePath = developmentDir(workspace, storyId).resolve(CHANGE_NOTE);
        String note = new String(Files.readAllBytes(notePath), StandardCharsets.UTF_8);
        List<String> greenHits = SelfGreenScanner.findHits(note);
        if (!greenHits.isEmpty()) {
            throw new StageGateException(
                    "Development must not self-verify green: " + join(greenHits));
        }
    }

    private static List<String> normalize(List<String> files) {
        Set<String> set = new LinkedHashSet<String>();
        if (files == null) {
            return Collections.emptyList();
        }
        for (String f : files) {
            if (!Strings.isBlank(f)) {
                set.add(f.trim().replace('\\', '/'));
            }
        }
        return new ArrayList<String>(set);
    }

    private static String join(List<String> parts) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(parts.get(i));
        }
        return sb.toString();
    }
}
