package com.ai4se.runtime.demo.host;

import static org.junit.jupiter.api.Assertions.*;

import com.ai4se.orchestration.analysis.StageGateException;
import com.ai4se.execution.support.StoryAdapterLease;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class CustomerDeliveryControllerTest {
    @TempDir Path temp;

    @Test
    void changingRequirementProbeOrConfigInvalidatesThePresentedReadiness() throws Exception {
        Path ws = workspace();
        write(ws.resolve(".story/s1/requirement.md"), "## acceptance\n- expected\n");
        write(ws.resolve(".story/s1/planning/plan.md"), "## Allowed Files\nsrc/A.java\n");
        Path probe = ws.resolve(".ai4se/acceptance-probes/s1/ac1.sh");
        write(probe, "exit 0\n");
        Path config = ws.resolve(".story/s1/controller/config.properties");
        write(config, "adapter=codex\n");
        String digest = CustomerDeliveryController.freezeReadiness(ws, "s1");
        CustomerDeliveryController.verifyReadiness(ws, "s1", digest);
        write(probe, "exit 1\n");
        assertThrows(StageGateException.class, () -> CustomerDeliveryController.verifyReadiness(ws, "s1", digest));
        write(probe, "exit 0\n");
        write(config, "adapter=claude\n");
        assertThrows(StageGateException.class, () -> CustomerDeliveryController.verifyReadiness(ws, "s1", digest));
        write(config, "adapter=codex\n");
        write(ws.resolve(".story/s1/requirement.md"), "different business decision\n");
        assertThrows(StageGateException.class, () -> CustomerDeliveryController.verifyReadiness(ws, "s1", digest));
    }

    @Test
    void pendingRunCannotReceiveBusinessAnswersOrResetItsBudget() throws Exception {
        Path ws = workspace();
        write(ws.resolve(".story/s1/controller/job.properties"), "status=RUNNING\n");
        assertEquals(50, CustomerDeliveryController.run(new String[] {"answer", "--workspace", ws.toString(),
                "--story", "s1", "--answer", "change scope", "--actor", "operator"}));
        write(ws.resolve(".story/s1/controller/job.properties"), "status=STOPPED\n");
        assertEquals(50, CustomerDeliveryController.run(new String[] {"start", "--workspace", ws.toString(),
                "--story", "s1", "--approve", "old", "--actor", "operator"}));
    }

    @Test
    void repositoryLeaseRejectsASecondTaskBeforeRecordingApproval() throws Exception {
        Path ws = workspace();
        try (StoryAdapterLease held = StoryAdapterLease.acquireFile(ws.resolve(".ai4se/host/controller.lock"), "test")) {
            assertEquals(50, CustomerDeliveryController.run(new String[] {"start", "--workspace", ws.toString(),
                    "--story", "s1", "--approve", "old", "--actor", "operator"}));
            assertFalse(Files.exists(ws.resolve(".story/s1/controller/start-approval.properties")));
        }
    }

    @Test
    void missingNpmScriptIsRejectedBeforeSpendingADevelopmentRound() throws Exception {
        Path ws = workspace();
        write(ws.resolve("frontend/package.json"), "{\"scripts\":{\"build\":\"echo build\"}}");
        write(ws.resolve(".ai4se/acceptance-probes/s1/ac1.sh"), "npm --prefix frontend run nonexistent\n");
        StageGateException e = assertThrows(StageGateException.class, () -> ProbeReadiness.check(ws, "s1"));
        assertTrue(e.getMessage().contains("nonexistent"));
    }

    @Test
    void uncommonCustomerSourceLayoutCanBeProvidedWithoutOpeningControlDirectories() throws Exception {
        Path ws = workspace();
        write(ws.resolve("feature_service/handler.py"), "def handle(): return 1\n");
        assertEquals(Arrays.asList("feature_service"), CustomerDeliveryController.validateWriteCeiling(
                ws, Arrays.asList("feature_service", "feature_service")));
        assertThrows(StageGateException.class, () -> CustomerDeliveryController.validateWriteCeiling(
                ws, Arrays.asList(".ai4se")));
        assertThrows(StageGateException.class, () -> CustomerDeliveryController.validateWriteCeiling(
                ws, Arrays.asList("../outside")));
    }

    @Test
    void detachedWorkerOutlivesTheLaunchingCliAndWritesFailureReport() throws Exception {
        Path ws = workspace();
        write(ws.resolve(".ai4se/repository/facts.md"), "# Facts\n- source: src/A.java\n");
        write(ws.resolve(".ai4se/repository/module-map.md"), "# Modules\n- src\n");
        write(ws.resolve(".ai4se/repository/baseline.md"), "# Baseline\n");
        write(ws.resolve(".ai4se/repository/entries.yaml"), "test: unknown\n");
        Path request = temp.resolve("request.md"); write(request, "Add a meaningful behavior with tests\n");
        Path binary = temp.resolve("fake-codex");
        write(binary, "#!/bin/sh\nsleep 2\necho controlled-test-adapter-failure >&2\nexit 1\n");
        assertTrue(binary.toFile().setExecutable(true));
        String cp = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        ProcessBuilder launcher = new ProcessBuilder(Arrays.asList(
                System.getProperty("java.home") + "/bin/java", "-cp", cp,
                "com.ai4se.runtime.demo.cli.Ai4seMain", "deliver", "prepare", "--workspace", ws.toString(),
                "--story", "s1", "--adapter", "codex", "--request-file", request.toString()));
        launcher.environment().put("AI4SE_CODEX_BIN", binary.toString());
        launcher.redirectErrorStream(true).redirectOutput(temp.resolve("launcher.log").toFile());
        Process process = launcher.start();
        assertTrue(process.waitFor(5, TimeUnit.SECONDS));
        assertEquals(0, process.exitValue(), new String(Files.readAllBytes(temp.resolve("launcher.log")), StandardCharsets.UTF_8));
        Path state = ws.resolve(".story/s1/controller/job.properties");
        for (int i = 0; i < 120; i++) {
            if ("STOPPED".equals(CustomerDeliveryController.load(state).getProperty("status"))
                    && Files.exists(ws.resolve(".story/s1/controller/acceptance-report.md"))) { break; }
            Thread.sleep(100);
        }
        Properties job = CustomerDeliveryController.load(state);
        assertEquals("STOPPED", job.getProperty("status"));
        String report = new String(Files.readAllBytes(ws.resolve(".story/s1/controller/acceptance-report.md")), StandardCharsets.UTF_8);
        assertTrue(report.contains("未产生 Verification 结果"));
        assertTrue(report.contains("STOPPED"));
        assertFalse(Files.exists(ws.resolve(".story/s1/development/changed-files.md")));
    }

    private Path workspace() throws Exception {
        Path ws = temp.resolve("customer"); Files.createDirectories(ws);
        git(ws, "init", "-q"); git(ws, "config", "user.name", "Validation");
        git(ws, "config", "user.email", "validation@example.invalid");
        write(ws.resolve("src/A.java"), "class A {}\n");
        git(ws, "add", "src/A.java"); git(ws, "commit", "-qm", "baseline");
        Files.createDirectories(ws.resolve(".ai4se/repository")); Files.createDirectories(ws.resolve(".story"));
        return ws;
    }
    private void git(Path ws, String... args) throws Exception {
        java.util.List<String> argv = new java.util.ArrayList<String>(Arrays.asList("git"));
        argv.addAll(Arrays.asList(args));
        Process p = new ProcessBuilder(argv).directory(ws.toFile()).redirectErrorStream(true)
                .redirectOutput(temp.resolve("git.log").toFile()).start();
        assertEquals(0, p.waitFor());
    }
    private void write(Path path, String body) throws Exception {
        Files.createDirectories(path.getParent()); Files.write(path, body.getBytes(StandardCharsets.UTF_8));
    }
}
