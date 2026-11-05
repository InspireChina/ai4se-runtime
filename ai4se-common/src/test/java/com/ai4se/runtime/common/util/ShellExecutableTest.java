package com.ai4se.runtime.common.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ShellExecutableTest {

    @TempDir
    Path temp;

    @Test
    void nonWindowsResolveIsBash() {
        if (ShellExecutable.isWindows()) {
            return;
        }
        assertEquals("bash", ShellExecutable.resolve());
        ShellExecutable.requireUsable();
    }

    @Test
    void detectsSystem32StubPath() {
        assertTrue(ShellExecutable.isWslStub("C:\\Windows\\System32\\bash.exe")
                || !ShellExecutable.isWindows());
        // Path shape check is OS-agnostic via private helper through public API on Windows only;
        // on non-Windows isWslStub returns false for any path.
        if (!ShellExecutable.isWindows()) {
            assertFalse(ShellExecutable.isWslStub("C:\\Windows\\System32\\bash.exe"));
        }
    }

    @Test
    void barePathNameNeverWrapped() {
        assertFalse(ShellExecutable.needsShellWrapper("agent"));
        assertFalse(ShellExecutable.needsShellWrapper("claude"));
        List<String> argv = ShellExecutable.launchArgv("agent", Arrays.asList("-p", "text"));
        assertEquals("agent", argv.get(0));
        assertEquals("-p", argv.get(1));
    }

    @Test
    void resolveCommandFallsBackToLeafWhenAbsent() {
        assertEquals("definitely-not-on-path-xyz",
                ShellExecutable.resolveCommand("definitely-not-on-path-xyz"));
        if (ShellExecutable.isWindows()) {
            assertTrue(ShellExecutable.resolveCommand("cmd").toLowerCase().contains("cmd"));
        }
    }

    @Test
    void shebangScriptWrappedOnlyOnWindows() throws Exception {
        Path stub = temp.resolve("fake-agent");
        Files.write(stub, ("#!/usr/bin/env bash\necho ok\n").getBytes(StandardCharsets.UTF_8));
        String path = stub.toAbsolutePath().toString();
        if (ShellExecutable.isWindows()) {
            assertTrue(ShellExecutable.needsShellWrapper(path));
            List<String> argv = ShellExecutable.launchArgv(path, Collections.singletonList("-p"));
            assertEquals(3, argv.size());
            assertEquals(path, argv.get(1));
            assertEquals("-p", argv.get(2));
            // argv[0] is resolved bash (absolute or "bash")
            assertTrue(argv.get(0).toLowerCase().contains("bash"), argv.toString());
        } else {
            assertFalse(ShellExecutable.needsShellWrapper(path));
            List<String> argv = ShellExecutable.launchArgv(path, Collections.singletonList("-p"));
            assertEquals(path, argv.get(0));
            assertEquals("-p", argv.get(1));
        }
    }
}
