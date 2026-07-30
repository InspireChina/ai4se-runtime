package com.ai4se.runtime.demo.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class StopConditionTest {

    @Test
    void twoConsecutiveBlocked_stops_round1DoesNot() {
        StopCondition.Result r1 = StopCondition.evaluate(1, true, false, false);
        assertFalse(r1.isStop());
        StopCondition.Result r2 = StopCondition.evaluate(2, true, true, false);
        assertTrue(r2.isStop());
        assertEquals(StopCondition.Reason.TWO_CONSECUTIVE_BLOCKED, r2.reason);
    }

    @Test
    void exceedThreeRounds_andRefuse() {
        assertTrue(StopCondition.evaluate(4, true, true, false).isStop());
        assertEquals(StopCondition.Reason.EXCEEDED_THREE_ROUNDS,
                StopCondition.evaluate(4, true, true, false).reason);
        assertTrue(StopCondition.isRefuseAnswer("拒绝回答"));
        assertTrue(StopCondition.evaluate(1, true, false, true).isStop());
    }
}
