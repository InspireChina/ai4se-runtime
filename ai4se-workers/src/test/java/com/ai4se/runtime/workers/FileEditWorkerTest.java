package com.ai4se.runtime.workers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileEditWorkerTest {

    @TempDir
    File tempDir;

    @Test
    void execute_writesFilesUnderWorkspace() throws Exception {
        FileEditWorker worker = new FileEditWorker();
        TaskId taskId = new TaskId("task_file_edit");
        Map<String, String> files = new HashMap<String, String>();
        files.put("notes/hello.txt", "hello-delivery\n");
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("workdir", tempDir.getAbsolutePath());
        params.put("files", files);
        WorkRequest request = new WorkRequest(
                new WorkItemId("wi_file"),
                taskId,
                "file.edit",
                Collections2.<ArtifactId>emptyList(),
                params,
                Duration.ofSeconds(10),
                Optional.<String>empty(),
                Optional.<String>empty());

        WorkResult result = worker.execute(request, new FixedView(taskId));

        assertEquals(WorkResultStatus.OK, result.getStatus());
        File written = new File(tempDir, "notes/hello.txt");
        assertTrue(written.isFile());
        assertEquals("hello-delivery\n",
                new String(Files.readAllBytes(written.toPath()), Charset.forName("UTF-8")));
        assertEquals(FileEditWorker.ARTIFACT_NAME, result.getMetrics().get("artifactName"));
    }

    @Test
    void execute_rejectsPathEscape() {
        FileEditWorker worker = new FileEditWorker();
        TaskId taskId = new TaskId("task_file_edit_bad");
        Map<String, String> files = new HashMap<String, String>();
        files.put("../outside.txt", "nope");
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("workdir", tempDir.getAbsolutePath());
        params.put("files", files);
        WorkRequest request = new WorkRequest(
                new WorkItemId("wi_file_bad"),
                taskId,
                "file.edit",
                Collections2.<ArtifactId>emptyList(),
                params,
                Duration.ofSeconds(10),
                Optional.<String>empty(),
                Optional.<String>empty());

        WorkResult result = worker.execute(request, new FixedView(taskId));

        assertEquals(WorkResultStatus.FATAL_FAIL, result.getStatus());
        assertFalse(new File(tempDir.getParentFile(), "outside.txt").isFile());
    }

    private static final class FixedView implements ExecutionContextView {
        private final TaskId taskId;

        FixedView(TaskId taskId) {
            this.taskId = taskId;
        }

        @Override
        public ExecutionContextId contextId() {
            return new ExecutionContextId("ctx_file");
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
