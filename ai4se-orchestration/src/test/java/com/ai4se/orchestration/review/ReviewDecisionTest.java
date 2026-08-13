package com.ai4se.orchestration.review;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ai4se.orchestration.analysis.StageGateException;
import org.junit.jupiter.api.Test;

final class ReviewDecisionTest {

    @Test
    void parseStrictAcceptsOnlyExactEnums() {
        assertEquals(ReviewDecision.PASS, ReviewDecision.parseStrict("PASS"));
        assertEquals(ReviewDecision.PASS, ReviewDecision.parseStrict("pass"));
        assertEquals(ReviewDecision.CONDITIONAL, ReviewDecision.parseStrict("CONDITIONAL"));
        assertEquals(ReviewDecision.REJECT, ReviewDecision.parseStrict("REJECT"));
    }

    @Test
    void parseStrictRejectsFailOpenLookalikes() {
        for (String bad : new String[] {
            "NOT PASS", "BYPASS", "PASS WITH CONDITIONS", "通过", "附条件通过", "maybe"
        }) {
            StageGateException ex = assertThrows(
                    StageGateException.class, () -> ReviewDecision.parseStrict(bad), bad);
            assertTrue(ex.getMessage().contains("FAILED_ADAPTER"), ex.getMessage());
        }
    }

    @Test
    void parseMarkdownDecisionAcceptsClosedPhraseSet() {
        assertEquals(ReviewDecision.PASS, ReviewDecision.parseMarkdownDecision("通过"));
        assertEquals(ReviewDecision.PASS, ReviewDecision.parseMarkdownDecision("PASS"));
        assertEquals(ReviewDecision.PASS, ReviewDecision.parseMarkdownDecision("**pass**"));
        assertEquals(ReviewDecision.CONDITIONAL, ReviewDecision.parseMarkdownDecision("附条件"));
        assertEquals(ReviewDecision.CONDITIONAL, ReviewDecision.parseMarkdownDecision("附条件通过"));
        assertEquals(ReviewDecision.CONDITIONAL, ReviewDecision.parseMarkdownDecision("CONDITIONAL"));
        assertEquals(ReviewDecision.REJECT, ReviewDecision.parseMarkdownDecision("驳回"));
        assertEquals(ReviewDecision.REJECT, ReviewDecision.parseMarkdownDecision("REJECT"));
    }

    @Test
    void parseMarkdownDecisionRejectsFailOpenLookalikes() {
        for (String bad : new String[] {
            "NOT PASS", "BYPASS", "PASS WITH CONDITIONS", "conditional accept"
        }) {
            StageGateException ex = assertThrows(
                    StageGateException.class,
                    () -> ReviewDecision.parseMarkdownDecision(bad),
                    bad);
            assertTrue(ex.getMessage().contains("FAILED_ADAPTER"), ex.getMessage());
        }
    }

    @Test
    void onlyPassAllowsAutomaticDelivery() {
        assertTrue(ReviewDecision.PASS.allowsAutomaticDelivery());
        assertFalse(ReviewDecision.CONDITIONAL.allowsAutomaticDelivery());
        assertFalse(ReviewDecision.REJECT.allowsAutomaticDelivery());
    }

    @Test
    void extractDecisionFromHeadingMarkdownOnly() {
        String md = "# Review\n\n## decision\n\n**附条件通过**\n\n## residual_risk\n\n- gap\n";
        assertEquals("附条件通过", ReviewRecords.extractDecisionText(md));
        assertEquals(
                ReviewDecision.CONDITIONAL,
                ReviewDecision.parseMarkdownDecision(ReviewRecords.extractDecisionText(md)));
    }

    @Test
    void verificationPassProseWithoutDecisionYieldsEmpty() {
        String md = ""
                + "# Review Result\n\n"
                + "Verification outcome: PASS\n"
                + "command_ok: true\n"
                + "The suite is green.\n";
        assertEquals("", ReviewRecords.extractDecisionText(md));
        assertThrows(
                StageGateException.class,
                () -> ReviewDecision.parseMarkdownDecision(
                        ReviewRecords.extractDecisionText(md)));
    }

    @Test
    void blankDecisionRejected() {
        assertThrows(Exception.class, () -> ReviewDecision.parseStrict(""));
        assertThrows(Exception.class, () -> ReviewDecision.parseMarkdownDecision(""));
        assertThrows(Exception.class, () -> ReviewDecision.parseMarkdownDecision("maybe"));
    }
}
