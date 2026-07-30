package com.ai4se.runtime.demo.delivery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LoopReturnPathTest {

    @Test
    void verificationFail_defaultsToExecution_notRequirement() {
        assertEquals("Execution",
                LoopReturnAdvisor.recommendedReturn(LoopReturnAdvisor.FailKind.SHELL_VERIFY));
        assertEquals("Execution",
                LoopReturnAdvisor.recommendedReturn(LoopReturnAdvisor.FailKind.MATRIX_MISSING));
        assertTrue(LoopReturnAdvisor.forbidsDefaultRequirement(LoopReturnAdvisor.FailKind.SHELL_VERIFY));
        String section = LoopReturnAdvisor.renderSection(LoopReturnAdvisor.FailKind.SHELL_VERIFY);
        assertTrue(section.contains("Recommended return: Execution"));
        assertFalse(section.contains("Recommended return: Requirement"));
    }

    @Test
    void clarificationBlocked_staysClarification() {
        assertEquals("Clarification",
                LoopReturnAdvisor.recommendedReturn(LoopReturnAdvisor.FailKind.CLARIFICATION_BLOCKED));
    }
}
