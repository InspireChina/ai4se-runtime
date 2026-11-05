package com.ai4se.runtime.engine.support;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Append-only journal for Demo / tests (not a Kernel object). */
public final class LifecycleJournal {

    private final List<String> events = new ArrayList<String>();

    public synchronized void record(String event) {
        events.add(event);
    }

    public synchronized List<String> events() {
        return Collections.unmodifiableList(new ArrayList<String>(events));
    }
}
