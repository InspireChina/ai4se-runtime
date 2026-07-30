package com.ai4se.runtime.demo.delivery;

import java.util.LinkedHashMap;
import java.util.Map;

/** Patches applied by FileEditWorker during First Production Delivery Execution. */
final class DeliveryPatches {

    private DeliveryPatches() {
    }

    static Map<String, String> planFiles() {
        Map<String, String> files = new LinkedHashMap<String, String>();
        files.put("PLAN.md", ""
                + "# Plan\n\n"
                + "1. Add TimeoutSource interface.\n"
                + "2. Fix ConfigService.timeoutMs() to read app.timeout.ms.\n"
                + "3. Update README with config contract.\n"
                + "4. Verify with mvn -f pom.xml -q test.\n");
        return files;
    }

    static Map<String, String> executionFiles() {
        Map<String, String> files = new LinkedHashMap<String, String>();
        files.put("src/main/java/com/example/delivery/TimeoutSource.java", ""
                + "package com.example.delivery;\n\n"
                + "/** Provides configured timeout in milliseconds. */\n"
                + "public interface TimeoutSource {\n"
                + "    int timeoutMs();\n"
                + "}\n");
        files.put("src/main/java/com/example/delivery/ConfigService.java", ""
                + "package com.example.delivery;\n\n"
                + "import java.io.IOException;\n"
                + "import java.io.InputStream;\n"
                + "import java.util.Properties;\n\n"
                + "/** Loads application.properties and exposes configured timeout. */\n"
                + "public final class ConfigService implements TimeoutSource {\n\n"
                + "    private final Properties properties;\n\n"
                + "    public ConfigService(Properties properties) {\n"
                + "        this.properties = properties;\n"
                + "    }\n\n"
                + "    public static ConfigService loadFromClasspath() throws IOException {\n"
                + "        Properties properties = new Properties();\n"
                + "        InputStream in = ConfigService.class.getResourceAsStream(\"/application.properties\");\n"
                + "        if (in != null) {\n"
                + "            try {\n"
                + "                properties.load(in);\n"
                + "            } finally {\n"
                + "                in.close();\n"
                + "            }\n"
                + "        }\n"
                + "        return new ConfigService(properties);\n"
                + "    }\n\n"
                + "    @Override\n"
                + "    public int timeoutMs() {\n"
                + "        String raw = properties.getProperty(\"app.timeout.ms\", \"30\");\n"
                + "        return Integer.parseInt(raw.trim());\n"
                + "    }\n"
                + "}\n");
        files.put("README.md", ""
                + "# First Delivery Sample\n\n"
                + "Config service for a small service.\n\n"
                + "## Configuration\n\n"
                + "| Key | Meaning | Default |\n"
                + "|-----|---------|---------|\n"
                + "| `app.timeout.ms` | Request timeout in milliseconds | `30` |\n"
                + "| `app.name` | Service display name | n/a |\n\n"
                + "`ConfigService` implements `TimeoutSource` and reads `app.timeout.ms` "
                + "from `application.properties`.\n");
        return files;
    }
}
