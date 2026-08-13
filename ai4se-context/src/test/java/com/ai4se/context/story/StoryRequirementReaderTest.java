package com.ai4se.context.story;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class StoryRequirementReaderTest {

    @TempDir
    Path temp;

    @Test
    void parsesHeadingsAndBulletAcceptance() throws Exception {
        Path ws = onboarded();
        write(ws, "s1", ""
                + "## raw\nHello world\n\n"
                + "## goal\nSay hello\n\n"
                + "## in_scope\n- api\n\n"
                + "## out_of_scope\n- ui\n\n"
                + "## acceptance\n"
                + "- returns 200\n"
                + "* body has ok\n"
                + "1. logged\n");

        StoryRequirement req = StoryRequirementReader.read(ws, "s1");
        assertEquals("Hello world", req.raw());
        assertEquals("Say hello", req.goal());
        assertEquals(3, req.acceptance().size());
        assertTrue(req.acceptance().contains("returns 200"));
        assertTrue(req.acceptance().contains("body has ok"));
        assertTrue(req.acceptance().contains("logged"));
    }

    @Test
    void normalizesInScopeAliases() {
        Map<String, String> sections = StoryRequirementReader.parseSections(""
                + "## in-scope\nA\n\n"
                + "## out of scope\nB\n");
        assertEquals("A", sections.get("in_scope"));
        assertEquals("B", sections.get("out_of_scope"));
    }

    @Test
    void mapsLimitedAcceptanceHeadingAliases() throws Exception {
        assertEquals("acceptance", StoryRequirementReader.normalizeHeading("Acceptance"));
        assertEquals("acceptance", StoryRequirementReader.normalizeHeading("Acceptance criteria"));
        assertEquals("acceptance", StoryRequirementReader.normalizeHeading("Acceptance Criterion"));

        Path ws = onboarded();
        write(ws, "s-ac", ""
                + "## goal\nG\n\n"
                + "## Acceptance criteria\n"
                + "1. instance field populated\n"
                + "2. static field unchanged\n");
        StoryRequirement req = StoryRequirementReader.read(ws, "s-ac");
        assertEquals(2, req.acceptance().size());
        assertTrue(req.acceptance().get(0).contains("instance field"));
    }

    @Test
    void mapsBackgroundAndAllowedFilesAliases() throws Exception {
        Path ws = onboarded();
        write(ws, "s-alias", ""
                + "## Background\nDeserializing must not mutate statics.\n\n"
                + "## Goal\nIgnore static fields.\n\n"
                + "## Allowed files\n- src/main/java/org/json/JSONObject.java\n\n"
                + "## Out of scope\n- pom.xml\n\n"
                + "## Acceptance\n- instance field populated\n");
        StoryRequirement req = StoryRequirementReader.read(ws, "s-alias");
        assertEquals("Deserializing must not mutate statics.", req.raw());
        assertTrue(req.inScope().contains("JSONObject.java"));
        assertEquals(1, req.acceptance().size());
    }

    @Test
    void parseAcceptanceLinesIgnoresPlainProse() {
        assertTrue(StoryRequirementReader.parseAcceptanceLines("大概正确就可以\n").isEmpty());
        assertEquals(1, StoryRequirementReader.parseAcceptanceLines("- criterion\n").size());
        assertEquals(1, StoryRequirementReader.parseAcceptanceLines("1. criterion\n").size());
        assertEquals(1, StoryRequirementReader.parseAcceptanceLines("1) criterion\n").size());
    }

    @Test
    void unknownAcceptanceLikeHeadingDoesNotAlias() {
        Map<String, String> sections = StoryRequirementReader.parseSections(""
                + "## Done when\n- x\n");
        assertEquals(null, sections.get("acceptance"));
        assertEquals("- x", sections.get("done_when"));
    }

    @Test
    void missingFileThrows() {
        assertThrows(Exception.class, () -> StoryRequirementReader.read(temp, "missing"));
    }

    private Path onboarded() throws Exception {
        Files.createDirectories(temp.resolve(".ai4se"));
        Files.createDirectories(temp.resolve(".story"));
        return temp;
    }

    private void write(Path ws, String id, String body) throws Exception {
        Path dir = ws.resolve(".story").resolve(id);
        Files.createDirectories(dir);
        Files.write(dir.resolve("requirement.md"), ("# S\n\n" + body).getBytes(StandardCharsets.UTF_8));
    }
}
