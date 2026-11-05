package com.ai4se.runtime.workers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

class CommandWorkerTest {

    @Test
    void toArgv_allowlistsEchoPwdGitStatus() {
        assertEquals(Arrays.asList("/bin/echo", "hi"), CommandWorker.toArgv("echo hi"));
        assertEquals(Arrays.asList("/bin/echo", "hello"), CommandWorker.toArgv("echo hello"));
        assertEquals(Arrays.asList("/bin/pwd"), CommandWorker.toArgv("pwd"));
        assertEquals(Arrays.asList("git", "status"), CommandWorker.toArgv("git status"));
    }

    @Test
    void toArgv_rejectsDisallowedCommands() {
        assertThrows(IllegalArgumentException.class, new Executable() {
            @Override
            public void execute() {
                CommandWorker.toArgv("curl http://example.com");
            }
        });
    }
}
