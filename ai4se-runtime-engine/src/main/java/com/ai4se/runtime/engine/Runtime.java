package com.ai4se.runtime.engine;

import com.ai4se.runtime.common.id.ArtifactId;
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
import com.ai4se.runtime.engine.support.Ids;
import com.ai4se.runtime.engine.support.LifecycleJournal;
import com.ai4se.runtime.engine.support.WorkRequestFactory;
import com.ai4se.runtime.kernel.artifact.Artifact;
import com.ai4se.runtime.kernel.checkpoint.Checkpoint;
import com.ai4se.runtime.kernel.context.ExecutionContext;
import com.ai4se.runtime.kernel.task.Task;
import com.ai4se.runtime.kernel.trace.TraceRoot;
import com.ai4se.runtime.worker.api.WorkRequest;
import com.ai4se.runtime.worker.api.WorkResult;
import com.ai4se.runtime.worker.api.WorkResultStatus;
import com.ai4se.runtime.worker.api.Worker;
import java.time.Duration;
import java.util.Collections;
import java.util.Objects;

/**
 * Runtime Engine — Orchestrator only.
 * Checkpoint is optional durable write after Artifact; does not alter Task state machine.
 */
public final class Runtime {

    private final TaskLifecycleService tasks;
    private final ContextLifecycleService contexts;
    private final ArtifactLifecycleService artifacts;
    private final TraceLifecycleService traces;
    private final CheckpointLifecycleService checkpoints;
    private final Worker worker;

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

        Task task = tasks.create(request);
        journal.record("TASK CREATED status=" + task.status());
        tasks.advanceToStarting(task);
        journal.record("TASK → STARTING");

        ExecutionBootstrap.Session session = ExecutionBootstrap.open(task, tasks, contexts, traces, journal);
        ExecutionContext context = session.context();
        TraceRoot trace = session.trace();

        WorkItemId workItemId = new WorkItemId(Ids.next("wi"));
        WorkRequest workRequest = WorkRequestFactory.create(
                task, workItemId, trace.traceId(), Duration.ofMinutes(1));

        WorkResult workResult = worker.execute(workRequest, context.asWorkerView());
        traces.recordNode(trace.traceId(), TraceRoot.SPAN_WORKER);
        journal.record("TRACE span=" + TraceRoot.SPAN_WORKER + " workerStatus=" + workResult.getStatus());

        if (workResult.getStatus() == WorkResultStatus.OK) {
            Artifact committed = artifacts.commitFromWorkResult(
                    task, workItemId, worker, workResult);
            journal.record("ARTIFACT COMMITTED id=" + committed.artifactId()
                    + " lifecycle=" + committed.lifecycle());
            contexts.indexCommitted(context, committed.artifactId(), committed.lifecycle());
            journal.record("CONTEXT indexCommitted artifactId=" + committed.artifactId());
            traces.recordNode(trace.traceId(), TraceRoot.SPAN_ARTIFACT);
            journal.record("TRACE span=" + TraceRoot.SPAN_ARTIFACT);

            // Artifact → Checkpoint → Task End (no CHECKPOINTING status; no Resume).
            Checkpoint checkpoint = checkpoints.createAtBoundary(task, context);
            tasks.markCheckpoint(task, checkpoint.checkpointId());
            journal.record("CHECKPOINT saved id=" + checkpoint.checkpointId()
                    + " sequence=" + checkpoint.sequence());

            tasks.succeed(task);
            journal.record("TASK → SUCCEEDED");
            freezeAndFinish(context, trace.traceId(), journal);

            return toResult(task, context, trace.traceId(), journal, true,
                    Collections.singletonList(committed.artifactId()),
                    workResult.getMessage().orElse("ok"));
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
                workResult.getMessage().orElse("worker failed"));
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
            String message) {
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
                message);
    }
}
