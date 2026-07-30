package com.example.api;

/** Minimal REST-style handler (no framework). */
public final class UserApi {

    private final UserRepository repository;
    private int lastStatus = 500;

    public UserApi(UserRepository repository) {
        this.repository = repository;
    }

    public int lastStatus() {
        return lastStatus;
    }

    public String handle(String method, String path) {
        if (!"GET".equals(method) || path == null || !path.startsWith("/api/users/")) {
            lastStatus = 404;
            return "{\"error\":\"not_found\"}";
        }
        String id = path.substring("/api/users/".length());
        String name = repository.findName(id);
        if (name == null) {
            lastStatus = 404;
            return "{\"error\":\"user_not_found\"}";
        }
        lastStatus = 200;
        return "{\"id\":\"" + id + "\",\"name\":\"" + name + "\"}";
    }
}
