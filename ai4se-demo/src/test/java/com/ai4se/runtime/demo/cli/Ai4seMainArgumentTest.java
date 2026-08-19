package com.ai4se.runtime.demo.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

final class Ai4seMainArgumentTest {

    @Test
    void helpExitZeroAndOmitsFixtureKnobs() throws Exception {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        PrintStream prev = System.out;
        System.setOut(new PrintStream(buf, true, "UTF-8"));
        try {
            int code = Ai4seMain.run(new String[] {"--help"});
            assertEquals(0, code);
        } finally {
            System.setOut(prev);
        }
        String help = new String(buf.toByteArray(), StandardCharsets.UTF_8).toLowerCase();
        assertTrue(help.contains("run"));
        assertTrue(help.contains("write-scope"));
        assertTrue(help.contains("scorecard"));
        assertTrue(help.contains("legacy-fixture"));
        assertTrue(!help.contains("--suite"));
        assertTrue(!help.contains("--fixture"));
        assertTrue(!help.contains("--seeded"));
        assertTrue(!help.contains("--hybrid"));
        assertTrue(!help.contains("devmutation"));
        assertTrue(!help.contains("--wave"));
    }

    @Test
    void runRequiresWorkspaceStoryAndWriteScope() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> Ai4seMain.RunArgs.parse(new String[] {"--workspace", "/tmp/ws"}, true));
        assertTrue(ex.getMessage().contains("story") || ex.getMessage().contains("write-scope"),
                ex.getMessage());
    }

    @Test
    void rejectsLegacyFixtureFlags() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> Ai4seMain.RunArgs.parse(new String[] {
                    "--workspace", "/tmp/ws",
                    "--story", "s1",
                    "--write-scope", "src/main/java",
                    "--script", "V4"
                }, true));
        assertTrue(ex.getMessage().toLowerCase().contains("unsupported"), ex.getMessage());
    }

    @Test
    void parsesMinimalRunArgs() {
        Ai4seMain.RunArgs a = Ai4seMain.RunArgs.parse(new String[] {
            "--workspace", "/tmp/ws",
            "--story", "story-1",
            "--requirement", "/tmp/seed.md",
            "--write-scope", "src/main/java",
            "--write-scope", "src/test/java",
            "--max-dev-rounds", "2"
        }, true);
        assertEquals("story-1", a.storyId);
        assertEquals(2, a.maxDevRounds);
        assertEquals(2, a.writeScopes.size());
        assertEquals("cursor", a.adapter);
    }

    @Test
    void parsesControlledAdapterAndRejectsUnknown() {
        Ai4seMain.RunArgs codex = Ai4seMain.RunArgs.parse(new String[] {
            "--workspace", "/tmp/ws", "--story", "s1", "--write-scope", "src/main/java",
            "--adapter", "codex"
        }, true);
        assertEquals("codex", codex.adapter);
        Ai4seMain.RunArgs claude = Ai4seMain.RunArgs.parse(new String[] {
            "--workspace", "/tmp/ws", "--story", "s1", "--write-scope", "src/main/java",
            "--adapter", "claude-cli"
        }, true);
        assertEquals("claude", claude.adapter);
        assertThrows(IllegalArgumentException.class, () -> Ai4seMain.RunArgs.parse(new String[] {
            "--workspace", "/tmp/ws", "--story", "s1", "--write-scope", "src/main/java",
            "--adapter", "fake"
        }, true));
    }

    @Test
    void rejectsNonPositiveMaxDevRounds() {
        IllegalArgumentException zero = assertThrows(
                IllegalArgumentException.class,
                () -> Ai4seMain.RunArgs.parse(new String[] {
                    "--workspace", "/tmp/ws",
                    "--story", "s1",
                    "--write-scope", "src/main/java",
                    "--max-dev-rounds", "0"
                }, true));
        assertTrue(zero.getMessage().contains("max-dev-rounds"), zero.getMessage());

        IllegalArgumentException negative = assertThrows(
                IllegalArgumentException.class,
                () -> Ai4seMain.RunArgs.parse(new String[] {
                    "--workspace", "/tmp/ws",
                    "--story", "s1",
                    "--write-scope", "src/main/java",
                    "--max-dev-rounds", "-3"
                }, true));
        assertTrue(negative.getMessage().contains("max-dev-rounds"), negative.getMessage());
    }

    @Test
    void scorecardRejectsArmAAndRequiresPairBaselineModel() {
        IllegalArgumentException armA = assertThrows(
                IllegalArgumentException.class,
                () -> Ai4seMain.ScorecardArgs.parse(new String[] {
                    "--workspace", "/tmp/ws",
                    "--story", "s1",
                    "--arm", "A",
                    "--pair-id", "p1",
                    "--baseline-commit", "abc",
                    "--model-id", "m1"
                }));
        assertTrue(armA.getMessage().toLowerCase().contains("arm a"), armA.getMessage());

        IllegalArgumentException missingPair = assertThrows(
                IllegalArgumentException.class,
                () -> Ai4seMain.ScorecardArgs.parse(new String[] {
                    "--workspace", "/tmp/ws",
                    "--story", "s1",
                    "--arm", "B",
                    "--baseline-commit", "abc",
                    "--model-id", "m1"
                }));
        assertTrue(missingPair.getMessage().contains("pair-id"), missingPair.getMessage());
    }

    @Test
    void scorecardRejectsNegativeCountersAndBadVerdict() {
        IllegalArgumentException neg = assertThrows(
                IllegalArgumentException.class,
                () -> Ai4seMain.ScorecardArgs.parse(new String[] {
                    "--workspace", "/tmp/ws",
                    "--story", "s1",
                    "--pair-id", "p1",
                    "--baseline-commit", "abc",
                    "--model-id", "m1",
                    "--input-tokens", "-1"
                }));
        assertTrue(neg.getMessage().contains("input-tokens"), neg.getMessage());

        IllegalArgumentException verdict = assertThrows(
                IllegalArgumentException.class,
                () -> Ai4seMain.ScorecardArgs.parse(new String[] {
                    "--workspace", "/tmp/ws",
                    "--story", "s1",
                    "--pair-id", "p1",
                    "--baseline-commit", "abc",
                    "--model-id", "m1",
                    "--diff-verdict", "maybe"
                }));
        assertTrue(verdict.getMessage().contains("diff-verdict"), verdict.getMessage());
    }

    @Test
    void scorecardParsesValidArmBArgs() {
        Ai4seMain.ScorecardArgs a = Ai4seMain.ScorecardArgs.parse(new String[] {
            "--workspace", "/tmp/ws",
            "--story", "s1",
            "--arm", "B",
            "--pair-id", "pair-9",
            "--baseline-commit", "deadbeef",
            "--model-id", "cursor",
            "--tool-calls", "na",
            "--diff-verdict", "minor_fix"
        });
        assertEquals("B", a.arm);
        assertEquals("pair-9", a.pairId);
        assertEquals("na", a.toolCalls);
        assertEquals("minor_fix", a.diffVerdict);
    }
}
