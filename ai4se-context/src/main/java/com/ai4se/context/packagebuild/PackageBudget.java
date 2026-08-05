package com.ai4se.context.packagebuild;

/**
 * Package byte budget for Context Builder.
 * Unlimited when {@code maxBytes < 0}.
 */
public final class PackageBudget {

    public static final PackageBudget UNLIMITED = new PackageBudget(-1L);

    private final long maxBytes;

    private PackageBudget(long maxBytes) {
        this.maxBytes = maxBytes;
    }

    public static PackageBudget ofBytes(long maxBytes) {
        if (maxBytes < 0) {
            return UNLIMITED;
        }
        return new PackageBudget(maxBytes);
    }

    public boolean isLimited() {
        return maxBytes >= 0;
    }

    public long maxBytes() {
        return maxBytes;
    }
}
