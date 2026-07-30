package com.ai4se.runtime.demo.delivery;

import org.junit.jupiter.api.Test;

/** Wraps {@link AltApproachPressureMain} — alternate pressure, not serial DoD. */
class AltApproachPressureTest {

    @Test
    void cliBatch_blindAndEngineAdversarial_notSerialPath() throws Exception {
        AltApproachPressureMain.main(new String[0]);
    }
}
