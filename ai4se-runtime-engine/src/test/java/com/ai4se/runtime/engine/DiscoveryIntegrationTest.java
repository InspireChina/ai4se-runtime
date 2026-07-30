package com.ai4se.runtime.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ai4se.runtime.common.context.ExecutionContextView;
import com.ai4se.runtime.common.id.ArtifactId;
import com.ai4se.runtime.common.id.WorkerId;
import com.ai4se.runtime.common.util.Collections2;
import com.ai4se.runtime.engine.api.RuntimeRequest;
import com.ai4se.runtime.engine.api.RuntimeResult;
import com.ai4se.runtime.engine.service.ArtifactLifecycleService;
import com.ai4se.runtime.engine.service.ContextLifecycleService;
import com.ai4se.runtime.engine.service.TaskLifecycleService;
import com.ai4se.runtime.engine.service.TraceLifecycleService;
import com.ai4se.runtime.engine.store.InMemoryArtifactStore;
import com.ai4se.runtime.engine.support.StageGate;
import com.ai4se.runtime.kernel.artifact.Artifact;
import com.ai4se.runtime.kernel.task.Budget;
import com.ai4se.runtime.kernel.task.GoalSpec;
import com.ai4se.runtime.kernel.task.TaskStatus;
import com.ai4se.runtime.kernel.task.WorkspaceRef;
import com.ai4se.runtime.worker.api.WorkRequest;
import com.ai4se.runtime.worker.api.WorkResult;
import com.ai4se.runtime.worker.api.WorkResultStatus;
import com.ai4se.runtime.worker.api.Worker;
import com.ai4se.runtime.worker.api.WorkerDescriptor;
import com.ai4se.runtime.worker.api.WorkerHealth;
import com.ai4se.runtime.worker.api.WorkerKind;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Playbook S8a — Engine Map+Search → discovery.hit-set; gaps→clarify; volume cap. */
class DiscoveryIntegrationTest {

    @TempDir
    Path workspace;

    @Test
    void hitSetRecallsDiffPaths_andFeedsPlanGate() throws Exception {
        Path orderApi = workspace.resolve("src/main/java/com/example/order/OrderApi.java");
        Files.createDirectories(orderApi.getParent());
        Files.write(orderApi, "public class OrderApi { int timeout; }\n".getBytes(StandardCharsets.UTF_8));
        Path other = workspace.resolve("src/main/java/com/example/util/Helpers.java");
        Files.createDirectories(other.getParent());
        Files.write(other, "public class Helpers {}\n".getBytes(StandardCharsets.UTF_8));

        AtomicInteger workerCalls = new AtomicInteger();
        InMemoryArtifactStore store = new InMemoryArtifactStore();
        ArtifactLifecycleService arts = new ArtifactLifecycleService(store);
        Runtime runtime = runtime(arts, new CountingWorker(workerCalls));

        RuntimeResult discovery = runtime.submit(discoveryRequest(
                "Change OrderApi timeout config",
                Collections.<String, Object>emptyMap()));
        assertTrue(discovery.isSuccess(), discovery.getMessage());
        assertEquals(0, workerCalls.get(), "DISCOVERY must not call Worker");
        Artifact hit = store.get(discovery.getCommittedArtifactIds().get(0)).get();
        assertEquals(StageGate.KIND_HIT_SET, hit.kind());
        String body = hit.storage().getLocator();
        assertTrue(body.contains("OrderApi.java"), body);

        // Fake final diff paths — recall check
        List<String> fakeDiff = Collections.singletonList(
                "src/main/java/com/example/order/OrderApi.java");
        for (String diffPath : fakeDiff) {
            assertTrue(body.contains(diffPath), "hit-set must recall diff path: " + diffPath);
        }

        RuntimeResult plan = runtime.submit(planRequest(hit.artifactId()));
        assertTrue(plan.isSuccess());
        assertEquals(1, workerCalls.get());
    }

    @Test
    void gapsFlowIntoClarifyQuestions_blockUntilAnswers() throws Exception {
        // Empty-ish workspace: promotion requirement → gaps
        Path readme = workspace.resolve("README.md");
        Files.write(readme, "hello\n".getBytes(StandardCharsets.UTF_8));

        AtomicInteger workerCalls = new AtomicInteger();
        InMemoryArtifactStore store = new InMemoryArtifactStore();
        ArtifactLifecycleService arts = new ArtifactLifecycleService(store);
        Runtime runtime = runtime(arts, new CountingWorker(workerCalls));

        RuntimeResult waiting = runtime.submit(discoveryRequest(
                "实现促销满减优惠 Promotion",
                Collections.<String, Object>emptyMap()));
        assertFalse(waiting.isSuccess());
        assertEquals(TaskStatus.BLOCKED_POLICY, waiting.getTaskStatus());
        assertEquals(0, workerCalls.get());

        boolean foundQ = false;
        String qBody = "";
        for (ArtifactId id : waiting.getCommittedArtifactIds()) {
            Artifact a = store.get(id).get();
            if (StageGate.KIND_CLARIFY_Q.equals(a.kind())) {
                foundQ = true;
                qBody = a.storage().getLocator();
            }
            if (StageGate.KIND_HIT_SET.equals(a.kind())) {
                String hitBody = a.storage().getLocator();
                assertTrue(hitBody.contains("## clarify-questions"), hitBody);
                assertTrue(hitBody.contains("## gaps"), hitBody);
            }
        }
        assertTrue(foundQ, "gaps must produce clarification.questionnaire");
        assertTrue(qBody.contains("Promotion") || qBody.contains("优惠") || qBody.contains("Order"), qBody);
        assertTrue(qBody.contains("# gaps"), "questionnaire must cite same gaps list");

        RuntimeResult cleared = runtime.submitClarifyAnswers(
                waiting.getTaskId(), "Promotion表已有; OrderService.calc 入口已知");
        assertTrue(cleared.isSuccess());
    }

    @Test
    void oversizedScan_truncatedAndPayloadCapped() throws Exception {
        for (int i = 0; i < 12; i++) {
            Path f = workspace.resolve("src/main/java/com/example/F" + i + ".java");
            Files.createDirectories(f.getParent());
            Files.write(f, ("class F" + i + " { String order; }\n").getBytes(StandardCharsets.UTF_8));
        }

        Map<String, Object> params = new HashMap<String, Object>();
        params.put("discovery.maxFiles", Integer.valueOf(5));
        params.put("discovery.maxPayloadChars", Integer.valueOf(600));
        params.put("discovery.topK", Integer.valueOf(3));

        InMemoryArtifactStore store = new InMemoryArtifactStore();
        ArtifactLifecycleService arts = new ArtifactLifecycleService(store);
        Runtime runtime = runtime(arts, new CountingWorker(new AtomicInteger()));

        RuntimeResult result = runtime.submit(discoveryRequest("order api timeout", params));
        assertTrue(result.isSuccess() || result.getTaskStatus() == TaskStatus.BLOCKED_POLICY);
        Artifact hit = null;
        for (ArtifactId id : result.getCommittedArtifactIds()) {
            Artifact a = store.get(id).get();
            if (StageGate.KIND_HIT_SET.equals(a.kind())) {
                hit = a;
            }
        }
        assertTrue(hit != null);
        String body = hit.storage().getLocator();
        assertTrue(body.contains("truncated: true") || body.contains("truncated-payload"), body);
        assertTrue(body.length() <= 700, "payload must respect cap, was " + body.length());
    }

    private static Runtime runtime(ArtifactLifecycleService arts, Worker worker) {
        return new Runtime(
                new TaskLifecycleService(),
                new ContextLifecycleService(),
                arts,
                new TraceLifecycleService(),
                worker);
    }

    private RuntimeRequest discoveryRequest(String requirement, Map<String, Object> extra) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("requirement", requirement);
        params.putAll(extra);
        return RuntimeRequest.builder()
                .projectId("demo")
                .profileId("demo.default")
                .profileRevision("1")
                .workflowId("s8a.discovery")
                .goal(new GoalSpec(
                        "DISCOVERY",
                        "acc-s8a",
                        Collections.<ArtifactId>emptyList(),
                        params))
                .workspaceRef(new WorkspaceRef(workspace.toString(), Optional.<String>empty()))
                .budget(new Budget(5, 1, 100L, Duration.ofMinutes(1), 0L))
                .build();
    }

    private static RuntimeRequest planRequest(ArtifactId hitSet) {
        return RuntimeRequest.builder()
                .projectId("demo")
                .profileId("demo.default")
                .profileRevision("1")
                .workflowId("s8a.plan")
                .goal(new GoalSpec(
                        "PLAN",
                        "acc-s8a-plan",
                        Collections.singletonList(hitSet),
                        Collections2.<String, Object>emptyMap()))
                .workspaceRef(new WorkspaceRef("/tmp/ai4se-ws", Optional.<String>empty()))
                .budget(new Budget(5, 1, 100L, Duration.ofMinutes(1), 0L))
                .build();
    }

    static final class CountingWorker implements Worker {
        private final AtomicInteger calls;

        CountingWorker(AtomicInteger calls) {
            this.calls = calls;
        }

        @Override
        public WorkerId workerId() {
            return new WorkerId("worker_count");
        }

        @Override
        public WorkerDescriptor descriptor() {
            return new WorkerDescriptor(
                    workerId(),
                    WorkerKind.CUSTOM,
                    "CountingWorker",
                    "0.1.0",
                    Collections.singletonList("any"),
                    1,
                    Collections.singleton("cpu"),
                    true);
        }

        @Override
        public WorkerHealth health() {
            return WorkerHealth.UP;
        }

        @Override
        public WorkResult execute(WorkRequest request, ExecutionContextView context) {
            calls.incrementAndGet();
            return new WorkResult(
                    WorkResultStatus.OK,
                    Collections2.<ArtifactId>emptyList(),
                    Optional.empty(),
                    Optional.of("ok"),
                    Collections.<String, Object>emptyMap());
        }
    }
}
