package com.ai4se.runtime.common.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

final class MarkdownListsTest {

    @Test
    void acceptsBareLinesUnderHeaderWithoutBulletMarkers() {
        String md = ""
                + "## Allowed Files\n\n"
                + "wmp-be-backend/pom.xml\n"
                + "wmp-be-backend/src/A.java\n\n"
                + "## Design\n\n"
                + "ignore me\n";
        List<String> got = MarkdownLists.extractSection(
                md, h -> h.contains("allowed"), item -> item);
        assertEquals(2, got.size());
        assertEquals("wmp-be-backend/pom.xml", got.get(0));
        assertEquals("wmp-be-backend/src/A.java", got.get(1));
    }

    @Test
    void stripsOptionalBulletMarkers() {
        String md = "## Priority1\n\n- slices/a.md\n* slices/b.md\n";
        List<String> got = MarkdownLists.extractSection(
                md, h -> h.contains("priority1"), item -> item);
        assertEquals(2, got.size());
        assertEquals("slices/a.md", got.get(0));
        assertEquals("slices/b.md", got.get(1));
    }

    @Test
    void leavesSectionWhenNextH2Starts() {
        String md = "## Allowed Files\n\nfoo/a.java\n## Other\n\nbar/b.java\n";
        List<String> got = MarkdownLists.extractSection(
                md, h -> h.contains("allowed"), item -> item);
        assertEquals(1, got.size());
        assertEquals("foo/a.java", got.get(0));
    }

    @Test
    void mapperNullOrBlankSkipsLine() {
        String md = "## Allowed Files\n\nkeep/me.java\n\nskip-me-blank\n";
        List<String> got = MarkdownLists.extractSection(
                md,
                h -> h.contains("allowed"),
                item -> "skip-me-blank".equals(item) ? null : item);
        assertEquals(1, got.size());
        assertTrue(got.get(0).contains("keep/me.java"));
    }
}
