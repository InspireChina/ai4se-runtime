package com.ai4se.orchestration.session;

/**
 * Control decision for pathway Session (W9).
 * Role switch defaults to {@link #NEW}; same-role continue is {@link #RESUME}.
 */
public enum SessionAction {
    /** Open a new Session (rebuild package via 02). Default on role switch. */
    NEW,
    /** Continue the same Session / same role (02 may compress-rebuild; chat is not the source of truth). */
    RESUME
}
