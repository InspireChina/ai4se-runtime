package com.ai4se.runtime.workers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ai4se.runtime.common.context.ExecutionContextView;
import com.ai4se.runtime.common.id.ArtifactId;
import com.ai4se.runtime.common.id.ExecutionContextId;
import com.ai4se.runtime.common.id.TaskId;
import com.ai4se.runtime.common.id.WorkItemId;
import com.ai4se.runtime.common.util.Collections2;
import com.ai4se.runtime.worker.api.WorkRequest;
import com.ai4se.runtime.worker.api.WorkResult;
import com.ai4se.runtime.worker.api.WorkResultStatus;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class NoopWorkerTest {

    @Test
    void execute_alwaysOk_withHelloArtifactHint() {
        NoopWorker worker = new NoopWorker();
        TaskId taskId = new TaskId("task_noop");
        WorkRequest request = new WorkRequest(
                new WorkItemId("wi_1"),
                taskId,
                "noop",
                Collections2.<ArtifactId>emptyList(),
                Collections.<String, Object>emptyMap(),
                Duration.ofSeconds(1),
                Optional.<String>empty(),
                Optional.<String>empty());
        WorkResult result = worker.execute(request, new FixedView(taskId));

        assertEquals(WorkResultStatus.OK, result.getStatus());
        assertEquals(NoopWorker.FIXED_MESSAGE, result.getMessage().get());
        assertEquals(NoopWorker.ARTIFACT_NAME, result.getMetrics().get("artifactName"));
        assertTrue(result.getMetrics().get("stdout").toString().contains("hello"));
    }

    private static final class FixedView implements ExecutionContextView {
        private final TaskId taskId;

        FixedView(TaskId taskId) {
            this.taskId = taskId;
        }

        @Override
        public ExecutionContextId contextId() {
            return new ExecutionContextId("ctx_noop");
        }

        @Override
        public TaskId taskId() {
            return taskId;
        }

        @Override
        public boolean frozen() {
            return false;
        }

        @Override
        public List<ArtifactId> listArtifactIds() {
            return Collections.emptyList();
        }

        @Override
        public Optional<String> profileId() {
            return Optional.of("demo.default");
        }
    }
}
