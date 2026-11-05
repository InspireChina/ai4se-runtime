package com.ai4se.orchestration.run;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class ProductionTerminalMappingTest {

    @Test
    void mapsReviewAdapterFormatFailuresToFailedAdapter() {
        assertEquals(
                ProductionTerminal.FAILED_ADAPTER,
                ProductionTerminal.fromStageGateMessage(
                        "FAILED_ADAPTER: Review Adapter must write review-result.properties"));
        assertEquals(
                ProductionTerminal.FAILED_ADAPTER,
                ProductionTerminal.fromStageGateMessage(
                        "FAILED_ADAPTER: Review Adapter failed (no Adapter retry; Control owns recovery): exit=1"));
    }

    @Test
    void mapsAnalysisAdapterTimeoutToFailedAdapter() {
        assertEquals(
                ProductionTerminal.FAILED_ADAPTER,
                ProductionTerminal.fromStageGateMessage(
                        "Analysis Adapter failed (no Adapter retry; Control owns recovery): Codex CLI timed out"));
    }

    @Test
    void mapsConditionalReviewToNeedsClarification() {
        assertEquals(
                ProductionTerminal.STOPPED_NEEDS_CLARIFICATION,
                ProductionTerminal.fromStageGateMessage(
                        "Review CONDITIONAL — cannot auto Delivery; waiting for human/supplemental verification"));
    }
}
