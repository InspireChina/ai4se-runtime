package com.example.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class OrderFacadeTest {

    @Test
    void quoteTotalCents_appliesTenPercentTax() {
        OrderFacade facade = new OrderFacade();
        assertEquals(1100, facade.quoteTotalCents(1000));
        assertEquals("tax=10%", facade.taxPolicy());
    }
}
