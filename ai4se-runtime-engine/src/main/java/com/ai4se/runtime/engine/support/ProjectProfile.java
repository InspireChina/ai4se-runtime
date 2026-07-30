package com.ai4se.runtime.engine.support;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * S9 — minimal Project Profile (YAML-ish text; not ProfileEngine / Plugin SPI).
 * New project = different profileId + verifyCommand; same Runtime.
 */
public final class ProjectProfile {

    public final String id;
    public final String projectId;
    public final String stack;
    public final String verifyCommand;
    public final String acceptanceDefault;

    public ProjectProfile(
            String id,
            String projectId,
            String stack,
            String verifyCommand,
            String acceptanceDefault) {
        this.id = id == null ? "" : id.trim();
        this.projectId = projectId == null ? "" : projectId.trim();
        this.stack = stack == null ? "" : stack.trim();
        this.verifyCommand = verifyCommand == null ? "" : verifyCommand.trim();
        this.acceptanceDefault = acceptanceDefault == null || acceptanceDefault.trim().isEmpty()
                ? "A1"
                : acceptanceDefault.trim();
    }

    /** Body for plan.test-strategy Artifact seeded from this Profile. */
    public String toTestStrategyBody() {
        return "command: " + verifyCommand + "\n"
                + "acceptance: " + acceptanceDefault + "\n"
                + "profile: " + id + "\n"
                + "stack: " + stack + "\n";
    }

    /**
     * Parse minimal profile text:
     * <pre>
     * id: java.config
     * projectId: stress-01
     * stack: java-maven
     * verifyCommand: mvn -f pom.xml -q test
     * acceptance: A1,A2
     * </pre>
     */
    public static ProjectProfile parse(String body) {
        Map<String, String> map = new LinkedHashMap<String, String>();
        if (body != null) {
            String[] lines = body.split("\\r?\\n");
            for (String raw : lines) {
                String line = raw.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                int colon = line.indexOf(':');
                if (colon <= 0) {
                    continue;
                }
                String key = line.substring(0, colon).trim().toLowerCase(Locale.ROOT);
                String value = line.substring(colon + 1).trim();
                map.put(key, value);
            }
        }
        String id = first(map, "id", "profileid");
        String projectId = first(map, "projectid", "project");
        String stack = first(map, "stack", "tech");
        String verify = first(map, "verifycommand", "verify", "testcommand");
        String acceptance = first(map, "acceptance", "acceptanceids");
        if (id.isEmpty()) {
            throw new IllegalArgumentException("ProjectProfile missing id");
        }
        if (verify.isEmpty()) {
            throw new IllegalArgumentException("ProjectProfile missing verifyCommand: " + id);
        }
        return new ProjectProfile(id, projectId, stack, verify, acceptance);
    }

    private static String first(Map<String, String> map, String... keys) {
        for (String k : keys) {
            if (map.containsKey(k)) {
                return map.get(k);
            }
        }
        return "";
    }
}
