package com.example.order;

/** Order HTTP-style handler. Timeout is not configurable yet. */
public final class OrderApi {

    public String createOrder(String payload) {
        return "{\"status\":\"ok\",\"id\":\"o1\"}";
    }
}
