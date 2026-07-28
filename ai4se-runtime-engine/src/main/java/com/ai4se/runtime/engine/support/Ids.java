package com.ai4se.runtime.engine.support;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/** Simple id factory for walking skeleton (not a Kernel object). */
public final class Ids {

    private static final AtomicLong SEQ = new AtomicLong(1);

    private Ids() {
    }

    public static String next(String prefix) {
        return prefix + "_" + SEQ.getAndIncrement() + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }
}
