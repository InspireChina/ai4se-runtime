package com.ai4se.runtime.common.id;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class TaskIdTest {

    @Test
    void acceptsNonBlank() {
        assertEquals("task_1", new TaskId("task_1").value());
    }

    @Test
    void rejectsBlank() {
        assertThrows(IllegalArgumentException.class, new org.junit.jupiter.api.function.Executable() {
            @Override
            public void execute() {
                new TaskId(" ");
            }
        });
    }
}
