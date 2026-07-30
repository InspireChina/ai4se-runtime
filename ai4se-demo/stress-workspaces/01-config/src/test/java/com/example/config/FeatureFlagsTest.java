package com.example.config;

import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

class FeatureFlagsTest {

    @Test
    void isFeatureEnabled_readsApplicationProperties() throws Exception {
        FeatureFlags flags = FeatureFlags.load();
        assertFalse(flags.isFeatureEnabled());
    }
}
