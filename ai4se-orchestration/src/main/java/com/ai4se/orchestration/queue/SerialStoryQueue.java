package com.ai4se.orchestration.queue;

import com.ai4se.orchestration.acceptance.HumanAcceptanceRecords;
import com.ai4se.orchestration.analysis.ClarificationRecords;
import com.ai4se.orchestration.run.RunLedger;
import com.ai4se.orchestration.workflow.StoryWorkflowMachine;
import com.ai4se.orchestration.workflow.StoryWorkflowState;
import com.ai4se.orchestration.workflow.WorkflowStage;
import com.ai4se.orchestration.workflow.WorkflowStatus;
import com.ai4se.runtime.common.util.Strings;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A deliberately small serial Story queue. It schedules; it does not approve, answer, or start
 * a model by itself. That boundary lets a UI/OMP call the existing explicit commands and prevents
 * a blocked business question from becoming a hidden retry loop.
 */
public final class SerialStoryQueue {

    public static final String DIR = "queue";
    public static final String FILE = "serial-queue.properties";

    private SerialStoryQueue() {
    }

    public static void add(Path workspace, String storyId) throws IOException {
        requireStoryId(storyId);
        Path story = workspace.resolve(".story").resolve(storyId);
        if (!Files.isDirectory(story)) {
            throw new IllegalArgumentException("Story is not opened: " + story);
        }
        List<String> ids = read(workspace);
        if (ids.contains(storyId)) {
            throw new IllegalArgumentException("Story already queued: " + storyId);
        }
        ids.add(storyId);
        write(workspace, ids);
    }

    public static List<Entry> entries(Path workspace) throws IOException {
        List<Entry> out = new ArrayList<Entry>();
        for (String id : read(workspace)) {
            out.add(new Entry(id, classify(workspace, id)));
        }
        return Collections.unmodifiableList(out);
    }

    /** First work item that can advance without being blocked by another card's decision. */
    public static Entry next(Path workspace) throws IOException {
        List<Entry> entries = entries(workspace);
        for (Entry entry : entries) {
            if (entry.status == Status.READY_FOR_RUN) {
                return entry;
            }
        }
        for (Entry entry : entries) {
            if (entry.status == Status.READY_FOR_SPECIFICATION) {
                return entry;
            }
        }
        return null;
    }

    public static String format(Path workspace) throws IOException {
        StringBuilder out = new StringBuilder("queue=SERIAL\n");
        List<Entry> entries = entries(workspace);
        out.append("count=").append(entries.size()).append('\n');
        for (int i = 0; i < entries.size(); i++) {
            Entry e = entries.get(i);
            out.append("story.").append(i + 1).append('=').append(e.storyId)
                    .append(" status=").append(e.status.name()).append('\n');
        }
        Entry next = next(workspace);
        if (next == null) {
            out.append("next=NONE\n");
        } else {
            out.append("next=").append(next.storyId).append(" action=")
                    .append(nextAction(next.status)).append('\n');
        }
        return out.toString();
    }

    private static Status classify(Path workspace, String storyId) throws IOException {
        if (HumanAcceptanceRecords.hasRecord(workspace, storyId)) {
            return HumanAcceptanceRecords.isAccepted(workspace, storyId)
                    ? Status.ACCEPTED : Status.REJECTED;
        }
        Path story = workspace.resolve(".story").resolve(storyId);
        if (Files.isDirectory(RunLedger.runDir(workspace, storyId))) {
            try {
                StoryWorkflowState workflow = StoryWorkflowMachine.load(workspace, storyId);
                if (workflow.status() == WorkflowStatus.COMPLETED) {
                    return Status.AWAITING_HUMAN_ACCEPTANCE;
                }
                if (ClarificationRecords.hasPending(workspace, storyId)) {
                    return Status.WAITING_ANALYSIS_ANSWER;
                }
                if (workflow.stage() == WorkflowStage.PLANNING
                        && workflow.status() == WorkflowStatus.STOPPED) {
                    return Status.WAITING_PLAN_APPROVAL;
                }
                if (workflow.status() == WorkflowStatus.STOPPED) {
                    return Status.STOPPED_POLICY;
                }
                return Status.RUNNING;
            } catch (Exception ignored) {
                return Status.STOPPED_POLICY;
            }
        }
        if (Files.isRegularFile(story.resolve("specification/frozen-inputs.properties"))) {
            return Status.READY_FOR_RUN;
        }
        Path specResult = story.resolve("specification/specification.result.properties");
        if (Files.isRegularFile(specResult)) {
            String text = new String(Files.readAllBytes(specResult), StandardCharsets.UTF_8);
            return text.contains("decision=CLARIFICATION_REQUIRED")
                    ? Status.WAITING_SPECIFICATION_ANSWER : Status.WAITING_SPECIFICATION_FREEZE;
        }
        if (Files.isRegularFile(story.resolve("input/intake.properties"))) {
            return Status.READY_FOR_SPECIFICATION;
        }
        return Status.INVALID;
    }

    private static String nextAction(Status status) {
        if (status == Status.READY_FOR_RUN) {
            return "run";
        }
        if (status == Status.READY_FOR_SPECIFICATION) {
            return "specify";
        }
        return "NONE";
    }

    private static List<String> read(Path workspace) throws IOException {
        Path file = file(workspace);
        if (!Files.isRegularFile(file)) {
            return new ArrayList<String>();
        }
        List<String> ids = new ArrayList<String>();
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            String t = line.trim();
            if (!t.startsWith("story.")) {
                continue;
            }
            int eq = t.indexOf('=');
            if (eq < 0) {
                continue;
            }
            String id = t.substring(eq + 1).trim();
            if (!Strings.isBlank(id)) {
                ids.add(id);
            }
        }
        return ids;
    }

    private static void write(Path workspace, List<String> ids) throws IOException {
        Path file = file(workspace);
        Files.createDirectories(file.getParent());
        StringBuilder body = new StringBuilder("version=1\nmode=SERIAL\n");
        for (int i = 0; i < ids.size(); i++) {
            body.append("story.").append(i + 1).append('=').append(ids.get(i)).append('\n');
        }
        Files.write(file, body.toString().getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    private static Path file(Path workspace) {
        return workspace.resolve(".ai4se").resolve(DIR).resolve(FILE);
    }

    private static void requireStoryId(String storyId) {
        if (Strings.isBlank(storyId) || !storyId.matches("^[A-Za-z0-9][A-Za-z0-9._-]*$")) {
            throw new IllegalArgumentException("Invalid story id: " + storyId);
        }
    }

    public enum Status {
        READY_FOR_SPECIFICATION,
        WAITING_SPECIFICATION_ANSWER,
        WAITING_SPECIFICATION_FREEZE,
        READY_FOR_RUN,
        WAITING_ANALYSIS_ANSWER,
        WAITING_PLAN_APPROVAL,
        RUNNING,
        STOPPED_POLICY,
        AWAITING_HUMAN_ACCEPTANCE,
        ACCEPTED,
        REJECTED,
        INVALID
    }

    public static final class Entry {
        public final String storyId;
        public final Status status;

        Entry(String storyId, Status status) {
            this.storyId = storyId;
            this.status = status;
        }
    }
}
