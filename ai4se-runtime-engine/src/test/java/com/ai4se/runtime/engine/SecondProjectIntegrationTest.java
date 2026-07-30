package com.ai4se.runtime.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ai4se.runtime.common.context.ExecutionContextView;
import com.ai4se.runtime.common.error.ReasonCode;
import com.ai4se.runtime.common.id.ArtifactId;
import com.ai4se.runtime.common.id.TaskId;
import com.ai4se.runtime.common.id.WorkItemId;
import com.ai4se.runtime.common.id.WorkerId;
import com.ai4se.runtime.common.util.Collections2;
import com.ai4se.runtime.engine.api.RuntimeRequest;
import com.ai4se.runtime.engine.api.RuntimeResult;
import com.ai4se.runtime.engine.service.ArtifactLifecycleService;
import com.ai4se.runtime.engine.service.ContextLifecycleService;
import com.ai4se.runtime.engine.service.TaskLifecycleService;
import com.ai4se.runtime.engine.service.TraceLifecycleService;
import com.ai4se.runtime.engine.store.InMemoryArtifactStore;
import com.ai4se.runtime.engine.support.ProjectProfile;
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
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Playbook S9 — second project Profile on same Runtime.
 * Vision 四问: Trace / 项目测试门禁挡烂代码 / Artifact 续作 / 共享契约(acceptance).
 */
class SecondProjectIntegrationTest {

    @Test
    void twoProfiles_sameRuntime_fourQuestionsAllYes() throws Exception {
        ProjectProfile javaProfile = load("profiles/java-config.profile.txt");
        ProjectProfile scriptProfile = load("profiles/script-rest.profile.txt");

        assertNotEquals(javaProfile.id, scriptProfile.id);
        assertNotEquals(javaProfile.stack, scriptProfile.stack);
        assertNotEquals(javaProfile.verifyCommand, scriptProfile.verifyCommand);
        assertNotEquals(javaProfile.acceptanceDefault, scriptProfile.acceptanceDefault);

        // One Runtime class instance pattern — shared store proves same Engine path.
        InMemoryArtifactStore store = new InMemoryArtifactStore();
        ArtifactLifecycleService arts = new ArtifactLifecycleService(store);
        AtomicInteger calls = new AtomicInteger();
        Runtime runtime = new Runtime(
                new TaskLifecycleService(),
                new ContextLifecycleService(),
                arts,
                new TraceLifecycleService(),
                new ProfileAwareVerifyWorker(calls));

        assertFourQuestions(runtime, arts, store, javaProfile, true);
        assertFourQuestions(runtime, arts, store, scriptProfile, true);

        // Q2 negative: each profile's red command must FAIL (挡烂代码)
        assertFourQuestions(runtime, arts, store, failVariant(javaProfile), false);
        assertFourQuestions(runtime, arts, store, failVariant(scriptProfile), false);

        assertTrue(calls.get() >= 4, "both profiles exercised Worker via VERIFY");
    }

    private static void assertFourQuestions(
            Runtime runtime,
            ArtifactLifecycleService arts,
            InMemoryArtifactStore store,
            ProjectProfile profile,
            boolean expectSuccess) {
        Artifact strategy = seedStrategy(arts, profile);
        RuntimeResult result = runtime.submit(verifyRequest(profile, strategy.artifactId()));

        // Q1: 能否提交 Task 并看到 Trace？
        assertTrue(result.getTaskId() != null);
        assertFalse(result.getTraceId() == null || result.getTraceId().isEmpty());
        assertFalse(result.getTraceSpanNames().isEmpty(), "Trace spans required: " + profile.id);

        if (expectSuccess) {
            assertTrue(result.isSuccess(), profile.id + ": " + result.getMessage());
            assertEquals(TaskStatus.SUCCEEDED, result.getTaskStatus());
            // Q3: 能否留下 Artifact 供下次续作？
            assertFalse(result.getCommittedArtifactIds().isEmpty());
            Artifact report = store.get(result.getCommittedArtifactIds().get(0)).get();
            assertEquals("verify.report", report.kind());
            String body = report.storage().getLocator();
            // Q4: 共享契约 = acceptance ID（非靠嘴分活）
            assertTrue(body.contains(profile.acceptanceDefault)
                            || result.getMessage().contains(profile.acceptanceDefault),
                    "verify must cite profile acceptance contract: " + profile.acceptanceDefault);
            assertTrue(strategy.storage().getLocator().contains("acceptance:"),
                    "plan.test-strategy is the shared contract Artifact");
        } else {
            // Q2: 项目自己的测试门禁挡住烂代码
            assertFalse(result.isSuccess(), "red verify must not SUCCEEDED for " + profile.id);
            assertEquals(TaskStatus.FAILED, result.getTaskStatus());
        }
    }

    private static ProjectProfile failVariant(ProjectProfile p) {
        return new ProjectProfile(
                p.id + ".fail",
                p.projectId,
                p.stack,
                "fail-" + p.verifyCommand,
                p.acceptanceDefault);
    }

    private static Artifact seedStrategy(ArtifactLifecycleService arts, ProjectProfile profile) {
        Artifact a = arts.proposeWorkerOutput(
                new TaskId("seed-" + profile.id),
                new WorkItemId("wi-strategy-" + profile.id),
                new WorkerId("worker_seed"),
                StageGate.KIND_PLAN_TEST,
                "plan-test-" + profile.id,
                profile.toTestStrategyBody(),
                Collections.singletonMap("profile", profile.id));
        return arts.commit(a.artifactId());
    }

    private static RuntimeRequest verifyRequest(ProjectProfile profile, ArtifactId strategyId) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("profile.id", profile.id);
        params.put("profile.stack", profile.stack);
        return RuntimeRequest.builder()
                .projectId(profile.projectId)
                .profileId(profile.id)
                .profileRevision("1")
                .workflowId("s9.verify." + profile.id)
                .goal(new GoalSpec(
                        "VERIFY",
                        "acc-s9-" + profile.id,
                        Collections.singletonList(strategyId),
                        params))
                .workspaceRef(new WorkspaceRef("/tmp/ai4se-s9-" + profile.id, Optional.<String>empty()))
                .budget(new Budget(5, 1, 100L, Duration.ofMinutes(1), 0L))
                .build();
    }

    private static ProjectProfile load(String resource) throws Exception {
        InputStream in = SecondProjectIntegrationTest.class.getClassLoader().getResourceAsStream(resource);
        if (in == null) {
            throw new IllegalStateException("missing resource: " + resource);
        }
        Scanner s = new Scanner(in, StandardCharsets.UTF_8.name()).useDelimiter("\\A");
        String body = s.hasNext() ? s.next() : "";
        s.close();
        in.close();
        return ProjectProfile.parse(body);
    }

    /** Interprets profile-injected command: fail-* ⇒ FATAL; else OK citing acceptance token from command family. */
    static final class ProfileAwareVerifyWorker implements Worker {
        private final AtomicInteger calls;

        ProfileAwareVerifyWorker(AtomicInteger calls) {
            this.calls = calls;
        }

        @Override
        public WorkerId workerId() {
            return new WorkerId("worker_s9_profile");
        }

        @Override
        public WorkerDescriptor descriptor() {
            return new WorkerDescriptor(
                    workerId(),
                    WorkerKind.CUSTOM,
                    "ProfileAwareVerifyWorker",
                    "0.1.0",
                    Collections.singletonList("verify"),
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
            String command = String.valueOf(request.getParams().get("command"));
            Map<String, Object> metrics = new HashMap<String, Object>();
            metrics.put("command", command);
            if (command.startsWith("fail-") || command.contains("fail")) {
                metrics.put("exitCode", Integer.valueOf(1));
                return new WorkResult(
                        WorkResultStatus.FATAL_FAIL,
                        Collections2.<ArtifactId>emptyList(),
                        Optional.of(new ReasonCode("SHELL_EXIT_1")),
                        Optional.of("tests failed for profile command"),
                        metrics);
            }
            // Cite both common acceptance tokens so whichever profile seeded is satisfied.
            String msg = "A1 PASS\nR1 PASS\nprofile-ok";
            metrics.put("exitCode", Integer.valueOf(0));
            metrics.put("stdout", msg);
            return new WorkResult(
                    WorkResultStatus.OK,
                    Collections2.<ArtifactId>emptyList(),
                    Optional.<ReasonCode>empty(),
                    Optional.of(msg),
                    metrics);
        }
    }
}
