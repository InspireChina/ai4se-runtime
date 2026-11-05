package com.ai4se.orchestration.production;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ai4se.context.onboard.OnboardRepoScript;
import com.ai4se.execution.cursor.CursorCliAdapter;
import com.ai4se.execution.support.ProcessInvoker;
import com.ai4se.orchestration.analysis.StageGateException;
import com.ai4se.orchestration.run.ProductionTerminal;
import com.ai4se.orchestration.run.RunLedger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Evidence-triggered: PackageRefuse after beginRun must settle FAILED_POLICY (exit 50),
 * never leave a RUNNING ledger without a terminal.
 */
final class ProductionPathwayPackageRefuseSettlesTest {

    @TempDir
    Path temp;

    @Test
    void placeholderAcceptanceSettlesFailedPolicyNotRunning() throws Exception {
        Path ws = prepareGitWorkspace();
        Path seed = temp.resolve("seed-placeholder.md");
        Files.write(
                seed,
                ("## raw\nx\n## goal\ny\n## in_scope\n- a\n## out_of_scope\n- b\n"
                        + "## Acceptance\n- TBD\n")
                        .getBytes(StandardCharsets.UTF_8));

        ProductionRunRequest request = ProductionRunRequest.builder(ws, "story-refuse")
                .seedRequirement(seed)
                .writeScope("src/main/java")
                .maxDevelopmentRounds(1)
                .build();

        ProcessInvoker invoker = new ProcessInvoker.RealProcessInvoker();
        ProductionRunResult result = ProductionPathway.run(
                request, invoker, fakeCursor(invoker));

        assertEquals(ProductionTerminal.FAILED_POLICY, result.terminal);
        assertEquals(50, result.exitCode);
        assertTrue(
                result.detailOrNull != null && result.detailOrNull.toLowerCase().contains("acceptance"),
                result.detailOrNull);

        RunLedger ledger = RunLedger.openExisting(ws, "story-refuse");
        RunLedger.RunStateSnapshot snap = ledger.readState();
        assertEquals(ProductionTerminal.FAILED_POLICY.name(), snap.terminalOrNull);
        assertFalse("RUNNING".equals(snap.statusOrNull), "status=" + snap.statusOrNull);

        boolean sawStop = false;
        for (String line : ledger.readEventLines()) {
            if (line.contains("\"type\":\"run_stopped\"")
                    && line.contains(ProductionTerminal.FAILED_POLICY.name())) {
                sawStop = true;
                break;
            }
        }
        assertTrue(sawStop, "expected run_stopped FAILED_POLICY event");
    }

    @Test
    void acceptanceCriteriaAliasIsJudgableAtWorkspaceGate() throws Exception {
        Path ws = prepareGitWorkspace();
        Path seed = temp.resolve("seed-criteria.md");
        Files.write(
                seed,
                ("## raw\nx\n## goal\ny\n## in_scope\n- a\n## out_of_scope\n- b\n"
                        + "## Acceptance criteria\n"
                        + "1. instance field populated\n"
                        + "2. static field unchanged\n")
                        .getBytes(StandardCharsets.UTF_8));

        ProductionRunRequest request = ProductionRunRequest.builder(ws, "story-alias")
                .seedRequirement(seed)
                .writeScope("src/main/java")
                .build();

        ProductionPathway.validateWorkspaceGates(
                ws, request, new ProcessInvoker.RealProcessInvoker());
    }

    @Test
    void plainProseAcceptanceIsRejected() throws Exception {
        Path seed = temp.resolve("seed-prose.md");
        Files.write(
                seed,
                ("## raw\nx\n## goal\ny\n## in_scope\n- a\n## out_of_scope\n- b\n"
                        + "## Acceptance\n\n大概正确就可以\n")
                        .getBytes(StandardCharsets.UTF_8));
        StageGateException ex = assertThrows(
                StageGateException.class,
                () -> ProductionPathway.requireJudgableAcceptance(seed));
        assertTrue(ex.getMessage().toLowerCase().contains("acceptance"), ex.getMessage());
    }

    @Test
    void bulletAcceptanceIsJudgable() throws Exception {
        Path seed = temp.resolve("seed-bullet.md");
        Files.write(
                seed,
                ("## raw\nx\n## goal\ny\n## in_scope\n- a\n## out_of_scope\n- b\n"
                        + "## Acceptance\n- criterion\n")
                        .getBytes(StandardCharsets.UTF_8));
        assertDoesNotThrow(() -> ProductionPathway.requireJudgableAcceptance(seed));
    }

    @Test
    void numberedAcceptanceIsJudgable() throws Exception {
        Path seedDot = temp.resolve("seed-numbered-dot.md");
        Files.write(
                seedDot,
                ("## raw\nx\n## goal\ny\n## in_scope\n- a\n## out_of_scope\n- b\n"
                        + "## Acceptance\n1. criterion\n")
                        .getBytes(StandardCharsets.UTF_8));
        assertDoesNotThrow(() -> ProductionPathway.requireJudgableAcceptance(seedDot));

        Path seedParen = temp.resolve("seed-numbered-paren.md");
        Files.write(
                seedParen,
                ("## raw\nx\n## goal\ny\n## in_scope\n- a\n## out_of_scope\n- b\n"
                        + "## Acceptance\n1) criterion\n")
                        .getBytes(StandardCharsets.UTF_8));
        assertDoesNotThrow(() -> ProductionPathway.requireJudgableAcceptance(seedParen));
    }

    private CursorCliAdapter fakeCursor(ProcessInvoker invoker) throws Exception {
        Path stub = temp.resolve("fake-cursor");
        Files.write(
                stub,
                ("#!/usr/bin/env bash\necho stub-cursor\nexit 0\n")
                        .getBytes(StandardCharsets.UTF_8));
        stub.toFile().setExecutable(true);
        return new CursorCliAdapter(invoker, stub.toAbsolutePath().toString());
    }

    private Path prepareGitWorkspace() throws Exception {
        Path ws = temp.resolve("ws");
        Files.createDirectories(ws.resolve("src/main/java"));
        Files.write(
                ws.resolve("src/main/java/A.java"),
                "class A {}\n".getBytes(StandardCharsets.UTF_8));
        ProcessInvoker invoker = new ProcessInvoker.RealProcessInvoker();
        run(invoker, ws, "git", "init", "--template=");
        run(invoker, ws, "git", "add", "-A");
        run(invoker, ws, "git", "-c", "user.name=t", "-c", "user.email=t@t", "commit", "-m", "init");
        OnboardRepoScript.run(ws);
        Files.write(
                ws.resolve(".ai4se/repository/entries.yaml"),
                ("build:\n  - true\ntest:\n  - true\n").getBytes(StandardCharsets.UTF_8));
        run(invoker, ws, "git", "add", "-A");
        run(invoker, ws, "git", "-c", "user.name=t", "-c", "user.email=t@t", "commit", "-m", "onboard");
        return ws;
    }

    private static void run(ProcessInvoker invoker, Path ws, String... argv) throws Exception {
        ProcessInvoker.ProcessOutcome out =
                invoker.run(Arrays.asList(argv), ws, null, Duration.ofSeconds(30));
        if (out.exitCode != 0) {
            throw new IllegalStateException(out.stderr + out.stdout);
        }
    }
}
