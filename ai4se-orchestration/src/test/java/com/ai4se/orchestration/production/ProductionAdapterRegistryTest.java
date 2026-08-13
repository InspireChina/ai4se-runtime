package com.ai4se.orchestration.production;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ai4se.execution.api.AdapterResult;
import com.ai4se.execution.api.ModelCliAdapter;
import com.ai4se.execution.claude.ClaudeCliAdapter;
import com.ai4se.execution.codex.CodexCliAdapter;
import com.ai4se.execution.cursor.CursorCliAdapter;
import com.ai4se.execution.support.FunctionalModelCliAdapter;
import java.util.Collections;
import org.junit.jupiter.api.Test;

final class ProductionAdapterRegistryTest {

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
}
