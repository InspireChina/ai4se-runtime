package com.ai4se.context.rules;

import com.ai4se.runtime.common.util.Strings;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/** Customer / platform Rule document loaded for Context Package assembly. */
public final class RuleDocument {

    private final String id;
    private final Set<String> roles;
    private final boolean applicable;
    private final String body;
    private final PathRef source;

    public RuleDocument(String id, Set<String> roles, boolean applicable, String body, PathRef source) {
        this.id = Objects.requireNonNull(id, "id");
        this.roles = Collections.unmodifiableSet(new LinkedHashSet<String>(roles));
        this.applicable = applicable;
        this.body = body == null ? "" : body;
        this.source = source;
    }

    public String id() {
        return id;
    }

    public Set<String> roles() {
        return roles;
    }

    public boolean applicable() {
        return applicable;
    }

    public String body() {
        return body;
    }

    public PathRef source() {
        return source;
    }

    public int byteSize() {
        return body.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
    }

    public boolean appliesToRole(String role) {
        if (!applicable) {
            return false;
        }
        if (roles.isEmpty()) {
            return true;
        }
        if (Strings.isBlank(role)) {
            return false;
        }
        String r = role.trim().toLowerCase(Locale.ROOT);
        for (String allowed : roles) {
            if (allowed.toLowerCase(Locale.ROOT).equals(r)) {
                return true;
            }
            // Coding alias
            if ("development".equals(r) && "coding".equals(allowed.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    /** Lightweight path holder to avoid leaking nio into equals callers. */
    public static final class PathRef {
        public final String path;

        public PathRef(String path) {
            this.path = path == null ? "" : path;
        }
    }
}
