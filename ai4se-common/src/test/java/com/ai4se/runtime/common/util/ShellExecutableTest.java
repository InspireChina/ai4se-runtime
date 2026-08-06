package com.ai4se.runtime.common.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ShellExecutableTest {

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
}
