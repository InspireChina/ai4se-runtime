package com.ai4se.orchestration.queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class SerialStoryQueueTest {

    @TempDir
    Path temp;

    @Test
    void blockedSpecificationDoesNotBlockLaterFrozenStory() throws Exception {
        Files.createDirectories(temp.resolve(".ai4se"));
        Files.createDirectories(temp.resolve(".story/a/specification"));
        Files.createDirectories(temp.resolve(".story/b/specification"));
        Files.write(temp.resolve(".story/a/specification/specification.result.properties"),
                "decision=CLARIFICATION_REQUIRED\n".getBytes(StandardCharsets.UTF_8));
        Files.write(temp.resolve(".story/b/specification/frozen-inputs.properties"),
                "status=REQUIREMENT_FROZEN\n".getBytes(StandardCharsets.UTF_8));

        SerialStoryQueue.add(temp, "a");
        SerialStoryQueue.add(temp, "b");

        SerialStoryQueue.Entry next = SerialStoryQueue.next(temp);
        assertEquals("b", next.storyId);
        assertEquals(SerialStoryQueue.Status.READY_FOR_RUN, next.status);
        assertTrue(SerialStoryQueue.format(temp).contains("story.1=a status=WAITING_SPECIFICATION_ANSWER"));
    }
}
