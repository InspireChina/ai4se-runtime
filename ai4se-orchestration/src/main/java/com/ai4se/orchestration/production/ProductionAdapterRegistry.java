package com.ai4se.orchestration.production;

import com.ai4se.execution.api.ModelCliAdapter;
import com.ai4se.execution.claude.ClaudeCliAdapter;
import com.ai4se.execution.codex.CodexCliAdapter;
import com.ai4se.execution.cursor.CursorCliAdapter;
import com.ai4se.execution.support.ProcessInvoker;
import com.ai4se.runtime.common.util.Strings;
import java.util.Locale;

/** Explicit allow-list for adapters that may be injected into production runs. */
public final class ProductionAdapterRegistry {

    private ProductionAdapterRegistry() {
    }

    public static ModelCliAdapter create(String selection, ProcessInvoker invoker, String defaultModel) {
        String key = normalize(selection);
        if ("cursor".equals(key)) {
            return new CursorCliAdapter(invoker, CursorCliAdapter.resolveBinary(null), defaultModel);
        }
        if ("codex".equals(key)) {
            return new CodexCliAdapter(invoker, CodexCliAdapter.resolveBinary(null), defaultModel);
        }
        if ("claude".equals(key)) {
            return new ClaudeCliAdapter(invoker, ClaudeCliAdapter.resolveBinary(null), defaultModel);
        }
        throw new IllegalArgumentException(
                "Unknown adapter: " + selection + " (use cursor|codex|claude)");
    }

    public static boolean isRegistered(ModelCliAdapter adapter) {
        return adapter instanceof CursorCliAdapter
                || adapter instanceof CodexCliAdapter
                || adapter instanceof ClaudeCliAdapter;
    }

    public static String normalize(String selection) {
        if (Strings.isBlank(selection)) {
            return "cursor";
        }
        String key = selection.trim().toLowerCase(Locale.ROOT);
        if ("cursor-cli".equals(key)) {
            return "cursor";
        }
        if ("codex-cli".equals(key)) {
            return "codex";
        }
        if ("claude-cli".equals(key)) {
            return "claude";
        }
        if ("cursor".equals(key) || "codex".equals(key) || "claude".equals(key)) {
            return key;
        }
        throw new IllegalArgumentException(
                "Unknown adapter: " + selection + " (use cursor|codex|claude)");
    }
}
