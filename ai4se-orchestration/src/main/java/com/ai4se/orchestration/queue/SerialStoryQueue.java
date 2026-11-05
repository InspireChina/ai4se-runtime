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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
        add(workspace, storyId, Dependency.NONE, null);
    }

    /**
     * Adds an explicitly declared predecessor relationship.  The queue is still serial and does
     * not start a model itself; this declaration only determines whether a later Story may be
     * offered to an operator/batch runner after its predecessor settles.
     */
    public static void add(Path workspace, String storyId, Dependency dependency, String parentStoryId)
            throws IOException {
        requireStoryId(storyId);
        Path story = workspace.resolve(".story").resolve(storyId);
        if (!Files.isDirectory(story)) {
            throw new IllegalArgumentException("Story is not opened: " + story);
        }
        Dependency safeDependency = dependency == null ? Dependency.NONE : dependency;
        List<QueuedStory> queued = read(workspace);
        if (find(queued, storyId) != null) {
            throw new IllegalArgumentException("Story already queued: " + storyId);
        }
        String parent = Strings.isBlank(parentStoryId) ? null : parentStoryId.trim();
        if (safeDependency == Dependency.NONE && parent != null) {
            throw new IllegalArgumentException("parentStoryId requires a non-NONE dependency");
        }
        if (safeDependency != Dependency.NONE) {
            requireStoryId(parent);
            if (storyId.equals(parent) || find(queued, parent) == null) {
                throw new IllegalArgumentException(
                        "dependency parent must already be queued and cannot be the Story itself: " + parent);
            }
        }
        queued.add(new QueuedStory(storyId, safeDependency, parent));
        write(workspace, queued);
    }

    public static List<Entry> entries(Path workspace) throws IOException {
        List<Entry> out = new ArrayList<Entry>();
        for (QueuedStory queued : read(workspace)) {
            out.add(new Entry(queued.storyId, classify(workspace, queued), queued.dependency, queued.parentStoryId));
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
                    .append(" status=").append(e.status.name())
                    .append(" dependency=").append(e.dependency.name());
            if (e.parentStoryId != null) {
                out.append(" parent=").append(e.parentStoryId);
            }
            out.append('\n');
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

    private static Status classify(Path workspace, QueuedStory queued) throws IOException {
        Status dependencyBlock = dependencyBlock(workspace, queued);
        if (dependencyBlock != null) {
            return dependencyBlock;
        }
        return classifyOwnState(workspace, queued.storyId);
    }

    private static Status dependencyBlock(Path workspace, QueuedStory queued) throws IOException {
        if (queued.dependency == Dependency.NONE) {
            return null;
        }
        QueuedStory parent = find(read(workspace), queued.parentStoryId);
        if (parent == null) {
            return Status.BLOCKED_BY_INVALID_PARENT;
        }
        Status parentStatus = classify(workspace, parent);
        if (parentStatus == Status.REJECTED || parentStatus == Status.BLOCKED_BY_REJECTED_ANCESTOR) {
            return Status.BLOCKED_BY_REJECTED_ANCESTOR;
        }
        if (queued.dependency == Dependency.REQUIRES_ACCEPTED_PARENT) {
            return parentStatus == Status.ACCEPTED ? null : Status.WAITING_PARENT_ACCEPTANCE;
        }
        // A batch-approved successor may begin after its predecessor delivered locally, but is
        // still blocked when that predecessor is later rejected.  The declaration is evidence of
        // an operator-approved shared business premise, not an automatic acceptance substitute.
        if (parentStatus == Status.AWAITING_HUMAN_ACCEPTANCE || parentStatus == Status.ACCEPTED) {
            return null;
        }
        return Status.WAITING_PARENT_DELIVERY;
    }

    private static Status classifyOwnState(Path workspace, String storyId) throws IOException {
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

    private static List<QueuedStory> read(Path workspace) throws IOException {
        Path file = file(workspace);
        if (!Files.isRegularFile(file)) {
            return new ArrayList<QueuedStory>();
        }
        Map<Integer, QueuedStory> numbered = new LinkedHashMap<Integer, QueuedStory>();
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            String t = line.trim();
            if (!t.startsWith("story.")) {
                continue;
            }
            int eq = t.indexOf('=');
            if (eq < 0) {
                continue;
            }
            String key = t.substring(0, eq);
            String id = t.substring(eq + 1).trim();
            int separator = id.indexOf(' ');
            String attributes = separator < 0 ? "" : id.substring(separator + 1).trim();
            id = separator < 0 ? id : id.substring(0, separator).trim();
            if (!Strings.isBlank(id)) {
                int order;
                try {
                    order = Integer.parseInt(key.substring("story.".length()));
                } catch (NumberFormatException ignored) {
                    continue;
                }
                Dependency dependency = Dependency.NONE;
                String parent = null;
                for (String attribute : attributes.split("\\s+")) {
                    if (attribute.startsWith("dependency=")) {
                        dependency = Dependency.parse(attribute.substring("dependency=".length()));
                    } else if (attribute.startsWith("parent=")) {
                        parent = attribute.substring("parent=".length()).trim();
                    }
                }
                numbered.put(Integer.valueOf(order), new QueuedStory(id, dependency, parent));
            }
        }
        return new ArrayList<QueuedStory>(numbered.values());
    }

    private static void write(Path workspace, List<QueuedStory> stories) throws IOException {
        Path file = file(workspace);
        Files.createDirectories(file.getParent());
        StringBuilder body = new StringBuilder("version=1\nmode=SERIAL\n");
        for (int i = 0; i < stories.size(); i++) {
            QueuedStory story = stories.get(i);
            body.append("story.").append(i + 1).append('=').append(story.storyId)
                    .append(" dependency=").append(story.dependency.name());
            if (story.parentStoryId != null) {
                body.append(" parent=").append(story.parentStoryId);
            }
            body.append('\n');
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

    private static QueuedStory find(List<QueuedStory> stories, String storyId) {
        if (Strings.isBlank(storyId)) {
            return null;
        }
        for (QueuedStory story : stories) {
            if (storyId.equals(story.storyId)) {
                return story;
            }
        }
        return null;
    }

    public enum Dependency {
        NONE,
        BATCH_APPROVED_PARENT,
        REQUIRES_ACCEPTED_PARENT;

        public static Dependency parse(String value) {
            if (Strings.isBlank(value)) {
                return NONE;
            }
            try {
                return Dependency.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException bad) {
                throw new IllegalArgumentException("Unknown queue dependency: " + value);
            }
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
        WAITING_PARENT_DELIVERY,
        WAITING_PARENT_ACCEPTANCE,
        BLOCKED_BY_REJECTED_ANCESTOR,
        BLOCKED_BY_INVALID_PARENT,
        INVALID
    }

    public static final class Entry {
        public final String storyId;
        public final Status status;
        public final Dependency dependency;
        public final String parentStoryId;

        Entry(String storyId, Status status, Dependency dependency, String parentStoryId) {
            this.storyId = storyId;
            this.status = status;
            this.dependency = dependency;
            this.parentStoryId = parentStoryId;
        }
    }

    private static final class QueuedStory {
        private final String storyId;
        private final Dependency dependency;
        private final String parentStoryId;

        private QueuedStory(String storyId, Dependency dependency, String parentStoryId) {
            this.storyId = storyId;
            this.dependency = dependency == null ? Dependency.NONE : dependency;
            this.parentStoryId = Strings.isBlank(parentStoryId) ? null : parentStoryId;
        }
    }
}
