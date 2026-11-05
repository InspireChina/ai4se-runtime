package com.example.order;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class OrderApiTest {
    @Test
    void createOrder_returnsOk() {
        assertTrue(new OrderApi().createOrder("{}").contains("ok"));
    }
}
