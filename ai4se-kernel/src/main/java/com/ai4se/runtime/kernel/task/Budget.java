package com.ai4se.runtime.kernel.task;

import java.time.Duration;
import java.util.Objects;

public final class Budget {

    private final int maxSteps;
    private final int maxIterations;
    private final long maxTokens;
    private final Duration maxWallClock;
    private final long maxCostUnits;

    public Budget(
            int maxSteps,
            int maxIterations,
            long maxTokens,
            Duration maxWallClock,
            long maxCostUnits) {
        this.maxWallClock = Objects.requireNonNull(maxWallClock, "maxWallClock");
        if (maxSteps < 0 || maxIterations < 0 || maxTokens < 0 || maxCostUnits < 0) {
            throw new IllegalArgumentException("budget fields must be >= 0");
        }
        this.maxSteps = maxSteps;
        this.maxIterations = maxIterations;
        this.maxTokens = maxTokens;
        this.maxCostUnits = maxCostUnits;
    }

    public int getMaxSteps() { return maxSteps; }
    public int getMaxIterations() { return maxIterations; }
    public long getMaxTokens() { return maxTokens; }
    public Duration getMaxWallClock() { return maxWallClock; }
    public long getMaxCostUnits() { return maxCostUnits; }
}
