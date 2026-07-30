package com.ai4se.runtime.engine;

import com.ai4se.runtime.common.id.ArtifactId;
import com.ai4se.runtime.common.id.CheckpointId;
import com.ai4se.runtime.common.id.TaskId;
import com.ai4se.runtime.common.id.WorkItemId;
import com.ai4se.runtime.engine.api.RuntimeRequest;
import com.ai4se.runtime.engine.api.RuntimeResult;
import com.ai4se.runtime.engine.service.ArtifactLifecycleService;
import com.ai4se.runtime.engine.service.CheckpointLifecycleService;
import com.ai4se.runtime.engine.service.ContextLifecycleService;
import com.ai4se.runtime.engine.service.TaskLifecycleService;
import com.ai4se.runtime.engine.service.TraceLifecycleService;
import com.ai4se.runtime.engine.store.InMemoryArtifactStore;
import com.ai4se.runtime.engine.store.MemoryCheckpointStore;
import com.ai4se.runtime.engine.support.ExecutionBootstrap;
import com.ai4se.runtime.engine.support.HumanWaitRegistry;
import com.ai4se.runtime.engine.support.Ids;
import com.ai4se.runtime.engine.support.LifecycleJournal;
import com.ai4se.runtime.engine.support.MapSearchDiscovery;
import com.ai4se.runtime.engine.support.PlanTestStrategy;
import com.ai4se.runtime.engine.support.StageGate;
import com.ai4se.runtime.engine.support.WorkRequestFactory;
import com.ai4se.runtime.kernel.artifact.Artifact;
import com.ai4se.runtime.kernel.artifact.ArtifactLifecycle;
import com.ai4se.runtime.kernel.checkpoint.Checkpoint;
import com.ai4se.runtime.kernel.context.ExecutionContext;
import com.ai4se.runtime.kernel.task.Task;
import com.ai4se.runtime.kernel.task.TaskStatus;
import com.ai4se.runtime.kernel.trace.TraceRoot;
import com.ai4se.runtime.worker.api.WorkRequest;
import com.ai4se.runtime.worker.api.WorkResult;
import com.ai4se.runtime.worker.api.WorkResultStatus;
import com.ai4se.runtime.worker.api.Worker;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Runtime Engine — Orchestrator only.
 * Checkpoint is optional durable write after Artifact; does not alter Task state machine.
 * S4: POLICY_EXCEPTION → BLOCKED_POLICY + human Artifact APIs (not a Scheduler).
 * S5: StageGate before Worker — missing required Artifact kinds ⇒ FAILED (Worker not called).
 * S7: VERIFY goals load plan.test-strategy; skip/empty/missing acceptance citation ⇒ FAILED.
 * S8a: DISCOVERY runs Map+Search in Engine; commits discovery.hit-set; gaps → BLOCKED_POLICY clarify.
 */
public final class Runtime {

    private final TaskLifecycleService tasks;
    private final ContextLifecycleService contexts;
    private final ArtifactLifecycleService artifacts;
    private final TraceLifecycleService traces;
    private final CheckpointLifecycleService checkpoints;
    private final Worker worker;
    private final HumanWaitRegistry humanWaits = new HumanWaitRegistry();

    public Runtime(Worker worker) {
        this(new TaskLifecycleService(),
                new ContextLifecycleService(),
                new ArtifactLifecycleService(new InMemoryArtifactStore()),
                new TraceLifecycleService(),
                new CheckpointLifecycleService(new MemoryCheckpointStore()),
                worker);
    }

    public Runtime(
            TaskLifecycleService tasks,
            ContextLifecycleService contexts,
            ArtifactLifecycleService artifacts,
            TraceLifecycleService traces,
            Worker worker) {
        this(tasks, contexts, artifacts, traces,
                new CheckpointLifecycleService(new MemoryCheckpointStore()),
                worker);
    }

    public Runtime(
            TaskLifecycleService tasks,
            ContextLifecycleService contexts,
            ArtifactLifecycleService artifacts,
            TraceLifecycleService traces,
            CheckpointLifecycleService checkpoints,
            Worker worker) {
        this.tasks = Objects.requireNonNull(tasks, "tasks");
        this.contexts = Objects.requireNonNull(contexts, "contexts");
        this.artifacts = Objects.requireNonNull(artifacts, "artifacts");
        this.traces = Objects.requireNonNull(traces, "traces");
        this.checkpoints = Objects.requireNonNull(checkpoints, "checkpoints");
        this.worker = Objects.requireNonNull(worker, "worker");
    }

    public RuntimeResult submit(RuntimeRequest request) {
        Objects.requireNonNull(request, "request");
        LifecycleJournal journal = new LifecycleJournal();
        long startedNanos = System.nanoTime();

        Task task = tasks.create(request);
        journal.record("TASK CREATED status=" + task.status());
        tasks.advanceToStarting(task);
        journal.record("TASK → STARTING");

        ExecutionBootstrap.Session session = ExecutionBootstrap.open(task, tasks, contexts, traces, journal);
        ExecutionContext context = session.context();
        TraceRoot trace = session.trace();

        WorkItemId workItemId = new WorkItemId(Ids.next("wi"));
        Duration timeout = request.getBudget().getMaxWallClock();
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            timeout = Duration.ofMinutes(1);
        }

        // S5: seed GoalSpec inputs, then StageGate before Worker (Worker cannot disable).
        try {
            seedInputArtifacts(task, context, journal);
        } catch (RuntimeException seedFail) {
            journal.record("STAGE_GATE seed failed: " + seedFail.getMessage());
            tasks.failGate(task, "STAGE_GATE_INPUT", seedFail.getMessage());
            journal.record("TASK → FAILED (stage gate input)");
            freezeAndFinish(context, trace.traceId(), journal);
            return toResult(task, context, trace.traceId(), journal, false,
                    Collections.<ArtifactId>emptyList(),
                    seedFail.getMessage(),
                    Optional.<CheckpointId>empty(),
                    durationMs(startedNanos));
        }
        Set<String> presentKinds = collectCommittedKinds(context);
        StageGate.Result gate = StageGate.check(task.goal().getType(), presentKinds);
        if (!gate.ok) {
            String msg = "StageGate rejected goalType=" + task.goal().getType()
                    + " missing=" + gate.missing;
            journal.record("STAGE_GATE FAIL " + msg);
            tasks.failGate(task, "STAGE_GATE_MISSING_ARTIFACT", msg);
            journal.record("TASK → FAILED (stage gate; worker not invoked)");
            freezeAndFinish(context, trace.traceId(), journal);
            return toResult(task, context, trace.traceId(), journal, false,
                    Collections.<ArtifactId>emptyList(),
                    msg,
                    Optional.<CheckpointId>empty(),
                    durationMs(startedNanos));
        }
        journal.record("STAGE_GATE PASS goalType=" + task.goal().getType());

        if (StageGate.isDiscoveryGoal(task.goal().getType())) {
            return runDiscovery(task, context, trace, journal, workItemId, startedNanos);
        }

        PlanTestStrategy verifyStrategy = null;
        String commandOverride = null;
        if (StageGate.isVerifyGoal(task.goal().getType())) {
            Artifact strategyArt = findCommittedByKind(context, StageGate.KIND_PLAN_TEST);
            if (strategyArt == null) {
                String msg = "VERIFY missing plan.test-strategy after StageGate";
                tasks.failGate(task, "VERIFY_NO_STRATEGY", msg);
                freezeAndFinish(context, trace.traceId(), journal);
                return toResult(task, context, trace.traceId(), journal, false,
                        Collections.<ArtifactId>emptyList(), msg,
                        Optional.<CheckpointId>empty(), durationMs(startedNanos));
            }
            verifyStrategy = PlanTestStrategy.parse(strategyArt.storage().getLocator());
            if (verifyStrategy.isSkipOrEmpty()) {
                String msg = "VERIFY refused: plan.test-strategy command is skip/empty (tests not run)";
                journal.record("VERIFY FAIL " + msg);
                tasks.failGate(task, "VERIFY_TESTS_NOT_RUN", msg);
                freezeAndFinish(context, trace.traceId(), journal);
                return toResult(task, context, trace.traceId(), journal, false,
                        Collections.<ArtifactId>emptyList(), msg,
                        Optional.<CheckpointId>empty(), durationMs(startedNanos));
            }
            if (verifyStrategy.acceptanceIds.isEmpty()) {
                String msg = "VERIFY refused: plan.test-strategy has no acceptance IDs";
                tasks.failGate(task, "VERIFY_NO_ACCEPTANCE", msg);
                freezeAndFinish(context, trace.traceId(), journal);
                return toResult(task, context, trace.traceId(), journal, false,
                        Collections.<ArtifactId>emptyList(), msg,
                        Optional.<CheckpointId>empty(), durationMs(startedNanos));
            }
            commandOverride = verifyStrategy.command;
            journal.record("VERIFY using plan.test-strategy command=" + commandOverride
                    + " acceptance=" + verifyStrategy.acceptanceIds);
        }

        WorkRequest workRequest = WorkRequestFactory.create(
                task, workItemId, trace.traceId(), timeout, commandOverride);

        WorkResult workResult = worker.execute(workRequest, context.asWorkerView());
        traces.recordNode(trace.traceId(), TraceRoot.SPAN_WORKER);
        journal.record("TRACE span=" + TraceRoot.SPAN_WORKER + " workerStatus=" + workResult.getStatus());

        if (workResult.getStatus() == WorkResultStatus.OK) {
            if (verifyStrategy != null) {
                String report = workResult.getMessage().orElse("");
                Object stdout = workResult.getMetrics().get("stdout");
                if (stdout != null) {
                    report = report + "\n" + stdout.toString();
                }
                if (!verifyStrategy.citesAllAcceptances(report)) {
                    String msg = "VERIFY refused: report missing acceptance IDs "
                            + verifyStrategy.acceptanceIds;
                    journal.record("VERIFY FAIL " + msg);
                    tasks.failGate(task, "VERIFY_ACCEPTANCE_NOT_CITED", msg);
                    freezeAndFinish(context, trace.traceId(), journal);
                    return toResult(task, context, trace.traceId(), journal, false,
                            Collections.<ArtifactId>emptyList(), msg,
                            Optional.<CheckpointId>empty(), durationMs(startedNanos));
                }
            }

            Artifact committed = artifacts.commitFromWorkResult(
                    task, workItemId, worker, workResult);
            journal.record("ARTIFACT COMMITTED id=" + committed.artifactId()
                    + " lifecycle=" + committed.lifecycle());
            contexts.indexCommitted(context, committed.artifactId(), committed.lifecycle());
            journal.record("CONTEXT indexCommitted artifactId=" + committed.artifactId());
            traces.recordNode(trace.traceId(), TraceRoot.SPAN_ARTIFACT);
            journal.record("TRACE span=" + TraceRoot.SPAN_ARTIFACT);

            Checkpoint checkpoint = checkpoints.createAtBoundary(task, context);
            tasks.markCheckpoint(task, checkpoint.checkpointId());
            journal.record("CHECKPOINT saved id=" + checkpoint.checkpointId()
                    + " sequence=" + checkpoint.sequence());

            tasks.succeed(task);
            journal.record("TASK → SUCCEEDED");
            freezeAndFinish(context, trace.traceId(), journal);

            return toResult(task, context, trace.traceId(), journal, true,
                    Collections.singletonList(committed.artifactId()),
                    workResult.getMessage().orElse("ok"),
                    Optional.of(checkpoint.checkpointId()),
                    durationMs(startedNanos));
        }

        if (workResult.getStatus() == WorkResultStatus.POLICY_EXCEPTION) {
            // S4: questionnaire / plan-for-approval as COMMITTED Artifact; Task waits human.
            Artifact questionnaire = artifacts.commitFromWorkResult(
                    task, workItemId, worker, workResult);
            journal.record("ARTIFACT COMMITTED (human-wait) id=" + questionnaire.artifactId());
            contexts.indexCommitted(context, questionnaire.artifactId(), questionnaire.lifecycle());
            traces.recordNode(trace.traceId(), TraceRoot.SPAN_ARTIFACT);
            journal.record("TRACE span=" + TraceRoot.SPAN_ARTIFACT);

            tasks.blockPolicy(task);
            journal.record("TASK → BLOCKED_POLICY (await human Artifact)");
            humanWaits.put(new HumanWaitRegistry.Session(
                    task, context, trace, journal, startedNanos, workItemId));

            return toResult(task, context, trace.traceId(), journal, false,
                    Collections.singletonList(questionnaire.artifactId()),
                    workResult.getMessage().orElse("waiting for human"),
                    Optional.<CheckpointId>empty(),
                    durationMs(startedNanos));
        }

        Artifact abandoned = artifacts.abandonFromWorkResult(task, workItemId, worker, workResult);
        journal.record("ARTIFACT ABANDONED id=" + abandoned.artifactId()
                + " lifecycle=" + abandoned.lifecycle());
        traces.recordNode(trace.traceId(), TraceRoot.SPAN_ARTIFACT);
        journal.record("TRACE span=" + TraceRoot.SPAN_ARTIFACT);

        tasks.fail(task, workResult);
        journal.record("TASK → FAILED");
        freezeAndFinish(context, trace.traceId(), journal);

        return toResult(task, context, trace.traceId(), journal, false,
                Collections.<ArtifactId>emptyList(),
                workResult.getMessage().orElse("worker failed"),
                Optional.<CheckpointId>empty(),
                durationMs(startedNanos));
    }

    /**
     * S4: commit clarification answers Artifact, leave BLOCKED_POLICY, stub-enter next stage (SUCCEEDED).
     */
    public RuntimeResult submitClarifyAnswers(TaskId taskId, String answersPayload) {
        return resumeWithHumanArtifact(
                taskId, "clarification.answers", "clarify-answers", answersPayload);
    }

    /** S4: commit plan.approved Artifact, leave BLOCKED_POLICY, stub-enter next stage (SUCCEEDED). */
    public RuntimeResult approvePlan(TaskId taskId, String approvalNote) {
        return resumeWithHumanArtifact(
                taskId, "plan.approved", "plan-approved", approvalNote);
    }

    private RuntimeResult resumeWithHumanArtifact(
            TaskId taskId,
            String artifactKind,
            String artifactName,
            String payload) {
        Objects.requireNonNull(taskId, "taskId");
        HumanWaitRegistry.Session session = humanWaits.get(taskId);
        if (session == null) {
            throw new IllegalStateException("No human-wait session for task: " + taskId);
        }
        Task task = session.task;
        if (task.status() != TaskStatus.BLOCKED_POLICY) {
            throw new IllegalStateException(
                    "Task not BLOCKED_POLICY: " + taskId + " status=" + task.status());
        }
        if (payload == null || payload.trim().isEmpty()) {
            throw new IllegalArgumentException("Human answer/approval payload must be non-empty Artifact body");
        }

        LifecycleJournal journal = session.journal;
        WorkItemId humanWi = new WorkItemId(Ids.next("wi-human"));
        Artifact answer = artifacts.proposeWorkerOutput(
                task.taskId(),
                humanWi,
                worker.workerId(),
                artifactKind,
                artifactName,
                payload.trim(),
                Collections.singletonMap("source", "human"));
        answer = artifacts.commit(answer.artifactId());
        journal.record("ARTIFACT COMMITTED (human) kind=" + artifactKind + " id=" + answer.artifactId());
        contexts.indexCommitted(session.context, answer.artifactId(), answer.lifecycle());

        tasks.resumeFromPolicy(task);
        journal.record("TASK → RUNNING (human Artifact accepted)");

        // Minimal S4 proof: next-stage stub = succeed after human gate (no Scheduler / StageRunner).
        Checkpoint checkpoint = checkpoints.createAtBoundary(task, session.context);
        tasks.markCheckpoint(task, checkpoint.checkpointId());
        tasks.succeed(task);
        journal.record("TASK → SUCCEEDED (post-human stub stage)");
        freezeAndFinish(session.context, session.trace.traceId(), journal);
        humanWaits.remove(taskId);

        List<ArtifactId> committed = new ArrayList<ArtifactId>();
        committed.add(answer.artifactId());
        return toResult(task, session.context, session.trace.traceId(), journal, true,
                committed,
                "human gate cleared",
                Optional.of(checkpoint.checkpointId()),
                durationMs(session.startedNanos));
    }

    private Artifact findCommittedByKind(ExecutionContext context, String kind) {
        for (ArtifactId id : context.listArtifactIds()) {
            Optional<Artifact> found = artifacts.get(id);
            if (found.isPresent()
                    && found.get().lifecycle() == ArtifactLifecycle.COMMITTED
                    && kind.equals(found.get().kind())) {
                return found.get();
            }
        }
        return null;
    }

    /**
     * S8a: Engine Map+Search (Worker not invoked). Hit-set always committed;
     * non-empty gaps ⇒ questionnaire + BLOCKED_POLICY (摸底≠懂业务).
     */
    private RuntimeResult runDiscovery(
            Task task,
            ExecutionContext context,
            TraceRoot trace,
            LifecycleJournal journal,
            WorkItemId workItemId,
            long startedNanos) {
        journal.record("DISCOVERY Map+Search (Engine; worker skipped)");
        String requirement = discoveryRequirement(task);
        MapSearchDiscovery.Budget budget = discoveryBudget(task);
        Path root = Paths.get(task.workspaceRef().getRootPath());
        MapSearchDiscovery.Result discovery;
        try {
            discovery = MapSearchDiscovery.run(root, requirement, budget);
        } catch (Exception e) {
            String msg = "DISCOVERY failed: " + e.getMessage();
            journal.record(msg);
            tasks.failGate(task, "DISCOVERY_IO", msg);
            freezeAndFinish(context, trace.traceId(), journal);
            return toResult(task, context, trace.traceId(), journal, false,
                    Collections.<ArtifactId>emptyList(), msg,
                    Optional.<CheckpointId>empty(), durationMs(startedNanos));
        }

        Map<String, String> meta = new HashMap<String, String>();
        meta.put("strategy", "map+search");
        meta.put("truncated", discovery.truncated ? "true" : "false");
        meta.put("files-scanned", String.valueOf(discovery.filesScanned));
        meta.put("source", "engine-s8a");

        Artifact hitSet = artifacts.proposeWorkerOutput(
                task.taskId(),
                workItemId,
                worker.workerId(),
                StageGate.KIND_HIT_SET,
                "discovery-hit-set",
                discovery.payload,
                meta);
        hitSet = artifacts.commit(hitSet.artifactId());
        contexts.indexCommitted(context, hitSet.artifactId(), hitSet.lifecycle());
        journal.record("ARTIFACT COMMITTED kind=" + StageGate.KIND_HIT_SET
                + " id=" + hitSet.artifactId()
                + " hits=" + discovery.hits.size()
                + " truncated=" + discovery.truncated);
        traces.recordNode(trace.traceId(), TraceRoot.SPAN_ARTIFACT);

        List<ArtifactId> committed = new ArrayList<ArtifactId>();
        committed.add(hitSet.artifactId());

        if (discovery.needsClarification()) {
            StringBuilder qBody = new StringBuilder();
            qBody.append("# clarification.questionnaire (from discovery gaps)\n");
            for (String q : discovery.clarifyQuestions) {
                qBody.append("- ").append(q).append('\n');
            }
            qBody.append("\n# gaps\n");
            for (String g : discovery.gaps) {
                qBody.append("- ").append(g).append('\n');
            }
            WorkItemId qWi = new WorkItemId(Ids.next("wi-clarify"));
            Artifact questionnaire = artifacts.proposeWorkerOutput(
                    task.taskId(),
                    qWi,
                    worker.workerId(),
                    StageGate.KIND_CLARIFY_Q,
                    "discovery-gaps-clarify",
                    qBody.toString(),
                    Collections.singletonMap("source", "discovery-gaps"));
            questionnaire = artifacts.commit(questionnaire.artifactId());
            contexts.indexCommitted(context, questionnaire.artifactId(), questionnaire.lifecycle());
            committed.add(questionnaire.artifactId());
            journal.record("ARTIFACT COMMITTED kind=" + StageGate.KIND_CLARIFY_Q
                    + " questions=" + discovery.clarifyQuestions.size());

            tasks.blockPolicy(task);
            journal.record("TASK → BLOCKED_POLICY (discovery gaps need clarify)");
            humanWaits.put(new HumanWaitRegistry.Session(
                    task, context, trace, journal, startedNanos, workItemId));

            return toResult(task, context, trace.traceId(), journal, false,
                    committed,
                    "discovery hit-set committed; awaiting clarify for gaps",
                    Optional.<CheckpointId>empty(),
                    durationMs(startedNanos));
        }

        Checkpoint checkpoint = checkpoints.createAtBoundary(task, context);
        tasks.markCheckpoint(task, checkpoint.checkpointId());
        tasks.succeed(task);
        journal.record("TASK → SUCCEEDED (discovery)");
        freezeAndFinish(context, trace.traceId(), journal);

        return toResult(task, context, trace.traceId(), journal, true,
                committed,
                "discovery hit-set ok hits=" + discovery.hits.size(),
                Optional.of(checkpoint.checkpointId()),
                durationMs(startedNanos));
    }

    private static String discoveryRequirement(Task task) {
        Map<String, Object> params = task.goal().getParams();
        if (params != null) {
            Object req = params.get("requirement");
            if (req != null && !req.toString().trim().isEmpty()) {
                return req.toString().trim();
            }
        }
        String acc = task.goal().getAcceptanceRef();
        return acc == null ? "" : acc;
    }

    private static MapSearchDiscovery.Budget discoveryBudget(Task task) {
        Map<String, Object> params = task.goal().getParams();
        int maxFiles = MapSearchDiscovery.DEFAULT_MAX_FILES;
        int topK = MapSearchDiscovery.DEFAULT_TOP_K;
        int maxPayload = MapSearchDiscovery.DEFAULT_MAX_PAYLOAD_CHARS;
        if (params != null) {
            maxFiles = intParam(params, "discovery.maxFiles", maxFiles);
            topK = intParam(params, "discovery.topK", topK);
            maxPayload = intParam(params, "discovery.maxPayloadChars", maxPayload);
        }
        return new MapSearchDiscovery.Budget(maxFiles, topK, maxPayload);
    }

    private static int intParam(Map<String, Object> params, String key, int fallback) {
        Object v = params.get(key);
        if (v instanceof Number) {
            return ((Number) v).intValue();
        }
        if (v != null) {
            try {
                return Integer.parseInt(v.toString().trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private void seedInputArtifacts(Task task, ExecutionContext context, LifecycleJournal journal) {
        List<ArtifactId> inputs = task.goal().getInputArtifactIds();
        if (inputs == null || inputs.isEmpty()) {
            return;
        }
        for (ArtifactId id : inputs) {
            Artifact art = artifacts.requireCommitted(id);
            contexts.indexCommitted(context, art.artifactId(), art.lifecycle());
            journal.record("CONTEXT seeded input kind=" + art.kind() + " id=" + art.artifactId());
        }
    }

    private Set<String> collectCommittedKinds(ExecutionContext context) {
        Set<String> kinds = StageGate.newKindSet();
        for (ArtifactId id : context.listArtifactIds()) {
            Optional<Artifact> found = artifacts.get(id);
            if (found.isPresent() && found.get().lifecycle() == ArtifactLifecycle.COMMITTED) {
                kinds.add(found.get().kind());
            }
        }
        return kinds;
    }

    private static long durationMs(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1000000L;
    }

    private void freezeAndFinish(ExecutionContext context, String traceId, LifecycleJournal journal) {
        contexts.freeze(context);
        journal.record("CONTEXT FROZEN phase=" + context.phase());
        traces.finishAndClose(traceId);
        journal.record("TRACE span=" + TraceRoot.SPAN_FINISH);
        journal.record("TRACE closed");
    }

    private RuntimeResult toResult(
            Task task,
            ExecutionContext context,
            String traceId,
            LifecycleJournal journal,
            boolean success,
            java.util.List<ArtifactId> committedIds,
            String message,
            Optional<CheckpointId> checkpointId,
            long durationMs) {
        return new RuntimeResult(
                task.taskId(),
                context.contextId(),
                traceId,
                task.status(),
                context.phase(),
                committedIds,
                traces.get(traceId).get().spanNames(),
                journal.events(),
                success,
                message,
                checkpointId,
                durationMs,
                worker.workerId().value(),
                task.goal().getType());
    }
}
