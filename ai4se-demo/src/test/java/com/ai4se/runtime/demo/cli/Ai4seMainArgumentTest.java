package com.ai4se.runtime.demo.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import com.ai4se.orchestration.run.RunLedger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class Ai4seMainArgumentTest {

    @TempDir
    Path temp;

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
        assertTrue(help.contains("approve-plan"));
        assertTrue(help.contains("bridge prepare-discovery"));
        assertTrue(help.contains("terminal-host"));
        assertTrue(help.contains("answer"));
        assertTrue(help.contains("accept"));
        assertTrue(help.contains("reject"));
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
    void parsesHumanAnswerAndPlanApprovalArguments() {
        Ai4seMain.HumanDecisionArgs answer = Ai4seMain.HumanDecisionArgs.parseAnswer(new String[] {
            "--workspace", "/tmp/ws", "--story", "s1", "--answer", "Use UTC", "--actor", "peng"
        });
        assertEquals("Use UTC", answer.value);
        assertEquals("peng", answer.actor);

        Ai4seMain.HumanDecisionArgs approval = Ai4seMain.HumanDecisionArgs.parseApproval(new String[] {
            "--workspace", "/tmp/ws", "--story", "s1", "--note", "plan reviewed"
        });
        assertEquals("plan reviewed", approval.value);
        assertEquals("operator-cli", approval.actor);
        assertThrows(IllegalArgumentException.class, () -> Ai4seMain.HumanDecisionArgs.parseAnswer(
                new String[] {"--workspace", "/tmp/ws", "--story", "s1"}));

        Ai4seMain.HumanDecisionArgs acceptance = Ai4seMain.HumanDecisionArgs.parseAcceptance(new String[] {
            "--workspace", "/tmp/ws", "--story", "s1", "--note", "customer checked it", "--actor", "peng"
        }, "acceptance note");
        assertEquals("customer checked it", acceptance.value);
        assertEquals("peng", acceptance.actor);
        assertThrows(IllegalArgumentException.class,
                () -> Ai4seMain.HumanDecisionArgs.parseAcceptance(
                        new String[] {"--workspace", "/tmp/ws", "--story", "s1"}, "acceptance note"));
    }

    @Test
    void intakeRequiresExactlyOneRawTextSourceAndAllowsAttachments() {
        Ai4seMain.IntakeArgs ok = Ai4seMain.IntakeArgs.parse(new String[] {
            "--workspace", "/tmp/ws", "--story", "s1", "--text", "add a button",
            "--attachment", "/tmp/mock.png", "--attachment", "/tmp/flow.pdf"
        });
        assertEquals("s1", ok.storyId);
        assertEquals(2, ok.attachments.size());
        assertThrows(IllegalArgumentException.class, () -> Ai4seMain.IntakeArgs.parse(new String[] {
            "--workspace", "/tmp/ws", "--story", "s1"
        }));
        assertThrows(IllegalArgumentException.class, () -> Ai4seMain.IntakeArgs.parse(new String[] {
            "--workspace", "/tmp/ws", "--story", "s1", "--text", "x", "--request-file", "/tmp/nope"
        }));
    }

    @Test
    void parsesSerialQueueCommandsWithoutPretendingTheyRunStories() {
        Ai4seMain.QueueArgs add = Ai4seMain.QueueArgs.parse(new String[] {
            "add", "--workspace", "/tmp/ws", "--story", "s1"
        });
        assertEquals("add", add.action);
        assertEquals("s1", add.storyId);
        Ai4seMain.QueueArgs status = Ai4seMain.QueueArgs.parse(new String[] {
            "status", "--workspace", "/tmp/ws"
        });
        assertEquals("status", status.action);
        assertThrows(IllegalArgumentException.class, () -> Ai4seMain.QueueArgs.parse(new String[] {
            "add", "--workspace", "/tmp/ws"
        }));
    }

    @Test
    void parsesPortableHostInstallAndBoundedBridgeActions() {
        Ai4seMain.InstallArgs install = Ai4seMain.InstallArgs.parse(new String[] {
            "--workspace", "/tmp/ws", "--host", "terminal-host", "--runtime-jar", "/tmp/runtime.jar"
        });
        assertEquals("terminal-host", install.host);
        Ai4seMain.BridgeArgs discovery = Ai4seMain.BridgeArgs.parse(new String[] {
            "prepare-discovery", "--workspace", "/tmp/ws", "--candidate", "initial", "--scope", "repository"
        });
        assertEquals("prepare-discovery", discovery.action);
        Ai4seMain.BridgeArgs specification = Ai4seMain.BridgeArgs.parse(new String[] {
            "submit-specification", "--workspace", "/tmp/ws", "--story", "story-1"
        });
        assertEquals("story-1", specification.storyId);
        Ai4seMain.KnowledgeCheckpointArgs checkpoint = Ai4seMain.KnowledgeCheckpointArgs.parse(new String[] {
            "--workspace", "/tmp/ws", "--candidate", "initial"
        });
        assertEquals("initial", checkpoint.candidateId);
        assertThrows(IllegalArgumentException.class, () -> Ai4seMain.BridgeArgs.parse(new String[] {
            "prepare-discovery", "--workspace", "/tmp/ws", "--scope", "repository"
        }));
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
    void resumeDefaultsToPinnedLedgerAdapterButLeavesExplicitChoiceForExactMatchGate()
            throws Exception {
        Path workspace = temp.resolve("resume-adapter");
        RunLedger ledger = RunLedger.open(workspace, "s1");
        ledger.beginRun("src/main/java", 3, "codex-cli", "cli-default");

        String[] implicitArgs = {"--workspace", workspace.toString(), "--story", "s1"};
        Ai4seMain.RunArgs implicit = Ai4seMain.RunArgs.parse(implicitArgs, false);
        assertEquals("codex", Ai4seMain.resolveResumeAdapterSelection(
                implicit, implicitArgs, ledger.readState()).adapter);

        String[] explicitArgs = {"--workspace", workspace.toString(), "--story", "s1", "--adapter", "cursor"};
        Ai4seMain.RunArgs explicit = Ai4seMain.RunArgs.parse(explicitArgs, false);
        assertEquals("cursor", Ai4seMain.resolveResumeAdapterSelection(
                explicit, explicitArgs, ledger.readState()).adapter);
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
