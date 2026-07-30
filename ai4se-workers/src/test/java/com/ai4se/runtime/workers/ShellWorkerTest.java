package com.ai4se.runtime.workers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
import java.io.File;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;

class ShellWorkerTest {

    @Test
    void toArgv_allowlistsEchoPwdGitStatus() {
        assertEquals(Arrays.asList("/bin/echo", "hello"), ShellWorker.toArgv("echo hello"));
        assertEquals(Arrays.asList("/bin/pwd"), ShellWorker.toArgv("pwd"));
        assertEquals(Arrays.asList("git", "status"), ShellWorker.toArgv("git status"));
    }

    @Test
    void toArgv_allowlistsMvnTestUnderWorkdir(@TempDir File tempDir) throws Exception {
        File pom = new File(tempDir, "pom.xml");
        assertTrue(pom.createNewFile());
        List<String> argv = ShellWorker.toArgv("mvn -f pom.xml -q test", tempDir);
        assertEquals("mvn", argv.get(0));
        assertEquals("-f", argv.get(1));
        assertEquals(pom.getCanonicalFile().getAbsolutePath(), argv.get(2));
        assertEquals("-q", argv.get(3));
        assertEquals("test", argv.get(4));
    }

    @Test
    void toArgv_rejectsDisallowedCommands() {
        assertThrows(IllegalArgumentException.class, new Executable() {
            @Override
            public void execute() {
                ShellWorker.toArgv("rm -rf /");
            }
        });
    }

    @Test
    void execute_echo_capturesRealStdout() {
        ShellWorker worker = new ShellWorker();
        TaskId taskId = new TaskId("task_shell");
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("command", "echo sprint7-real");
        WorkRequest request = new WorkRequest(
                new WorkItemId("wi_shell"),
                taskId,
                "echo sprint7-real",
                Collections2.<ArtifactId>emptyList(),
                params,
                Duration.ofSeconds(10),
                Optional.<String>empty(),
                Optional.<String>empty());

        WorkResult result = worker.execute(request, new FixedView(taskId));

        assertEquals(WorkResultStatus.OK, result.getStatus());
        assertEquals(Integer.valueOf(0), result.getMetrics().get("exitCode"));
        assertTrue(result.getMetrics().get("stdout").toString().contains("sprint7-real"));
        assertEquals(ShellWorker.ARTIFACT_NAME, result.getMetrics().get("artifactName"));
    }

    private static final class FixedView implements ExecutionContextView {
        private final TaskId taskId;

        FixedView(TaskId taskId) {
            this.taskId = taskId;
        }

        @Override
        public ExecutionContextId contextId() {
            return new ExecutionContextId("ctx_shell");
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
