package com.ai4se.execution.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Locks the public capability: role write surface (one place) + vendor mapping catalog (one place).
 * New CLI families must extend {@link CliVendor} + {@link UnattendedPermissionPolicy}, not copy
 * per-adapter if-trees.
 */
final class UnattendedPermissionPolicyTest {

    @Test
    void roleMatrixMatchesContractWriteSurface() {
        assertEquals(UnattendedWriteScope.STORY_ARTIFACT, UnattendedWriteScope.forRole("Analysis"));
        assertEquals(UnattendedWriteScope.STORY_ARTIFACT, UnattendedWriteScope.forRole("Planning"));
        assertEquals(UnattendedWriteScope.STORY_ARTIFACT, UnattendedWriteScope.forRole("Review"));
        assertEquals(UnattendedWriteScope.BUSINESS_SOURCE, UnattendedWriteScope.forRole("Development"));
        assertEquals(UnattendedWriteScope.NONE, UnattendedWriteScope.forRole("Acceptance"));
        assertEquals(UnattendedWriteScope.NONE, UnattendedWriteScope.forRole("unknown"));
    }

    @Test
    void everyRegisteredVendorMapsBothWriteScopes() {
        for (CliVendor vendor : CliVendor.values()) {
            for (UnattendedWriteScope scope : new UnattendedWriteScope[] {
                UnattendedWriteScope.STORY_ARTIFACT, UnattendedWriteScope.BUSINESS_SOURCE
            }) {
                List<String> argv = new ArrayList<String>();
                UnattendedPermissionPolicy.appendFlags(vendor, scope, argv);
                assertFalse(argv.isEmpty(), vendor + "/" + scope + " must emit flags");
            }
        }
    }

    @Test
    void claudeMatchesDocumentedThreeTier() {
        List<String> story = new ArrayList<String>();
        assertEquals(
                UnattendedWriteScope.STORY_ARTIFACT,
                UnattendedPermissionPolicy.apply(CliVendor.CLAUDE, "Analysis", story));
        assertTrue(story.contains("--permission-mode"));
        assertTrue(story.contains("acceptEdits"));
        assertFalse(story.contains("--dangerously-skip-permissions"));

        List<String> biz = new ArrayList<String>();
        UnattendedPermissionPolicy.apply(CliVendor.CLAUDE, "Development", biz);
        assertTrue(biz.contains("--dangerously-skip-permissions"));
        assertFalse(biz.contains("--permission-mode"));
    }

    @Test
    void cursorMapsStoryAndBusinessWithoutPerRoleTreesInAdapters() {
        List<String> story = new ArrayList<String>();
        UnattendedPermissionPolicy.apply(CliVendor.CURSOR, "Planning", story);
        assertTrue(story.contains("--trust"));
        assertTrue(story.contains("--auto-review"));
        assertTrue(story.contains("--approve-mcps"));
        assertFalse(story.contains("--force"));

        List<String> biz = new ArrayList<String>();
        UnattendedPermissionPolicy.apply(CliVendor.CURSOR, "Dev", biz);
        assertTrue(biz.contains("--force"));
        assertTrue(biz.contains("--approve-mcps"));
    }

    @Test
    void codexMapsBothWriteScopesWithoutDangerousBypass() {
        for (String role : new String[] {"Analysis", "Planning", "Development", "Review"}) {
            List<String> argv = new ArrayList<String>();
            UnattendedPermissionPolicy.apply(CliVendor.CODEX, role, argv);
            assertTrue(argv.contains("--sandbox"));
            assertTrue(argv.contains("workspace-write"));
            assertTrue(argv.contains("--approve-for-me"));
            assertFalse(argv.contains("--dangerously-bypass-approvals-and-sandbox"));
        }
    }

    @Test
    void productionCliAdaptersMustCallSharedPolicy() throws Exception {
        Path mainJava = Paths.get("ai4se-execution/src/main/java/com/ai4se/execution").toAbsolutePath();
        if (!Files.isDirectory(mainJava)) {
            mainJava = Paths.get("src/main/java/com/ai4se/execution").toAbsolutePath();
        }
        assertTrue(Files.isDirectory(mainJava), "execution main sources: " + mainJava);
        List<Path> adapters = new ArrayList<Path>();
        collectCliAdapters(mainJava, adapters);
        assertTrue(adapters.size() >= 2, "expected Claude+Cursor adapters, got " + adapters);
        for (int i = 0; i < adapters.size(); i++) {
            Path adapter = adapters.get(i);
            String src = new String(Files.readAllBytes(adapter), StandardCharsets.UTF_8);
            assertTrue(
                    src.contains("UnattendedPermissionPolicy.apply"),
                    adapter.getFileName() + " must call UnattendedPermissionPolicy.apply");
            assertFalse(
                    src.contains("isWriteRole(request.role())"),
                    adapter.getFileName() + " must not keep private isWriteRole permission trees");
        }
    }

    @Test
    void applyRejectsNullVendor() {
        assertThrows(
                IllegalArgumentException.class,
                () -> UnattendedPermissionPolicy.apply(null, "Analysis", new ArrayList<String>()));
    }

    private static void collectCliAdapters(Path dir, List<Path> out) throws Exception {
        if (!Files.isDirectory(dir)) {
            return;
        }
        DirectoryStream<Path> stream = Files.newDirectoryStream(dir);
        try {
            for (Path p : stream) {
                if (Files.isDirectory(p)) {
                    collectCliAdapters(p, out);
                } else {
                    String name = p.getFileName().toString();
                    if (name.endsWith("CliAdapter.java")
                            && !"ModelCliAdapter.java".equals(name)
                            && name.indexOf("Functional") < 0) {
                        out.add(p);
                    }
                }
            }
        } finally {
            stream.close();
        }
    }
}
