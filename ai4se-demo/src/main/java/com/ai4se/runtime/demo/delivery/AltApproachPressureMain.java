package com.ai4se.runtime.demo.delivery;

import com.ai4se.runtime.common.context.ExecutionContextView;
import com.ai4se.runtime.common.error.ReasonCode;
import com.ai4se.runtime.common.id.ArtifactId;
import com.ai4se.runtime.common.id.TaskId;
import com.ai4se.runtime.common.id.WorkItemId;
import com.ai4se.runtime.common.id.WorkerId;
import com.ai4se.runtime.common.util.Collections2;
import com.ai4se.runtime.demo.analysis.RequirementAnalysisPipeline;
import com.ai4se.runtime.engine.Runtime;
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
import java.io.File;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Alternate pressure — NOT SerialDelivery six-stage happy path.
 * Approach: blind analysis + Engine gate abuse + Engine DISCOVERY on a new CLI-batch shape.
 * <pre>
 * mvn -pl ai4se-demo exec:java -Ddemo.mainClass=com.ai4se.runtime.demo.delivery.AltApproachPressureMain
 * </pre>
 */
public final class AltApproachPressureMain {

    public static final String CLI_DRY_RUN_REQUIREMENT = ""
            + "Add --dry-run to ImportCli so CSV rows are listed without writing. "
            + "Keep CsvReader; document flag in README.";

    private AltApproachPressureMain() {
    }

    public static void main(String[] args) throws Exception {
        File module = WorkspaceBootstrap.resolveDemoModuleRoot();
        Path evidence = new File(module, "target/alt-approach-pressure/evidence-delta.md").toPath();
        Files.createDirectories(evidence.getParent());

        StringBuilder sb = new StringBuilder();
        sb.append("# Evidence Delta — Alternate-approach pressure (B, not serial DoD)\n\n");
        sb.append("playbook: docs/build-pathway-playbook.md · pressure mode\n");
        sb.append("question: Can S0–S9 hold when we **do not** reuse SerialDelivery happy path?\n");
        sb.append("workspace: `stress-workspaces/04-cli-batch` (CLI/CSV ≠ config/REST/cross-module)\n");
        sb.append("honest: **样例仓 B** — not external production repo\n\n");

        boolean ok = true;
        File ws = new File(module, "stress-workspaces/04-cli-batch");
        if (!ws.isDirectory()) {
            throw new IllegalStateException("missing fixture: " + ws);
        }

        // --- A) Blind analysis: matching shape (no patches / no Runtime delivery) ---
        Path blindOut = new File(module, "target/alt-approach-pressure/blind-cli").toPath();
        RequirementAnalysisPipeline.Result blind = RequirementAnalysisPipeline.run(
                ws.toPath(),
                CLI_DRY_RUN_REQUIREMENT,
                blindOut,
                null,
                false,
                "alt-cli-batch");
        boolean hitsImport = false;
        for (String c : blind.context.getCandidateFiles()) {
            if (c.contains("ImportCli")) {
                hitsImport = true;
                break;
            }
        }
        sb.append("## A. Blind analysis (match shape, no Execution)\n\n");
        sb.append("- gap: ").append(blind.gap.getStatus()).append('\n');
        sb.append("- mayPlan: ").append(blind.gap.mayPlan()).append('\n');
        sb.append("- ImportCli in candidates: ").append(hitsImport).append('\n');
        sb.append("- patches applied: **false** (by design)\n");
        if (!hitsImport) {
            ok = false;
            sb.append("- status: **FAIL** (blind recall)\n\n");
        } else {
            sb.append("- status: **PASS**\n\n");
        }

        // --- B) Cross-domain honesty: promotion demand on CLI repo must not fake-clear ---
        Path promoOut = new File(module, "target/alt-approach-pressure/blind-promo").toPath();
        Map<String, String> unknownAnswers = new LinkedHashMap<String, String>();
        RequirementAnalysisPipeline.Result promo = RequirementAnalysisPipeline.run(
                ws.toPath(),
                RequirementAnalysisPipeline.PROMOTION_REQUIREMENT,
                promoOut,
                unknownAnswers,
                true,
                "alt-cli-vs-promo");
        boolean blockedHonest = !promo.gap.mayPlan();
        sb.append("## B. Cross-domain honesty (促销需求砸到 CLI 仓)\n\n");
        sb.append("- gap: ").append(promo.gap.getStatus()).append('\n');
        sb.append("- mayPlan: ").append(promo.gap.mayPlan()).append('\n');
        sb.append("- clarify questions: ").append(promo.context.getNeedClarification().size()).append('\n');
        if (!blockedHonest) {
            ok = false;
            sb.append("- status: **FAIL** (must not mayPlan on alien domain)\n\n");
        } else {
            sb.append("- status: **PASS** (mayPlan=false)\n\n");
        }

        // --- C) Engine adversarial (no SerialDeliveryRunner) ---
        InMemoryArtifactStore store = new InMemoryArtifactStore();
        ArtifactLifecycleService arts = new ArtifactLifecycleService(store);
        AtomicInteger workerCalls = new AtomicInteger();
        Runtime runtime = new Runtime(
                new TaskLifecycleService(),
                new ContextLifecycleService(),
                arts,
                new TraceLifecycleService(),
                new CiteOkWorker(workerCalls));

        sb.append("## C. Engine adversarial + DISCOVERY (same Runtime, no Demo serial)\n\n");

        RuntimeResult planNoHit = runtime.submit(request(
                "PLAN", ws.getAbsolutePath(), "alt.cli", Collections.<ArtifactId>emptyList(),
                params("requirement", CLI_DRY_RUN_REQUIREMENT)));
        boolean gatePlan = !planNoHit.isSuccess()
                && planNoHit.getMessage().contains("discovery.hit-set")
                && workerCalls.get() == 0;
        sb.append("- PLAN without hit-set rejected, worker=0: ").append(gatePlan).append('\n');
        if (!gatePlan) {
            ok = false;
        }

        int callsBefore = workerCalls.get();
        RuntimeResult discovery = runtime.submit(request(
                "DISCOVERY", ws.getAbsolutePath(), "alt.cli",
                Collections.<ArtifactId>emptyList(),
                params("requirement", CLI_DRY_RUN_REQUIREMENT)));
        boolean discoveryOk = discovery.isSuccess() || discovery.getTaskStatus() == TaskStatus.BLOCKED_POLICY;
        Artifact hit = null;
        for (ArtifactId id : discovery.getCommittedArtifactIds()) {
            Artifact a = store.get(id).get();
            if (StageGate.KIND_HIT_SET.equals(a.kind())) {
                hit = a;
            }
        }
        boolean recall = hit != null && hit.storage().getLocator().contains("ImportCli");
        boolean discoveryNoWorker = workerCalls.get() == callsBefore;
        sb.append("- DISCOVERY committed hit-set: ").append(hit != null).append('\n');
        sb.append("- hit-set recalls ImportCli: ").append(recall).append('\n');
        sb.append("- DISCOVERY skipped Worker: ").append(discoveryNoWorker).append('\n');
        sb.append("- DISCOVERY status: ").append(discovery.getTaskStatus()).append('\n');
        if (!discoveryOk || !recall || !discoveryNoWorker) {
            ok = false;
        }

        ProjectProfile profile = ProjectProfile.parse(""
                + "id: alt.cli.batch\n"
                + "projectId: stress-04-cli-batch\n"
                + "stack: java-cli-batch\n"
                + "verifyCommand: skip\n"
                + "acceptance: C1\n");
        Artifact skipStrategy = arts.proposeWorkerOutput(
                new TaskId("seed-skip"),
                new WorkItemId("wi-skip"),
                new WorkerId("seed"),
                StageGate.KIND_PLAN_TEST,
                "skip-strategy",
                profile.toTestStrategyBody(),
                Collections.<String, String>emptyMap());
        skipStrategy = arts.commit(skipStrategy.artifactId());
        RuntimeResult skipVerify = runtime.submit(request(
                "VERIFY", ws.getAbsolutePath(), profile.id,
                Collections.singletonList(skipStrategy.artifactId()),
                Collections.<String, Object>emptyMap()));
        boolean skipBlocked = !skipVerify.isSuccess()
                && (skipVerify.getMessage().contains("not run") || skipVerify.getMessage().contains("skip"));
        sb.append("- VERIFY command=skip refused: ").append(skipBlocked).append('\n');
        if (!skipBlocked) {
            ok = false;
        }

        ProjectProfile greenProfile = ProjectProfile.parse(""
                + "id: alt.cli.batch.green\n"
                + "projectId: stress-04-cli-batch\n"
                + "stack: java-cli-batch\n"
                + "verifyCommand: pass-cli-batch\n"
                + "acceptance: C1\n");
        Artifact greenStrategy = arts.proposeWorkerOutput(
                new TaskId("seed-green"),
                new WorkItemId("wi-green"),
                new WorkerId("seed"),
                StageGate.KIND_PLAN_TEST,
                "green-strategy",
                greenProfile.toTestStrategyBody(),
                Collections.<String, String>emptyMap());
        greenStrategy = arts.commit(greenStrategy.artifactId());
        RuntimeResult green = runtime.submit(request(
                "VERIFY", ws.getAbsolutePath(), greenProfile.id,
                Collections.singletonList(greenStrategy.artifactId()),
                Collections.<String, Object>emptyMap()));
        boolean greenOk = green.isSuccess()
                && green.getTraceId() != null
                && !green.getTraceId().isEmpty();
        sb.append("- VERIFY green cites C1 + Trace: ").append(greenOk).append('\n');
        if (!greenOk) {
            ok = false;
        }

        sb.append("- status: **").append(ok ? "PASS" : "FAIL").append("**\n\n");

        sb.append("## Evidence Delta (new only)\n\n");
        sb.append("| New evidence | Result |\n|--------------|--------|\n");
        sb.append("| CLI-batch shape (≠ prior three) | ").append(ok ? "used" : "n/a").append(" |\n");
        sb.append("| Blind analysis + cross-domain mayPlan=false | see A/B |\n");
        sb.append("| Engine DISCOVERY/gate/skip — no SerialDelivery | see C |\n\n");
        sb.append("Not claimed: external business repo, S8b/c, S10+, Claude.\n");
        sb.append("Not re-scored: FirstProduction / BoundaryStress serial paths.\n\n");
        sb.append("## Verdict\n\n");
        sb.append(ok ? "**PASS** — alternate approach pressure holds on sample B.\n"
                : "**FAIL** — see sections above.\n");

        Files.write(evidence, sb.toString().getBytes(Charset.forName("UTF-8")));
        Path docsCopy = new File(module, "../docs/alt-approach-pressure-evidence.md").toPath().normalize();
        Files.createDirectories(docsCopy.getParent());
        Files.write(docsCopy, sb.toString().getBytes(Charset.forName("UTF-8")));

        System.out.println(sb);
        System.out.println("report = " + docsCopy.toAbsolutePath());
        if (!ok) {
            throw new IllegalStateException("Alt-approach pressure FAILED");
        }
        System.out.println("Alt-approach pressure OK");
    }

    private static Map<String, Object> params(String k, String v) {
        Map<String, Object> m = new HashMap<String, Object>();
        m.put(k, v);
        return m;
    }

    private static RuntimeRequest request(
            String goal,
            String workspace,
            String profileId,
            java.util.List<ArtifactId> inputs,
            Map<String, Object> params) {
        return RuntimeRequest.builder()
                .projectId("alt-pressure")
                .profileId(profileId)
                .profileRevision("1")
                .workflowId("alt." + goal.toLowerCase())
                .goal(new GoalSpec(goal, "acc-alt", inputs, params))
                .workspaceRef(new WorkspaceRef(workspace, Optional.<String>empty()))
                .budget(new Budget(5, 1, 100L, Duration.ofMinutes(1), 0L))
                .build();
    }

    static final class CiteOkWorker implements Worker {
        private final AtomicInteger calls;

        CiteOkWorker(AtomicInteger calls) {
            this.calls = calls;
        }

        @Override
        public WorkerId workerId() {
            return new WorkerId("worker_alt_cite");
        }

        @Override
        public WorkerDescriptor descriptor() {
            return new WorkerDescriptor(
                    workerId(), WorkerKind.CUSTOM, "CiteOkWorker", "0.1.0",
                    Collections.singletonList("verify"), 1, Collections.singleton("cpu"), true);
        }

        @Override
        public WorkerHealth health() {
            return WorkerHealth.UP;
        }

        @Override
        public WorkResult execute(WorkRequest request, ExecutionContextView context) {
            calls.incrementAndGet();
            String command = String.valueOf(request.getParams().get("command"));
            if (command.contains("skip") || command.startsWith("fail")) {
                return new WorkResult(
                        WorkResultStatus.FATAL_FAIL,
                        Collections2.<ArtifactId>emptyList(),
                        Optional.of(new ReasonCode("FAIL")),
                        Optional.of("should not run"),
                        Collections.<String, Object>emptyMap());
            }
            return new WorkResult(
                    WorkResultStatus.OK,
                    Collections2.<ArtifactId>emptyList(),
                    Optional.<ReasonCode>empty(),
                    Optional.of("C1 PASS"),
                    Collections.<String, Object>singletonMap("stdout", "C1 PASS"));
        }
    }
}
