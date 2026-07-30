package com.example.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class UserApiTest {

    @Test
    void getUser_returnsJsonForKnownId() {
        UserApi api = new UserApi(new UserRepository());
        String body = api.handle("GET", "/api/users/u1");
        assertTrue(body.contains("\"id\":\"u1\""));
        assertTrue(body.contains("\"name\":\"Ada\""));
        assertEquals(200, api.lastStatus());
    }

    @Test
    void getUser_returns404ForUnknownId() {
        UserApi api = new UserApi(new UserRepository());
        String body = api.handle("GET", "/api/users/missing");
        assertEquals(404, api.lastStatus());
        assertTrue(body.contains("\"error\""));
    }
}
