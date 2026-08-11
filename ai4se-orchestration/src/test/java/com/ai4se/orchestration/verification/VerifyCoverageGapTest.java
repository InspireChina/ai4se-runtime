package com.ai4se.orchestration.verification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;

final class VerifyCoverageGapTest {

    @Test
    void frontendDiffBackendOnlyEntriesDisclosesFrontendGap() {
        VerifyCoverageGap.Assessment a = VerifyCoverageGap.assess(
                Arrays.asList(
                        "src/A.java",
                        "wmp-be-frontend/src/foo.test.ts",
                        "wmp-be-frontend/src/bar.vue"),
                Collections.singletonList("mvn -q test"));
        assertTrue(a.hasGap());
        assertEquals("frontend", a.gapLabel());
        assertTrue(a.touched().contains(VerifyCoverageGap.SURFACE_FRONTEND));
        assertTrue(a.touched().contains(VerifyCoverageGap.SURFACE_BACKEND));
        assertTrue(a.covered().contains(VerifyCoverageGap.SURFACE_BACKEND));
        assertFalse(a.covered().contains(VerifyCoverageGap.SURFACE_FRONTEND));
        assertTrue(a.detailLine().contains("disclosure_only=true"));
    }

    @Test
    void frontendEntryCoversFrontendDiff() {
        VerifyCoverageGap.Assessment a = VerifyCoverageGap.assess(
                Collections.singletonList("wmp-be-frontend/src/foo.test.ts"),
                Arrays.asList(
                        "mvn -q test",
                        "cd wmp-be-frontend && npx vitest run"));
        assertFalse(a.hasGap());
        assertEquals("none", a.gapLabel());
    }

    @Test
    void backendOnlyDiffNoGapWithMvn() {
        VerifyCoverageGap.Assessment a = VerifyCoverageGap.assess(
                Collections.singletonList("src/main/java/com/x/A.java"),
                Collections.singletonList("mvn -q test"));
        assertFalse(a.hasGap());
        assertEquals("none", a.gapLabel());
    }

    @Test
    void emptyDiffNoGap() {
        VerifyCoverageGap.Assessment a = VerifyCoverageGap.assess(
                Collections.<String>emptyList(),
                Collections.singletonList("mvn -q test"));
        assertFalse(a.hasGap());
        assertEquals("none", a.gapLabel());
    }
}
