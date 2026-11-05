package com.ai4se.execution.support;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class StoryAdapterLeaseTest {

    @TempDir
    Path temp;

    @Test
    void rejectsASecondConcurrentSubmissionForTheSameStory() {
        try (StoryAdapterLease ignored = StoryAdapterLease.acquire(temp, "s1")) {
            IllegalStateException failure = assertThrows(IllegalStateException.class,
                    () -> StoryAdapterLease.acquire(temp, "s1"));
            assertTrue(failure.getMessage().contains("already active"));
        }
    }

    @Test
    void permitsTheLeaseAfterThePreviousSubmissionHasClosed() {
        try (StoryAdapterLease ignored = StoryAdapterLease.acquire(temp, "s1")) {
            // acquired and released
        }
        try (StoryAdapterLease ignored = StoryAdapterLease.acquire(temp, "s1")) {
            // A later controlled turn may acquire the lease.
        }
    }
}
