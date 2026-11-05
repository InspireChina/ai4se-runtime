package com.ai4se.orchestration.lifecycle;

import com.ai4se.context.workspace.WorkspaceSlotVerifier;
import com.ai4se.orchestration.analysis.StageGateException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * W10 · main loop must not force re-onboard per Story.
 * Slots ({@code .ai4se/}) are created once; subsequent Stories reuse them.
 */
public final class OnboardPolicy {

    private OnboardPolicy() {
    }

    /**
     * Assert customer slots already exist and are valid — Pathway must not call onboard again.
     */
    public static void requireSlotsAlreadyPresent(Path workspace) {
        if (workspace == null || !Files.isDirectory(workspace.resolve(".ai4se"))) {
            throw new StageGateException(
                    "Main loop expects existing .ai4se/ slots — onboard once outside Story loop");
        }
        WorkspaceSlotVerifier.requireValid(workspace);
    }

    /** True when re-onboard would be redundant (slots already honest). */
    public static boolean slotsReady(Path workspace) {
        try {
            WorkspaceSlotVerifier.requireValid(workspace);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }
}
