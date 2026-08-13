package com.ai4se.orchestration.review;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class ReviewDecisionTest {

    @Test
    void parsesPassConditionalRejectIncludingAttachedPassWording() {
        assertEquals(ReviewDecision.PASS, ReviewDecision.parse("通过"));
        assertEquals(ReviewDecision.PASS, ReviewDecision.parse("PASS"));
        assertEquals(ReviewDecision.PASS, ReviewDecision.parse("pass"));
        assertEquals(ReviewDecision.CONDITIONAL, ReviewDecision.parse("附条件"));
        assertEquals(ReviewDecision.CONDITIONAL, ReviewDecision.parse("附条件通过"));
        assertEquals(ReviewDecision.CONDITIONAL, ReviewDecision.parse("CONDITIONAL"));
        assertEquals(ReviewDecision.CONDITIONAL, ReviewDecision.parse("conditional accept"));
        assertEquals(ReviewDecision.REJECT, ReviewDecision.parse("驳回"));
        assertEquals(ReviewDecision.REJECT, ReviewDecision.parse("REJECT"));
    }

    @Test
    void onlyPassAllowsAutomaticDelivery() {
        assertTrue(ReviewDecision.PASS.allowsAutomaticDelivery());
        assertFalse(ReviewDecision.CONDITIONAL.allowsAutomaticDelivery());
        assertFalse(ReviewDecision.REJECT.allowsAutomaticDelivery());
    }

    @Test
    void extractDecisionFromHeadingMarkdown() {
        String md = "# Review\n\n## decision\n\n**附条件通过**\n\n## residual_risk\n\n- gap\n";
        assertEquals("附条件通过", ReviewRecords.extractDecisionText(md));
        assertEquals(ReviewDecision.CONDITIONAL, ReviewDecision.parse(ReviewRecords.extractDecisionText(md)));
    }

    @Test
    void blankDecisionRejected() {
        assertThrows(Exception.class, () -> ReviewDecision.parse(""));
        assertThrows(Exception.class, () -> ReviewDecision.parse("maybe"));
    }
}
