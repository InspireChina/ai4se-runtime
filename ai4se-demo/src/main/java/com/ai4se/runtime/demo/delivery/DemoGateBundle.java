package com.ai4se.runtime.demo.delivery;

import com.ai4se.runtime.common.context.ExecutionContextView;
import com.ai4se.runtime.common.id.ArtifactId;
import com.ai4se.runtime.common.id.TaskId;
import com.ai4se.runtime.common.id.WorkItemId;
import com.ai4se.runtime.common.id.WorkerId;
import com.ai4se.runtime.engine.Runtime;
import com.ai4se.runtime.engine.service.ArtifactLifecycleService;
import com.ai4se.runtime.engine.service.ContextLifecycleService;
import com.ai4se.runtime.engine.service.TaskLifecycleService;
import com.ai4se.runtime.engine.service.TraceLifecycleService;
import com.ai4se.runtime.engine.store.InMemoryArtifactStore;
import com.ai4se.runtime.engine.support.StageGate;
import com.ai4se.runtime.kernel.artifact.Artifact;
import com.ai4se.runtime.worker.api.WorkRequest;
import com.ai4se.runtime.worker.api.WorkResult;
import com.ai4se.runtime.worker.api.WorkResultStatus;
import com.ai4se.runtime.worker.api.Worker;
import com.ai4se.runtime.worker.api.WorkerDescriptor;
import com.ai4se.runtime.worker.api.WorkerHealth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Demo serial delivery shares one Artifact store across submits so StageGate
 * required kinds can be seeded (S5/S6/S7). Not a Scheduler / StageRunner.
 *
 * <p>DISCOVERY in Engine is Map+Search ({@code StageGate.isDiscoveryGoal}); Demo's
 * thin git/shell probe must use a non-discovery goalType (e.g. {@code GIT_STATUS})
 * or it would skip ShellWorker and possibly BLOCKED_POLICY.
 */
public final class DemoGateBundle {

    public static final String DEMO_ACCEPTANCE_TOKEN = "DEMO-VERIFY";
    /** Goal type for Demo shell probe — must not match {@link StageGate#isDiscoveryGoal}. */
    public static final String PROBE_GOAL_TYPE = "GIT_STATUS";

    private final ArtifactLifecycleService arts;
    private Artifact hitSet;
    private Artifact planApproved;
    private Artifact planTest;

    public DemoGateBundle() {
        this.arts = new ArtifactLifecycleService(new InMemoryArtifactStore());
    }

    public ArtifactLifecycleService artifacts() {
        return arts;
    }

    public Runtime runtime(Worker worker) {
        return new Runtime(
                new TaskLifecycleService(),
                new ContextLifecycleService(),
                arts,
                new TraceLifecycleService(),
                worker);
    }

    /**
     * Ensure COMMITTED kinds required by StageGate for {@code goalType}; return their ids.
     *
     * @param verifyCommand used when seeding/refreshing {@code plan.test-strategy}
     * @param acceptanceIds cited into strategy body; VERIFY Worker wrapper appends them on OK
     */
    public List<ArtifactId> inputsFor(
            String goalType, String verifyCommand, List<String> acceptanceIds) {
        List<String> required = StageGate.requiredKinds(goalType);
        if (required.isEmpty()) {
            return Collections.emptyList();
        }
        ensureHitSet();
        ensurePlanApproved();
        ensurePlanTest(verifyCommand, acceptanceIds);
        List<ArtifactId> ids = new ArrayList<ArtifactId>();
        for (String kind : required) {
            if (StageGate.KIND_HIT_SET.equals(kind)) {
                ids.add(hitSet.artifactId());
            } else if (StageGate.KIND_PLAN_APPROVED.equals(kind)) {
                ids.add(planApproved.artifactId());
            } else if (StageGate.KIND_PLAN_TEST.equals(kind)) {
                ids.add(planTest.artifactId());
            } else if (StageGate.KIND_PLAN_DESIGN.equals(kind)) {
                // Demo PLAN path does not run PLAN_TEST stage; design not required for stress chain.
                throw new IllegalStateException("DemoGateBundle does not seed " + kind);
            } else {
                throw new IllegalStateException("Unhandled StageGate kind: " + kind);
            }
        }
        return ids;
    }

    public static List<String> defaultAcceptanceIds() {
        return Collections.singletonList(DEMO_ACCEPTANCE_TOKEN);
    }

    /** Wraps Worker so OK results cite plan.test-strategy acceptance ids (S7 report check). */
    public static Worker citing(Worker inner, List<String> acceptanceIds) {
        return new CitingWorker(inner, acceptanceIds);
    }

    private void ensureHitSet() {
        if (hitSet != null) {
            return;
        }
        hitSet = commit(
                StageGate.KIND_HIT_SET,
                "hit-set",
                "# discovery.hit-set\n"
                        + "# DemoGateBundle seed for SerialDelivery StageGate (not Engine MapSearch).\n"
                        + "- path: (demo-seed)\n"
                        + "- reason: prerequisite for PLAN\n");
    }

    private void ensurePlanApproved() {
        if (planApproved != null) {
            return;
        }
        planApproved = commit(
                StageGate.KIND_PLAN_APPROVED,
                "plan-approved",
                "demo-preapproved: SerialDeliveryRunner fixture path\n");
    }

    private void ensurePlanTest(String verifyCommand, List<String> acceptanceIds) {
        List<String> ids = acceptanceIds == null || acceptanceIds.isEmpty()
                ? defaultAcceptanceIds()
                : acceptanceIds;
        String cmd = verifyCommand == null ? "" : verifyCommand.trim();
        StringBuilder body = new StringBuilder();
        body.append("# plan.test-strategy (DemoGateBundle seed)\n");
        body.append("command: ").append(cmd).append('\n');
        body.append("acceptance: ");
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) {
                body.append(", ");
            }
            body.append(ids.get(i));
        }
        body.append('\n');
        // Always re-commit fresh strategy so verify command matches this run.
        planTest = commit(StageGate.KIND_PLAN_TEST, "plan-test-strategy", body.toString());
    }

    private Artifact commit(String kind, String name, String body) {
        Artifact proposed = arts.proposeWorkerOutput(
                new TaskId("demo-gate-seed"),
                new WorkItemId("wi-demo-gate"),
                new WorkerId("worker_demo_gate"),
                kind,
                name,
                body,
                Collections.<String, String>emptyMap());
        return arts.commit(proposed.artifactId());
    }

    private static final class CitingWorker implements Worker {
        private final Worker inner;
        private final List<String> acceptanceIds;

        CitingWorker(Worker inner, List<String> acceptanceIds) {
            this.inner = inner;
            this.acceptanceIds = acceptanceIds == null
                    ? DemoGateBundle.defaultAcceptanceIds()
                    : new ArrayList<String>(acceptanceIds);
        }

        @Override
        public WorkerId workerId() {
            return inner.workerId();
        }

        @Override
        public WorkerDescriptor descriptor() {
            return inner.descriptor();
        }

        @Override
        public WorkerHealth health() {
            return inner.health();
        }

        @Override
        public WorkResult execute(WorkRequest request, ExecutionContextView context) {
            WorkResult result = inner.execute(request, context);
            if (result.getStatus() != WorkResultStatus.OK || acceptanceIds.isEmpty()) {
                return result;
            }
            StringBuilder msg = new StringBuilder(result.getMessage().orElse(""));
            msg.append('\n');
            for (String id : acceptanceIds) {
                msg.append(id).append('\n');
            }
            return new WorkResult(
                    result.getStatus(),
                    result.getOutputArtifactIds(),
                    result.getReasonCode(),
                    java.util.Optional.of(msg.toString()),
                    result.getMetrics());
        }
    }
}
