package com.ai4se.orchestration.pathway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ai4se.context.onboard.OnboardRepoScript;
import com.ai4se.execution.api.AdapterResult;
import com.ai4se.execution.support.FunctionalModelCliAdapter;
import com.ai4se.execution.support.ProcessInvoker;
import com.ai4se.orchestration.pathway.PathwayRunner.Script;
import com.ai4se.orchestration.support.CommandArgv;
import com.ai4se.orchestration.verification.VerificationControl;
import com.ai4se.orchestration.workflow.WorkflowStatus;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * B-suite (脱敏仓) V3/V4 sign-off: real {@code mvn test}, real git commit, Dev via Adapter-on-spine.
 * Not a live customer repo — handbook allows 脱敏仓 for B until a field repo is available.
 */
final class PathwayBSuiteSignOffIntegrationTest {

    private static final String ALLOWED =
            "src/main/java/com/example/delivery/ConfigService.java";

    @TempDir
    Path temp;

    @Test
    void bSuiteV3RealMavenTestAndEvidence() throws Exception {
        Path ws = prepareWorkspace("v3");
        ProcessInvoker real = new ProcessInvoker.RealProcessInvoker();

        // baseline: customer tests are red
        assertFalse(mvnTestPasses(ws), "fixture must start red");

        AtomicInteger adapterCalls = new AtomicInteger();
        FunctionalModelCliAdapter devAdapter = new FunctionalModelCliAdapter("b-suite-dev", request -> {
            adapterCalls.incrementAndGet();
            assertTrue(Files.isRegularFile(request.packageDir().resolve("manifest.md")));
            assertEquals("Development", request.role());
            writeFixed(request.workspace());
            return AdapterResult.ok(0, "dev-ok", "", Collections.singletonMap("adapter", "b-suite-dev"));
        });

        PathwayRunner.PathwayResult result = PathwayRunner.run(
                PathwayRunner.Config.builder(ws, "story-b-v3")
                        .script(Script.V3)
                        .suite("B")
                        .seedPath(seed(temp, "story-b-v3"))
                        .allowedFile(ALLOWED)
                        .verifyCommand("mvn -q test")
                        .devAdapter(devAdapter)
                        .humanAccepter("b-suite-reviewer")
                        .allowReviewFixture(true)
                        .build(),
                real);

        assertEquals(1, adapterCalls.get(), "Adapter submitted once — no retry");
        assertEquals(WorkflowStatus.COMPLETED, result.finalState.status());
        assertTrue(result.commitShaOrNull != null && result.commitShaOrNull.matches("^[0-9a-f]{7,40}$"));
        assertTrue(Files.isRegularFile(result.evidenceRoot.resolve("meta.yaml")));
        String meta = new String(Files.readAllBytes(result.evidenceRoot.resolve("meta.yaml")),
                StandardCharsets.UTF_8);
        assertTrue(meta.contains("suite: B"));
        assertTrue(meta.contains("script: V3"));
        assertTrue(meta.contains("spine_mode: hybrid_adapter_dev"));
        assertTrue(meta.contains("adapter_invoked: true"));
        assertTrue(meta.contains("adapter_roles: Development"));
        assertTrue(meta.contains("signoff_claim: adapter_spine_wiring_functional"));
        assertTrue(meta.contains("adapter_kind: functional_hook"));
        assertTrue(meta.contains("discovery_prepared_by_runner: true"));
        assertTrue(Files.isRegularFile(
                result.evidenceRoot.resolve("execution/adapter-dev-round-1.md")));
        assertTrue(Files.isRegularFile(result.evidenceRoot.resolve("verification/report-round-1.md")));
        String report = new String(Files.readAllBytes(
                ws.resolve(".story/story-b-v3/verification/report-round-1.md")), StandardCharsets.UTF_8);
        assertTrue(report.contains("outcome: PASS"));
        assertTrue(report.contains("observed: true"));
        assertTrue(report.contains("verdict_basis: " + VerificationControl.VERDICT_BASIS));
        assertTrue(report.contains("package_built_before_run: true"));
        String verifyPkg = new String(Files.readAllBytes(
                ws.resolve(".story/story-b-v3/packages/verification/round-1/slices/acceptance.md")),
                StandardCharsets.UTF_8);
        assertTrue(verifyPkg.contains("timeoutMs returns 5000"));
        assertFalse(verifyPkg.contains("See .story/"));
        assertTrue(Files.isRegularFile(result.evidenceRoot.resolve("sessions/decisions.md")));
        assertTrue(Files.isRegularFile(result.evidenceRoot.resolve("lifecycle/noop.md")));
        assertSignoffHybridSpineDisclosures(ws, "story-b-v3", result.evidenceRoot);
        assertTrue(mvnTestPasses(ws), "customer tests must stay green after Delivery");
    }

    @Test
    void bSuiteV4DefectLoopWithRealMaven() throws Exception {
        Path ws = prepareWorkspace("v4");
        ProcessInvoker real = new ProcessInvoker.RealProcessInvoker();

        AtomicInteger adapterCalls = new AtomicInteger();
        FunctionalModelCliAdapter devAdapter = new FunctionalModelCliAdapter("b-suite-dev", request -> {
            adapterCalls.incrementAndGet();
            String round = request.env().get("AI4SE_DEV_ROUND");
            if ("1".equals(round)) {
                writeStillBroken(request.workspace());
            } else {
                writeFixed(request.workspace());
            }
            return AdapterResult.ok(0, "dev-round-" + round, "", Collections.<String, String>emptyMap());
        });

        PathwayRunner.PathwayResult result = PathwayRunner.run(
                PathwayRunner.Config.builder(ws, "story-b-v4")
                        .script(Script.V4)
                        .suite("B")
                        .seedPath(seed(temp, "story-b-v4"))
                        .allowedFile(ALLOWED)
                        .verifyCommand("mvn -q test")
                        .devAdapter(devAdapter)
                        .allowReviewFixture(true)
                        .build(),
                real);

        assertEquals(2, adapterCalls.get(), "one Adapter submit per Dev round — no retry");
        assertEquals(Script.V4, result.script);
        assertEquals(WorkflowStatus.COMPLETED, result.finalState.status());
        assertTrue(Files.isRegularFile(
                ws.resolve(".story/story-b-v4/packages/verification/round-1/manifest.md")));
        assertTrue(Files.isRegularFile(
                ws.resolve(".story/story-b-v4/packages/verification/round-2/manifest.md")));
        assertTrue(Files.isDirectory(ws.resolve(".story/story-b-v4/defects")));
        String fail = new String(Files.readAllBytes(
                ws.resolve(".story/story-b-v4/verification/report-round-1.md")), StandardCharsets.UTF_8);
        String pass = new String(Files.readAllBytes(
                ws.resolve(".story/story-b-v4/verification/report-round-2.md")), StandardCharsets.UTF_8);
        assertTrue(fail.contains("outcome: FAIL"));
        assertTrue(pass.contains("outcome: PASS"));
        boolean defectInDevPackage = false;
        Path devPkgs = ws.resolve(".story/story-b-v4/packages/development");
        for (Path p : Files.newDirectoryStream(devPkgs)) {
            Path m = p.resolve("manifest.md");
            if (Files.isRegularFile(m)) {
                String text = new String(Files.readAllBytes(m), StandardCharsets.UTF_8);
                if (text.toLowerCase().contains("defect")) {
                    defectInDevPackage = true;
                    break;
                }
            }
        }
        assertTrue(defectInDevPackage, "re-Dev package after FAIL must include Defect P1");
        String meta = new String(Files.readAllBytes(result.evidenceRoot.resolve("meta.yaml")),
                StandardCharsets.UTF_8);
        assertTrue(meta.contains("suite: B"));
        assertTrue(meta.contains("script: V4"));
        assertTrue(meta.contains("spine_mode: hybrid_adapter_dev"));
        assertTrue(meta.contains("adapter_invoked: true"));
        assertTrue(meta.contains("adapter_kind: functional_hook"));
        assertTrue(Files.isRegularFile(
                ws.resolve(".story/story-b-v4/execution/adapter-dev-round-1.md")));
        assertTrue(Files.isRegularFile(
                ws.resolve(".story/story-b-v4/execution/adapter-dev-round-2.md")));
        assertSignoffHybridSpineDisclosures(ws, "story-b-v4", result.evidenceRoot);
    }

    @Test
    void bSuiteWithoutDevMutationOrAdapterRefused() throws Exception {
        Path ws = prepareWorkspace("no-mut");
        ProcessInvoker real = new ProcessInvoker.RealProcessInvoker();
        try {
            PathwayRunner.run(
                    PathwayRunner.Config.builder(ws, "story-b-refuse")
                            .script(Script.V3)
                            .suite("B")
                            .seedPath(seed(temp, "story-b-refuse"))
                            .allowedFile(ALLOWED)
                            .verifyCommand("mvn -q test")
                            .allowReviewFixture(true)
                            .build(),
                    real);
            throw new AssertionError("expected StageGateException");
        } catch (com.ai4se.orchestration.analysis.StageGateException expected) {
            assertTrue(expected.getMessage().contains("Dev Adapter")
                    || expected.getMessage().contains("DevMutation"));
        }
    }

    private Path prepareWorkspace(String tag) throws Exception {
        Path ws = temp.resolve("b-cust-" + tag);
        copyFixture(ws);
        gitInitWithCommit(ws);
        OnboardRepoScript.run(ws);
        Files.write(
                ws.resolve(".ai4se/repository/entries.yaml"),
                ("build:\n  - mvn -q -DskipTests package\ntest:\n  - mvn -q test\n")
                        .getBytes(StandardCharsets.UTF_8));
        return ws;
    }

    private static Path seed(Path temp, String storyId) throws IOException {
        Path seed = temp.resolve(storyId + "-seed.md");
        Files.write(seed, (""
                + "## raw\nConfig timeout should come from properties\n\n"
                + "## goal\nRead app.timeout.ms in ConfigService.timeoutMs()\n\n"
                + "## in_scope\n- ConfigService.timeoutMs\n\n"
                + "## out_of_scope\n- HTTP layer\n\n"
                + "## acceptance\n- timeoutMs returns 5000 from app.timeout.ms\n")
                .getBytes(StandardCharsets.UTF_8));
        return seed;
    }

    private static void writeFixed(Path workspace) throws IOException {
        Path file = workspace.resolve(ALLOWED);
        Files.write(file, (""
                + "package com.example.delivery;\n\n"
                + "import java.io.IOException;\n"
                + "import java.io.InputStream;\n"
                + "import java.util.Properties;\n\n"
                + "public final class ConfigService {\n"
                + "    private final Properties properties;\n"
                + "    public ConfigService(Properties properties) { this.properties = properties; }\n"
                + "    public static ConfigService loadFromClasspath() throws IOException {\n"
                + "        Properties properties = new Properties();\n"
                + "        InputStream in = ConfigService.class.getResourceAsStream(\"/application.properties\");\n"
                + "        if (in != null) { try { properties.load(in); } finally { in.close(); } }\n"
                + "        return new ConfigService(properties);\n"
                + "    }\n"
                + "    public int timeoutMs() {\n"
                + "        return Integer.parseInt(properties.getProperty(\"app.timeout.ms\", \"0\"));\n"
                + "    }\n"
                + "}\n").getBytes(StandardCharsets.UTF_8));
    }

    /** Still wrong — Verify must FAIL (V4 first round). */
    private static void writeStillBroken(Path workspace) throws IOException {
        Path file = workspace.resolve(ALLOWED);
        Files.write(file, (""
                + "package com.example.delivery;\n\n"
                + "import java.io.IOException;\n"
                + "import java.io.InputStream;\n"
                + "import java.util.Properties;\n\n"
                + "public final class ConfigService {\n"
                + "    private final Properties properties;\n"
                + "    public ConfigService(Properties properties) { this.properties = properties; }\n"
                + "    public static ConfigService loadFromClasspath() throws IOException {\n"
                + "        Properties properties = new Properties();\n"
                + "        InputStream in = ConfigService.class.getResourceAsStream(\"/application.properties\");\n"
                + "        if (in != null) { try { properties.load(in); } finally { in.close(); } }\n"
                + "        return new ConfigService(properties);\n"
                + "    }\n"
                + "    public int timeoutMs() {\n"
                + "        // intentional wrong fix — still ignores properties\n"
                + "        return 30;\n"
                + "    }\n"
                + "}\n").getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Hybrid spine disclosures only — not handbook §6 八问 full signoff.
     * Q3 preset Dev: B Functional writeFixed is intentional spine proof; do not claim non-preset Dev.
     */
    private static void assertSignoffHybridSpineDisclosures(Path ws, String storyId, Path evidence)
            throws IOException {
        String audit = new String(Files.readAllBytes(evidence.resolve("audit-host.md")),
                StandardCharsets.UTF_8);
        assertTrue(audit.contains("push: false"));
        assertTrue(audit.contains("suite: B"));
        assertTrue(audit.contains("spine_mode: hybrid_adapter_dev"));
        assertTrue(audit.contains("adapter_invoked: true"));
        String meta = new String(Files.readAllBytes(evidence.resolve("meta.yaml")),
                StandardCharsets.UTF_8);
        assertTrue(meta.contains("adapter_kind: functional_hook"));
        assertTrue(meta.contains("discovery_prepared_by_runner: true"));
        assertTrue(meta.contains("approval_prepared_by_runner: true"));
        assertTrue(meta.contains("signoff_claim: adapter_spine_wiring_functional"));
        assertTrue(meta.contains("human_acceptance_kind: fixture"));
        String acceptance = new String(Files.readAllBytes(
                evidence.resolve("acceptance/human-acceptance.md")), StandardCharsets.UTF_8);
        assertTrue(acceptance.contains("kind: FIXTURE"));
        // Discovery|skip present — no empty skip into Plan
        assertTrue(
                Files.isRegularFile(ws.resolve(".story/" + storyId + "/analysis/discovery.skip.md"))
                        || Files.isRegularFile(
                        ws.resolve(".story/" + storyId + "/analysis/discovery.report.md")));
        assertTrue(Files.isRegularFile(ws.resolve(".story/" + storyId + "/planning/plan.md")));
        String plan = new String(Files.readAllBytes(
                ws.resolve(".story/" + storyId + "/planning/plan.md")), StandardCharsets.UTF_8);
        assertTrue(plan.toLowerCase().contains("allowed"));
        String delivery = new String(Files.readAllBytes(
                ws.resolve(".story/" + storyId + "/delivery/delivery.md")), StandardCharsets.UTF_8);
        assertTrue(delivery.contains("pushed: false"));
        assertTrue(Files.isRegularFile(ws.resolve(".story/" + storyId + "/review/review-result.md")));
        assertTrue(Files.isRegularFile(evidence.resolve("acceptance/human-acceptance.md")));
        assertTrue(Files.isRegularFile(
                ws.resolve(".story/" + storyId + "/execution/adapter-dev-round-1.md")));
        // Verify used observed reports (not compile-only claim)
        assertTrue(Files.isDirectory(ws.resolve(".story/" + storyId + "/verification")));
    }

    private static boolean mvnTestPasses(Path ws) throws Exception {
        ProcessInvoker.ProcessOutcome outcome = new ProcessInvoker.RealProcessInvoker().run(
                CommandArgv.shellCommand("mvn -q test"), ws, null, Duration.ofMinutes(3));
        return !outcome.timedOut && outcome.exitCode == 0;
    }

    private static void copyFixture(Path dest) throws IOException {
        URL root = PathwayBSuiteSignOffIntegrationTest.class.getClassLoader()
                .getResource("b-suite-desensitized");
        if (root == null) {
            throw new IOException("missing test resource b-suite-desensitized");
        }
        Path src;
        try {
            src = Paths.get(URI.create(root.toURI().toString()));
        } catch (Exception e) {
            throw new IOException("cannot resolve fixture URI: " + root, e);
        }
        Files.createDirectories(dest);
        Files.walkFileTree(src, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
                    throws IOException {
                Files.createDirectories(dest.resolve(src.relativize(dir).toString()));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path target = dest.resolve(src.relativize(file).toString());
                Files.createDirectories(target.getParent());
                Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void gitInitWithCommit(Path ws) throws Exception {
        ProcessInvoker real = new ProcessInvoker.RealProcessInvoker();
        run(real, ws, "git", "init", "--template=");
        run(real, ws, "git", "add", "-A");
        run(real, ws, "git", "-c", "user.name=t", "-c", "user.email=t@t",
                "commit", "-m", "baseline desensitized customer");
    }

    private static void run(ProcessInvoker invoker, Path cwd, String... argv) throws Exception {
        ProcessInvoker.ProcessOutcome o = invoker.run(
                Arrays.asList(argv), cwd, null, java.time.Duration.ofMinutes(2));
        if (o.exitCode != 0) {
            throw new IllegalStateException("cmd failed " + Arrays.toString(argv) + "\n" + o.stderr);
        }
    }
}
