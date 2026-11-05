package com.ai4se.orchestration.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class AllowedPathSchemaTest {

    @Test
    void acceptsBareRelativePath() {
        assertEquals("src/main/Foo.java", AllowedPathSchema.requireBareRelativePath("src/main/Foo.java"));
        assertEquals("web/src/", AllowedPathSchema.requireBareRelativePath("web/src/"));
    }

    @Test
    void rejectsBackticks() {
        assertThrows(StageGateException.class, new org.junit.jupiter.api.function.Executable() {
            @Override
            public void execute() {
                AllowedPathSchema.requireBareRelativePath("`src/Foo.java`");
            }
        });
    }

    @Test
    void rejectsTrailingNotes() {
        assertThrows(StageGateException.class, new org.junit.jupiter.api.function.Executable() {
            @Override
            public void execute() {
                AllowedPathSchema.requireBareRelativePath("src/Foo.java (new)");
            }
        });
    }

    @Test
    void rejectsQuotes() {
        assertThrows(StageGateException.class, new org.junit.jupiter.api.function.Executable() {
            @Override
            public void execute() {
                AllowedPathSchema.requireBareRelativePath("\"src/Foo.java\"");
            }
        });
    }
}
