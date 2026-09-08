package com.ai4se.execution.support;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Process-wide, non-blocking lease for one model submission against one Story.
 *
 * <p>The control plane must never allow two terminal invocations to ask a model to write the
 * same Story artifacts concurrently.  In particular, a detached host shell must not turn a
 * harmless operator re-check into a second Specification/Planning/Development turn.  The lock
 * is deliberately non-blocking: callers receive a safe failure and can inspect the active run
 * instead of queueing an unbounded second agent.</p>
 */
public final class StoryAdapterLease implements AutoCloseable {

    private final FileChannel channel;
    private final FileLock lock;

    private StoryAdapterLease(FileChannel channel, FileLock lock) {
        this.channel = channel;
        this.lock = lock;
    }

    public static StoryAdapterLease acquire(Path workspace, String storyId) {
        if (workspace == null || storyId == null || storyId.trim().isEmpty()) {
            throw new IllegalArgumentException("workspace and storyId required for adapter lease");
        }
        Path lockPath = workspace.resolve(".story").resolve(storyId)
                .resolve("execution").resolve(".adapter-invocation.lock");
        FileChannel channel = null;
        try {
            Files.createDirectories(lockPath.getParent());
            channel = FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            FileLock lock = channel.tryLock();
            if (lock == null) {
                closeQuietly(channel);
                throw active(storyId);
            }
            return new StoryAdapterLease(channel, lock);
        } catch (OverlappingFileLockException e) {
            closeQuietly(channel);
            throw active(storyId);
        } catch (IOException e) {
            closeQuietly(channel);
            throw new IllegalStateException("Cannot acquire adapter lease for Story " + storyId, e);
        }
    }

    @Override
    public void close() {
        try {
            lock.release();
        } catch (IOException ignored) {
            // The channel close below is still sufficient to release an OS advisory lock.
        }
        closeQuietly(channel);
    }

    private static IllegalStateException active(String storyId) {
        return new IllegalStateException(
                "Another Adapter invocation is already active for Story " + storyId
                        + "; inspect its evidence and do not start a concurrent turn");
    }

    private static void closeQuietly(FileChannel channel) {
        if (channel == null) {
            return;
        }
        try {
            channel.close();
        } catch (IOException ignored) {
            // Best-effort cleanup for a failed acquisition.
        }
    }
}
