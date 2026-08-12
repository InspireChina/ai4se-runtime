package com.ai4se.orchestration.production;

import com.ai4se.orchestration.analysis.AllowedPathSchema;
import com.ai4se.orchestration.analysis.StageGateException;
import com.ai4se.orchestration.development.DiffScopeGuard;
import com.ai4se.runtime.common.util.Strings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Operator-authorized write ceiling for production runs.
 *
 * <p>Distinct from Plan Allowed: write-scope is human auth; Plan Allowed must be a subset
 * (exact path or directory prefix, same semantics as {@link DiffScopeGuard}).
 */
public final class OperatorWriteScope {

    private OperatorWriteScope() {
    }

    public static List<String> normalizeAndValidate(List<String> rawScopes) {
        if (rawScopes == null || rawScopes.isEmpty()) {
            throw new StageGateException("writeScope required — at least one relative path/directory");
        }
        List<String> out = new ArrayList<String>();
        for (String raw : rawScopes) {
            out.add(validateOne(raw));
        }
        return Collections.unmodifiableList(out);
    }

    public static String validateOne(String raw) {
        String n = AllowedPathSchema.requireBareRelativePath(raw);
        String lower = n.toLowerCase(Locale.ROOT);
        if (lower.equals(".git") || lower.startsWith(".git/")) {
            throw new StageGateException("writeScope must not include .git/: " + raw);
        }
        if (lower.equals(".ai4se") || lower.startsWith(".ai4se/")) {
            throw new StageGateException("writeScope must not include .ai4se/: " + raw);
        }
        if (lower.equals(".story") || lower.startsWith(".story/")) {
            throw new StageGateException("writeScope must not include .story/: " + raw);
        }
        return n;
    }

    /** Plan Allowed ⊆ operator writeScope (directory prefixes allowed). */
    public static void requirePlanAllowedSubset(List<String> planAllowed, List<String> writeScope) {
        if (planAllowed == null || planAllowed.isEmpty()) {
            throw new StageGateException("Plan Allowed is empty");
        }
        List<String> scope = normalizeAndValidate(writeScope);
        List<String> violations = DiffScopeGuard.findViolations(planAllowed, scope);
        if (!violations.isEmpty()) {
            throw new StageGateException(
                    "Plan Allowed must be subset of operator writeScope; outside: " + violations);
        }
    }

    public static boolean isForbiddenBusinessScopeToken(String path) {
        if (Strings.isBlank(path)) {
            return true;
        }
        try {
            validateOne(path);
            return false;
        } catch (StageGateException e) {
            return true;
        }
    }
}
