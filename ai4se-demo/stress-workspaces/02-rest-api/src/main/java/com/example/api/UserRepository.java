package com.example.api;

import java.util.HashMap;
import java.util.Map;

/** In-memory user lookup used by the HTTP layer. */
public final class UserRepository {

    private final Map<String, String> users = new HashMap<String, String>();

    public UserRepository() {
        users.put("u1", "Ada");
        users.put("u2", "Grace");
    }

    public String findName(String id) {
        return users.get(id);
    }
}
