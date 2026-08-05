package com.ai4se.context.story;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** S2 Story seed — anchors later stages; not the formal Analysis Spec. */
public final class StoryRequirement {

    private final String storyId;
    private final String raw;
    private final String goal;
    private final String inScope;
    private final String outOfScope;
    private final List<String> acceptance;

    public StoryRequirement(
            String storyId,
            String raw,
            String goal,
            String inScope,
            String outOfScope,
            List<String> acceptance) {
        this.storyId = Objects.requireNonNull(storyId, "storyId");
        this.raw = raw == null ? "" : raw.trim();
        this.goal = goal == null ? "" : goal.trim();
        this.inScope = inScope == null ? "" : inScope.trim();
        this.outOfScope = outOfScope == null ? "" : outOfScope.trim();
        this.acceptance = Collections.unmodifiableList(new ArrayList<String>(
                acceptance == null ? Collections.<String>emptyList() : acceptance));
    }

    public String storyId() {
        return storyId;
    }

    public String raw() {
        return raw;
    }

    public String goal() {
        return goal;
    }

    public String inScope() {
        return inScope;
    }

    public String outOfScope() {
        return outOfScope;
    }

    public List<String> acceptance() {
        return acceptance;
    }
}
