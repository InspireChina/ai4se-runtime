package com.ai4se.execution.model;

import com.ai4se.runtime.common.util.Strings;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Per-role model selection for Model CLI Adapters.
 *
 * <p>Problem class: one global CLI default cannot express “Analysis=sonnet / Dev=deepseek /
 * Review=acceptance-model”. Control selects; Adapter only passes {@code --model} when present.
 *
 * <p>Precedence when resolving for a role: explicit {@link #ENV_MODEL} on the request → role map →
 * {@link #defaultModel()} → blank (CLI vendor default).
 *
 * <p>Customer file: {@code .ai4se/runtime/role-models.yaml}
 */
public final class RoleModelConfig {

    public static final String ENV_MODEL = "AI4SE_MODEL";
    public static final String ENV_PREFIX = "AI4SE_MODEL_";
    public static final String RELATIVE_FILE = ".ai4se/runtime/role-models.yaml";

    private final String defaultModel;
    private final Map<String, String> byRole;

    public RoleModelConfig(String defaultModel, Map<String, String> byRole) {
        this.defaultModel = Strings.isBlank(defaultModel) ? null : defaultModel.trim();
        Map<String, String> map = new LinkedHashMap<String, String>();
        if (byRole != null) {
            for (Map.Entry<String, String> e : byRole.entrySet()) {
                if (Strings.isBlank(e.getKey()) || Strings.isBlank(e.getValue())) {
                    continue;
                }
                map.put(normalizeRole(e.getKey()), e.getValue().trim());
            }
        }
        this.byRole = Collections.unmodifiableMap(map);
    }

    public static RoleModelConfig empty() {
        return new RoleModelConfig(null, Collections.<String, String>emptyMap());
    }

    public String defaultModel() {
        return defaultModel;
    }

    public Map<String, String> byRole() {
        return byRole;
    }

    public boolean isEmpty() {
        return defaultModel == null && byRole.isEmpty();
    }

    /**
     * Resolve model id for a package role (Analysis / Planning / Development / Review / …).
     */
    public String resolve(String role) {
        String key = normalizeRole(role);
        String specific = byRole.get(key);
        if (!Strings.isBlank(specific)) {
            return specific;
        }
        // Acceptance agent often aliases Review when no dedicated stage adapter exists.
        if ("acceptance".equals(key)) {
            String review = byRole.get("review");
            if (!Strings.isBlank(review)) {
                return review;
            }
        }
        // Conversely: Review may use acceptance model when review key unset.
        if ("review".equals(key)) {
            String acceptance = byRole.get("acceptance");
            if (!Strings.isBlank(acceptance)) {
                return acceptance;
            }
        }
        return defaultModel;
    }

    /** Overlay wins on conflict (CLI flags over file, etc.). */
    public RoleModelConfig mergeOverlay(RoleModelConfig overlay) {
        if (overlay == null || overlay.isEmpty()) {
            return this;
        }
        if (isEmpty()) {
            return overlay;
        }
        Map<String, String> merged = new LinkedHashMap<String, String>(byRole);
        merged.putAll(overlay.byRole);
        String def = !Strings.isBlank(overlay.defaultModel) ? overlay.defaultModel : defaultModel;
        return new RoleModelConfig(def, merged);
    }

    public static RoleModelConfig loadFromWorkspace(Path workspace) throws IOException {
        if (workspace == null) {
            return empty();
        }
        Path file = workspace.resolve(RELATIVE_FILE);
        if (!Files.isRegularFile(file)) {
            return empty();
        }
        return parseYamlLite(new String(Files.readAllBytes(file), StandardCharsets.UTF_8));
    }

    /**
     * Env map: {@code AI4SE_MODEL} (default) + {@code AI4SE_MODEL_ANALYSIS} etc.
     */
    public static RoleModelConfig fromProcessEnv() {
        Map<String, String> roles = new LinkedHashMap<String, String>();
        putEnvRole(roles, "analysis", System.getenv(ENV_PREFIX + "ANALYSIS"));
        putEnvRole(roles, "planning", System.getenv(ENV_PREFIX + "PLANNING"));
        putEnvRole(roles, "development", System.getenv(ENV_PREFIX + "DEVELOPMENT"));
        putEnvRole(roles, "dev", System.getenv(ENV_PREFIX + "DEV"));
        putEnvRole(roles, "review", System.getenv(ENV_PREFIX + "REVIEW"));
        putEnvRole(roles, "acceptance", System.getenv(ENV_PREFIX + "ACCEPTANCE"));
        // DEV aliases development if development empty
        if (!roles.containsKey("development") && roles.containsKey("dev")) {
            roles.put("development", roles.get("dev"));
        }
        String def = System.getenv(ENV_MODEL);
        return new RoleModelConfig(def, roles);
    }

    private static void putEnvRole(Map<String, String> roles, String role, String value) {
        if (!Strings.isBlank(value)) {
            roles.put(normalizeRole(role), value.trim());
        }
    }

    /**
     * Minimal YAML: {@code default:} + {@code roles:} map (or flat {@code analysis:} keys).
     */
    static RoleModelConfig parseYamlLite(String text) {
        if (Strings.isBlank(text)) {
            return empty();
        }
        String defaultModel = null;
        Map<String, String> roles = new LinkedHashMap<String, String>();
        boolean inRoles = false;
        for (String raw : text.split("\n")) {
            String line = raw.replace("\t", "    ");
            String t = line.trim();
            if (t.isEmpty() || t.startsWith("#")) {
                continue;
            }
            if (t.startsWith("roles:") || t.equals("roles:")) {
                inRoles = true;
                continue;
            }
            if (!line.startsWith(" ") && !line.startsWith("\t") && t.contains(":") && !t.startsWith("-")) {
                // top-level key
                inRoles = false;
            }
            int colon = t.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            String key = t.substring(0, colon).trim();
            String value = t.substring(colon + 1).trim();
            if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) {
                value = value.substring(1, value.length() - 1);
            }
            if ("default".equals(key) || "default_model".equals(key)) {
                if (!Strings.isBlank(value)) {
                    defaultModel = value;
                }
                continue;
            }
            if ("roles".equals(key)) {
                inRoles = true;
                continue;
            }
            if (inRoles || isKnownRole(key)) {
                if (!Strings.isBlank(value)) {
                    roles.put(normalizeRole(key), value);
                }
            }
        }
        return new RoleModelConfig(defaultModel, roles);
    }

    public static String normalizeRole(String role) {
        if (Strings.isBlank(role)) {
            return "";
        }
        String r = role.trim().toLowerCase(Locale.ROOT);
        if ("dev".equals(r)) {
            return "development";
        }
        return r;
    }

    private static boolean isKnownRole(String key) {
        String k = normalizeRole(key);
        return "analysis".equals(k)
                || "planning".equals(k)
                || "development".equals(k)
                || "review".equals(k)
                || "acceptance".equals(k)
                || "verification".equals(k);
    }

    /** Put resolved model into Adapter env if absent. */
    public static void putResolvedModel(Map<String, String> env, String role, RoleModelConfig config) {
        if (env == null || config == null || config.isEmpty()) {
            return;
        }
        if (!Strings.isBlank(env.get(ENV_MODEL))) {
            return;
        }
        String model = config.resolve(role);
        if (!Strings.isBlank(model)) {
            env.put(ENV_MODEL, model);
        }
    }

    public static final class Builder {
        private String defaultModel;
        private final Map<String, String> byRole = new LinkedHashMap<String, String>();

        public Builder defaultModel(String model) {
            this.defaultModel = model;
            return this;
        }

        public Builder role(String role, String model) {
            if (!Strings.isBlank(role) && !Strings.isBlank(model)) {
                byRole.put(normalizeRole(role), model.trim());
            }
            return this;
        }

        public RoleModelConfig build() {
            return new RoleModelConfig(defaultModel, byRole);
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}
