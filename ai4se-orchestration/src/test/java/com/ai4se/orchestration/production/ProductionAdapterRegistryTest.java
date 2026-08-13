package com.ai4se.orchestration.production;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ai4se.execution.api.AdapterResult;
import com.ai4se.execution.api.ModelCliAdapter;
import com.ai4se.execution.claude.ClaudeCliAdapter;
import com.ai4se.execution.codex.CodexCliAdapter;
import com.ai4se.execution.cursor.CursorCliAdapter;
import com.ai4se.execution.support.FunctionalModelCliAdapter;
import com.ai4se.execution.support.ProcessInvoker;
import com.ai4se.orchestration.analysis.StageGateException;
import com.ai4se.orchestration.run.ProductionTerminal;
import com.ai4se.orchestration.run.RunLedger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ProductionAdapterRegistryTest {

    @TempDir
    Path temp;

    @Test
    void acceptsExactlyRegisteredCliFamilies() {
        assertTrue(ProductionAdapterRegistry.isRegistered(new CursorCliAdapter(null, "cursor")));
        assertTrue(ProductionAdapterRegistry.isRegistered(new CodexCliAdapter(null, "codex")));
        assertTrue(ProductionAdapterRegistry.isRegistered(new ClaudeCliAdapter(null, "claude")));
        ModelCliAdapter functional = new FunctionalModelCliAdapter(
                "functional", request -> AdapterResult.ok(0, "", "", Collections.<String, String>emptyMap()));
        assertFalse(ProductionAdapterRegistry.isRegistered(functional));
    }

    @Test
    void factorySupportsAliasesAndRejectsUnknown() {
        assertEquals("cursor-cli", ProductionAdapterRegistry.create("cursor", null, null).name());
        assertEquals("codex-cli", ProductionAdapterRegistry.create("codex", null, null).name());
        assertEquals("claude-cli", ProductionAdapterRegistry.create("claude", null, null).name());
        assertThrows(IllegalArgumentException.class,
                () -> ProductionAdapterRegistry.create("unknown", null, null));
    }

    @Test
    void codexLedgerRejectsCursorResume() throws Exception {
        RunLedger ledger = ledgerWithAdapter("codex-cli");
        StageGateException ex = assertThrows(
                StageGateException.class,
                () -> ProductionPathway.validateResumeAdapter(
                        ledger, new CursorCliAdapter(null, "cursor")));
        assertTrue(ex.getMessage().contains("FAILED_POLICY"), ex.getMessage());
        assertTrue(ex.getMessage().contains("codex-cli"), ex.getMessage());
    }

    @Test
    void cursorLedgerRejectsCodexResume() throws Exception {
        RunLedger ledger = ledgerWithAdapter("cursor-cli");
        StageGateException ex = assertThrows(
                StageGateException.class,
                () -> ProductionPathway.validateResumeAdapter(
                        ledger, new CodexCliAdapter(null, "codex")));
        assertTrue(ex.getMessage().contains("FAILED_POLICY"), ex.getMessage());
        assertTrue(ex.getMessage().contains("cursor-cli"), ex.getMessage());
    }

    @Test
    void productionResumeMismatchSettlesFailedPolicyExit50() throws Exception {
        Path workspace = temp.resolve("resume-mismatch");
        RunLedger ledger = RunLedger.open(workspace, "story-mismatch");
        ledger.beginRun("src/main/java", 3, "codex-cli", "model");

        ProductionRunResult result = ProductionPathway.resume(
                ProductionRunRequest.builder(workspace, "story-mismatch")
                        .writeScope("src/main/java")
                        .build(),
                new ProcessInvoker.RealProcessInvoker(),
                new CursorCliAdapter(null, "cursor"));

        assertEquals(ProductionTerminal.FAILED_POLICY, result.terminal);
        assertEquals(50, result.exitCode);
        assertEquals(ProductionTerminal.FAILED_POLICY.name(),
                RunLedger.openExisting(workspace, "story-mismatch")
                        .readState().terminalOrNull);
    }

    @Test
    void sameAdapterResumeIsAccepted() throws Exception {
        RunLedger ledger = ledgerWithAdapter("codex-cli");
        assertDoesNotThrow(() -> ProductionPathway.validateResumeAdapter(
                ledger, new CodexCliAdapter(null, "codex")));
        assertEquals("pinned", ledger.readState().adapterProvenanceOrNull);
    }

    @Test
    void legacyLedgerResumeIsAcceptedAndMarkedUnpinned() throws Exception {
        Path workspace = temp.resolve("legacy");
        RunLedger ledger = RunLedger.open(workspace, "story-legacy");
        ledger.beginRun("src/main/java", 3);

        assertDoesNotThrow(() -> ProductionPathway.validateResumeAdapter(
                ledger, new CursorCliAdapter(null, "cursor")));
        RunLedger.RunStateSnapshot state = ledger.readState();
        assertTrue(Files.readAllLines(ledger.statePath()).stream()
                .anyMatch(line -> line.contains("adapter_provenance=legacy_unpinned")));
        assertEquals("legacy_unpinned", state.adapterProvenanceOrNull);
    }

    @Test
    void ledgerPersistsModelSelectionProvenance() throws Exception {
        RunLedger cliDefault = RunLedger.open(temp.resolve("model-cli"), "story");
        cliDefault.beginRun("src", 1, "codex-cli", "(cli-default)", "cli-default");
        assertEquals("(cli-default)", cliDefault.readState().modelOrNull);
        assertEquals("cli-default", cliDefault.readState().modelSelectionOrNull);

        RunLedger roleResolved = RunLedger.open(temp.resolve("model-role"), "story");
        roleResolved.beginRun("src", 1, "codex-cli", "(role-resolved)", "role-resolved");
        assertEquals("(role-resolved)", roleResolved.readState().modelOrNull);
        assertEquals("role-resolved", roleResolved.readState().modelSelectionOrNull);

        RunLedger explicit = RunLedger.open(temp.resolve("model-explicit"), "story");
        explicit.beginRun("src", 1, "codex-cli", "gpt-test", "explicit");
        assertEquals("gpt-test", explicit.readState().modelOrNull);
        assertEquals("explicit", explicit.readState().modelSelectionOrNull);
    }

    private RunLedger ledgerWithAdapter(String adapter) throws Exception {
        Path workspace = temp.resolve(adapter.replace('-', '_'));
        RunLedger ledger = RunLedger.open(workspace, "story-pinned");
        ledger.beginRun("src/main/java", 3, adapter, "model");
        return ledger;
    }
}
