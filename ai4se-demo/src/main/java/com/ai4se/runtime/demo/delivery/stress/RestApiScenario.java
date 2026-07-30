package com.ai4se.runtime.demo.delivery.stress;

import com.ai4se.runtime.demo.delivery.DeliveryScenario;
import java.util.LinkedHashMap;
import java.util.Map;

/** Type: 新增 REST API — add UserApi GET /api/users/{id}. */
public final class RestApiScenario implements DeliveryScenario {

    @Override
    public String id() {
        return "02-rest-api";
    }

    @Override
    public String typeLabel() {
        return "新增 REST API";
    }

    @Override
    public String requirement() {
        return "Add UserApi handling GET /api/users/{id}: 200 JSON for known ids, "
                + "404 JSON error for unknown. Document endpoint in README.";
    }

    @Override
    public String projectId() {
        return "stress-02-rest-api";
    }

    @Override
    public String fixtureDirName() {
        return "stress-workspaces/02-rest-api";
    }

    @Override
    public String discoveryCommand() {
        return "pwd";
    }

    @Override
    public String verifyCommand() {
        return "mvn -f pom.xml -q test";
    }

    @Override
    public Map<String, String> planFiles() {
        Map<String, String> files = new LinkedHashMap<String, String>();
        files.put("PLAN.md", ""
                + "# Plan\n\n"
                + "1. Add UserApi with route parsing.\n"
                + "2. Wire UserRepository.\n"
                + "3. Document GET /api/users/{id}.\n"
                + "4. mvn test.\n");
        return files;
    }

    @Override
    public Map<String, String> executionFiles() {
        Map<String, String> files = new LinkedHashMap<String, String>();
        files.put("src/main/java/com/example/api/UserApi.java", ""
                + "package com.example.api;\n\n"
                + "/** Minimal REST-style handler (no framework). */\n"
                + "public final class UserApi {\n\n"
                + "    private final UserRepository repository;\n"
                + "    private int lastStatus = 500;\n\n"
                + "    public UserApi(UserRepository repository) {\n"
                + "        this.repository = repository;\n"
                + "    }\n\n"
                + "    public int lastStatus() {\n"
                + "        return lastStatus;\n"
                + "    }\n\n"
                + "    public String handle(String method, String path) {\n"
                + "        if (!\"GET\".equals(method) || path == null || !path.startsWith(\"/api/users/\")) {\n"
                + "            lastStatus = 404;\n"
                + "            return \"{\\\"error\\\":\\\"not_found\\\"}\";\n"
                + "        }\n"
                + "        String id = path.substring(\"/api/users/\".length());\n"
                + "        String name = repository.findName(id);\n"
                + "        if (name == null) {\n"
                + "            lastStatus = 404;\n"
                + "            return \"{\\\"error\\\":\\\"user_not_found\\\"}\";\n"
                + "        }\n"
                + "        lastStatus = 200;\n"
                + "        return \"{\\\"id\\\":\\\"\" + id + \"\\\",\\\"name\\\":\\\"\" + name + \"\\\"}\";\n"
                + "    }\n"
                + "}\n");
        files.put("README.md", ""
                + "# REST API stress sample\n\n"
                + "## Endpoint\n\n"
                + "`GET /api/users/{id}` → `200 {\"id\",\"name\"}` or `404 {\"error\"}`.\n");
        return files;
    }
}
