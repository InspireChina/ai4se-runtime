package com.ai4se.context.packagebuild;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ai4se.context.story.StoryRequirement;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

final class AcceptanceGateTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "看着办",
            "先看着办再说",
            "随意",
            "TBD",
            "todo later",
            "TODO",
            "（可检验的通过条件）",
            "(placeholder)",
            "可检验的通过条件",
            "可检验的通过条件；可测或可人工勾选"
    })
    void rejectsPlaceholders(String item) {
        StoryRequirement req = req(Collections.singletonList(item));
        assertFalse(AcceptanceGate.validate(req).isEmpty(), "should reject: " + item);
        assertTrue(AcceptanceGate.isPlaceholder(item));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "GET /health returns 200",
            "FeatureFlags.isEnabled returns false when disabled",
            "单元测试通过且覆盖主路径",
            "日志出现 started"
    })
    void acceptsConcreteItems(String item) {
        assertTrue(AcceptanceGate.validate(req(Collections.singletonList(item))).isEmpty());
        assertFalse(AcceptanceGate.isPlaceholder(item));
    }

    @Test
    void emptyListFails() {
        assertFalse(AcceptanceGate.validate(req(Collections.<String>emptyList())).isEmpty());
    }

    @Test
    void mixedUsableAndPlaceholderStillPassesIfAnyUsable() {
        List<String> items = Arrays.asList("看着办", "GET /health returns 200");
        assertTrue(AcceptanceGate.validate(req(items)).isEmpty(),
                "at least one usable AC is enough; placeholders are noted but usable count > 0");
    }

    @Test
    void allPlaceholdersFailsEvenIfNonEmpty() {
        List<String> items = Arrays.asList("看着办", "TBD", "todo");
        List<String> problems = AcceptanceGate.validate(req(items));
        assertFalse(problems.isEmpty());
        assertTrue(problems.stream().anyMatch(p -> p.contains("no usable")));
    }

    private static StoryRequirement req(List<String> acceptance) {
        return new StoryRequirement("s", "raw", "goal", "in", "out", acceptance);
    }
}
