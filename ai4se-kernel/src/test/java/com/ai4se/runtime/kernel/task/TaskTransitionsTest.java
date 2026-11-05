package com.ai4se.runtime.kernel.task;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ai4se.runtime.common.id.TaskId;
import com.ai4se.runtime.common.util.Collections2;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

class TaskTransitionsTest {

    @Test
    void allowsCreatedToValidating() {
        assertTrue(TaskTransitions.canTransition(TaskStatus.CREATED, TaskStatus.VALIDATING));
    }

    @Test
    void allowsBlockedPolicyRoundTrip() {
        assertTrue(TaskTransitions.canTransition(TaskStatus.RUNNING, TaskStatus.BLOCKED_POLICY));
        assertTrue(TaskTransitions.canTransition(TaskStatus.BLOCKED_POLICY, TaskStatus.RUNNING));
        assertTrue(TaskTransitions.canTransition(TaskStatus.BLOCKED_POLICY, TaskStatus.FAILED));
    }

    @Test
    void builderCreatesCreatedTask() {
        final Task task = sampleTask();
        assertTrue(task.status() == TaskStatus.CREATED);
        task.transitionTo(TaskStatus.VALIDATING);
        task.transitionTo(TaskStatus.QUEUED);
        assertThrows(IllegalStateException.class, new Executable() {
            @Override
            public void execute() {
                task.transitionTo(TaskStatus.RUNNING);
            }
        });
    }

    private static Task sampleTask() {
        return Task.builder()
                .taskId(new TaskId("task_1"))
                .projectId("demo")
                .profileId("demo.default")
                .profileRevision("1")
                .workflowId("demo.wf")
                .goal(new GoalSpec(
                        "BUGFIX",
                        "acc-1",
                        Collections2.<com.ai4se.runtime.common.id.ArtifactId>emptyList(),
                        Collections2.<String, Object>emptyMap()))
                .workspaceRef(new WorkspaceRef("/tmp/ws", Optional.<String>empty()))
                .budget(new Budget(10, 2, 1000L, Duration.ofMinutes(5), 0L))
                .build();
    }
}
