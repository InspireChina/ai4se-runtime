package com.ai4se.execution.support;

/**
 * Known headless Model CLI families. Adding a vendor <b>requires</b> a mapping in
 * {@link UnattendedPermissionPolicy} — that is the extensibility gate for the
 * "Contract write → unattended must not hang on interactive approval" problem class.
 */
public enum CliVendor {
    CLAUDE,
    CURSOR,
    CODEX

    // Next: OPENCODE, … — add constant AND mapping in UnattendedPermissionPolicy
    // in the same change; Adapter must call UnattendedPermissionPolicy.apply(this, role, argv).
}
