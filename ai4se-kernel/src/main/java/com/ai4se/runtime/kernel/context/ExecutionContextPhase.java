package com.ai4se.runtime.kernel.context;

/**
 * ExecutionContext lifecycle phases (Frozen Invariants §3.2).
 * Materializing → Active → Frozen.
 */
public enum ExecutionContextPhase {
    MATERIALIZING,
    ACTIVE,
    FROZEN
}
