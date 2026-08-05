package com.example.delivery;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ConfigServiceTest {

    @Test
    void timeoutMs_readsAppTimeoutFromProperties() throws Exception {
        ConfigService service = ConfigService.loadFromClasspath();
        assertEquals(5000, service.timeoutMs());
    }
}
