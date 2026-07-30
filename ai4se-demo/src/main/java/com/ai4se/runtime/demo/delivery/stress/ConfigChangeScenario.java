package com.ai4se.runtime.demo.delivery.stress;

import com.ai4se.runtime.demo.delivery.DeliveryScenario;
import java.util.LinkedHashMap;
import java.util.Map;

/** Type: 修改配置 — FeatureFlags must honor app.feature.enabled. */
public final class ConfigChangeScenario implements DeliveryScenario {

    @Override
    public String id() {
        return "01-config";
    }

    @Override
    public String typeLabel() {
        return "修改配置";
    }

    @Override
    public String requirement() {
        return "Make FeatureFlags.isFeatureEnabled() read app.feature.enabled "
                + "from application.properties (false in fixture). Update README config contract.";
    }

    @Override
    public String projectId() {
        return "stress-01-config";
    }

    @Override
    public String fixtureDirName() {
        return "stress-workspaces/01-config";
    }

    @Override
    public String discoveryCommand() {
        return "git status";
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
                + "1. Fix FeatureFlags to parse app.feature.enabled.\n"
                + "2. Document the property in README.\n"
                + "3. Verify with mvn test.\n");
        return files;
    }

    @Override
    public Map<String, String> executionFiles() {
        Map<String, String> files = new LinkedHashMap<String, String>();
        files.put("src/main/java/com/example/config/FeatureFlags.java", ""
                + "package com.example.config;\n\n"
                + "import java.io.IOException;\n"
                + "import java.io.InputStream;\n"
                + "import java.util.Properties;\n\n"
                + "public final class FeatureFlags {\n\n"
                + "    private final Properties properties;\n\n"
                + "    public FeatureFlags(Properties properties) {\n"
                + "        this.properties = properties;\n"
                + "    }\n\n"
                + "    public static FeatureFlags load() throws IOException {\n"
                + "        Properties properties = new Properties();\n"
                + "        InputStream in = FeatureFlags.class.getResourceAsStream(\"/application.properties\");\n"
                + "        if (in != null) {\n"
                + "            try {\n"
                + "                properties.load(in);\n"
                + "            } finally {\n"
                + "                in.close();\n"
                + "            }\n"
                + "        }\n"
                + "        return new FeatureFlags(properties);\n"
                + "    }\n\n"
                + "    public boolean isFeatureEnabled() {\n"
                + "        return Boolean.parseBoolean(properties.getProperty(\"app.feature.enabled\", \"false\"));\n"
                + "    }\n"
                + "}\n");
        files.put("README.md", ""
                + "# Config stress sample\n\n"
                + "| Key | Meaning |\n"
                + "|-----|---------|\n"
                + "| `app.feature.enabled` | Feature toggle (boolean) |\n"
                + "| `app.name` | Service name |\n");
        return files;
    }
}
