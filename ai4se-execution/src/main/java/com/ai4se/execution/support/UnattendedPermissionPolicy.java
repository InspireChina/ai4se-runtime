package com.ai4se.execution.support;

import java.util.List;

/**
 * Public capability: unattended permission posture for Model CLI Adapters.
 * <p>
 * <b>Problem class (not a per-role / per-vendor patch):</b> Stage Contract requires the role
 * to write → {@code -p}/print mode must not block on interactive Allow/Trust/MCP prompts.
 * <p>
 * Extension rule for OpenCode / Codex / …:
 * <ol>
 *   <li>Add {@link CliVendor} constant</li>
 *   <li>Add mapping in {@link #appendFlags(CliVendor, UnattendedWriteScope, List)} for
 *       {@link UnattendedWriteScope#STORY_ARTIFACT} and {@link UnattendedWriteScope#BUSINESS_SOURCE}</li>
 *   <li>Adapter {@code buildArgv} calls only {@link #apply(CliVendor, String, List)} — no private
 *       {@code if (isWriteRole)} trees</li>
 * </ol>
 * Role → write surface lives in {@link UnattendedWriteScope#forRole(String)} (one place).
 * Vendor → flags lives only here (one place). Missing vendor mapping fails closed.
 */
public final class UnattendedPermissionPolicy {

    private UnattendedPermissionPolicy() {
    }

    /**
     * Resolve role write surface and append vendor flags. Safe no-op when scope is
     * {@link UnattendedWriteScope#NONE}.
     */
    public static UnattendedWriteScope apply(CliVendor vendor, String role, List<String> afterBinary) {
        if (afterBinary == null) {
            throw new IllegalArgumentException("argv list required");
        }
        if (vendor == null) {
            throw new IllegalArgumentException("CliVendor required — register new CLI families explicitly");
        }
        UnattendedWriteScope scope = UnattendedWriteScope.forRole(role);
        if (scope == UnattendedWriteScope.NONE) {
            return scope;
        }
        appendFlags(vendor, scope, afterBinary);
        return scope;
    }

    static void appendFlags(CliVendor vendor, UnattendedWriteScope scope, List<String> afterBinary) {
        switch (vendor) {
            case CLAUDE:
                appendClaude(scope, afterBinary);
                return;
            case CURSOR:
                appendCursor(scope, afterBinary);
                return;
            default:
                throw new IllegalStateException(
                        "CliVendor." + vendor.name()
                                + " has no UnattendedPermissionPolicy mapping — "
                                + "add STORY_ARTIFACT + BUSINESS_SOURCE flags before shipping this Adapter");
        }
    }

    /**
     * Claude (cloud evidence + product doc): Dev full bypass; story roles acceptEdits only.
     */
    private static void appendClaude(UnattendedWriteScope scope, List<String> afterBinary) {
        if (scope == UnattendedWriteScope.BUSINESS_SOURCE) {
            afterBinary.add("--dangerously-skip-permissions");
            return;
        }
        if (scope == UnattendedWriteScope.STORY_ARTIFACT) {
            afterBinary.add("--permission-mode");
            afterBinary.add("acceptEdits");
            return;
        }
        throw missingScope(CliVendor.CLAUDE, scope);
    }

    /**
     * Cursor (local probe): trust + Smart Auto for story; force for business; MCP approve
     * is the same interactive-hang class.
     */
    private static void appendCursor(UnattendedWriteScope scope, List<String> afterBinary) {
        if (scope == UnattendedWriteScope.BUSINESS_SOURCE) {
            afterBinary.add("--force");
            afterBinary.add("--approve-mcps");
            return;
        }
        if (scope == UnattendedWriteScope.STORY_ARTIFACT) {
            afterBinary.add("--trust");
            afterBinary.add("--auto-review");
            afterBinary.add("--approve-mcps");
            return;
        }
        throw missingScope(CliVendor.CURSOR, scope);
    }

    private static IllegalStateException missingScope(CliVendor vendor, UnattendedWriteScope scope) {
        return new IllegalStateException(
                "UnattendedPermissionPolicy missing flags for " + vendor + " / " + scope);
    }
}
