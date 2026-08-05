package com.ai4se.context.workspace;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

final class WorkspaceSlotVerifierTest {

    @TempDir
    Path temp;

    @Test
    void failsWhenNotOnboarded() {
        List<String> problems = WorkspaceSlotVerifier.problems(temp);
        assertTrue(problems.stream().anyMatch(p -> p.contains(".ai4se")));
        assertThrows(WorkspaceSlotException.class, () -> WorkspaceSlotVerifier.requireValid(temp));
    }

    @Test
    void acceptsUnknownEntries() throws Exception {
        writeSlots(temp, "build: unknown\ntest: unknown\n", "# facts only\n");
        assertTrue(WorkspaceSlotVerifier.problems(temp).isEmpty());
        WorkspaceSlotVerifier.requireValid(temp);
    }

    @Test
    void acceptsMavenCommands() throws Exception {
        writeSlots(temp, "build:\n  - mvn -q package\ntest:\n  - mvn -q test\n", "# ok\n");
        assertTrue(WorkspaceSlotVerifier.problems(temp).isEmpty());
    }

    @Test
    void rejectsEmptyArraysWithoutUnknown() throws Exception {
        writeSlots(temp, "build: []\ntest: []\n", "# ok\n");
        List<String> problems = WorkspaceSlotVerifier.problems(temp);
        assertTrue(problems.stream().anyMatch(p -> p.contains("unknown")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"应实现登录", "建议改 UserService", "推荐修改配置", "应该改接口"})
    void rejectsSuggestionLanguageInEntries(String banned) throws Exception {
        writeSlots(temp, "build: unknown\ntest: unknown\n# " + banned + "\n", "# ok\n");
        List<String> problems = WorkspaceSlotVerifier.problems(temp);
        assertFalse(problems.isEmpty());
        assertTrue(problems.stream().anyMatch(p -> p.contains("suggestion") || p.contains(banned.substring(0, 2))));
    }

    @Test
    void rejectsSuggestionInBaseline() throws Exception {
        writeSlots(temp, "build: unknown\ntest: unknown\n", "# 建议实现缓存\n");
        assertFalse(WorkspaceSlotVerifier.problems(temp).isEmpty());
    }

    @Test
    void rejectsOnboardFillPlaceholdersInBaseline() throws Exception {
        writeSlots(
                temp,
                "build: unknown\ntest: unknown\n",
                "# Repository Baseline\n\n## Modules\n- (fill)\n\n## Build entry\n- (fill)\n");
        List<String> problems = WorkspaceSlotVerifier.problems(temp);
        assertTrue(problems.stream().anyMatch(p -> p.contains("(fill)")));
        assertThrows(WorkspaceSlotException.class, () -> WorkspaceSlotVerifier.requireValid(temp));
    }

    @Test
    void acceptsFilledBaselineWithoutFillToken() throws Exception {
        writeSlots(
                temp,
                "build:\n  - mvn -q package\ntest:\n  - mvn -q test\n",
                "# Repository Baseline\n\n## Modules\n- demo-module\n\n## Build entry\n- mvn package\n");
        assertTrue(WorkspaceSlotVerifier.problems(temp).isEmpty());
    }

    private static void writeSlots(Path ws, String entries, String baseline) throws Exception {
        Files.createDirectories(ws.resolve(".ai4se/repository"));
        Files.createDirectories(ws.resolve(".ai4se/index"));
        Files.createDirectories(ws.resolve(".story"));
        Files.write(ws.resolve(".ai4se/repository/entries.yaml"), entries.getBytes(StandardCharsets.UTF_8));
        Files.write(ws.resolve(".ai4se/repository/baseline.md"), baseline.getBytes(StandardCharsets.UTF_8));
        Files.write(ws.resolve(".ai4se/index/knowledge.yaml"), "entries: []\n".getBytes(StandardCharsets.UTF_8));
    }
}
