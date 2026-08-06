package com.ai4se.execution.support;

import java.util.Locale;

/**
 * Problem class: unattended Model CLI must not block on interactive tool/workspace approval
 * when the Stage Contract already requires the role to write.
 * <p>
 * Scope is derived from the <b>role write surface</b> (what Contract/prompt asks the model to
 * produce), not from a vendor flag and not from a single-role special case.
 * <ul>
 *   <li>{@link #NONE} — no Contract write; no unattended write flags</li>
 *   <li>{@link #STORY_ARTIFACT} — write under {@code .story/...} only (Analysis/Planning/Review)</li>
 *   <li>{@link #BUSINESS_SOURCE} — mutate Allowed business sources (Development)</li>
 * </ul>
 * Adapters map this enum to vendor CLI flags via {@link UnattendedPermissionPolicy}.
 */
public enum UnattendedWriteScope {
    NONE,
    STORY_ARTIFACT,
    BUSINESS_SOURCE;

    /**
     * Resolve write authority from package role. Unknown roles stay {@link #NONE}
     * (fail closed: no silent full-bypass).
     */
    public static UnattendedWriteScope forRole(String role) {
        if (role == null) {
            return NONE;
        }
        String r = role.trim().toLowerCase(Locale.ROOT);
        if ("development".equals(r) || "dev".equals(r)) {
            return BUSINESS_SOURCE;
        }
        if ("analysis".equals(r)
                || "planning".equals(r)
                || "plan".equals(r)
                || "review".equals(r)) {
            return STORY_ARTIFACT;
        }
        return NONE;
    }

    /** @deprecated prefer {@link #forRole}; kept for call-site migration. */
    @Deprecated
    public static boolean isBusinessSourceRole(String role) {
        return forRole(role) == BUSINESS_SOURCE;
    }

    /** @deprecated prefer {@link #forRole}. */
    @Deprecated
    public static boolean isStoryArtifactRole(String role) {
        return forRole(role) == STORY_ARTIFACT;
    }
}
