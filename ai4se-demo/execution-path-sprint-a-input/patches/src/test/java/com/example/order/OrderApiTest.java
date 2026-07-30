package com.example.order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class OrderApiTest {

    @Test
    void createOrder_returnsOk() {
        assertTrue(new OrderApi().createOrder("{}").contains("ok"));
    }

    @Test
    void timeoutMs_readsFromApplicationProperties() {
        assertEquals(3000, new OrderApi().timeoutMs());
    }

    @Test
    void createOrder_embedsConfiguredTimeout() {
        assertTrue(new OrderApi().createOrder("{}").contains("\"timeoutMs\":3000"));
    }
}
