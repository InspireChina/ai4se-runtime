package com.ai4se.runtime.demo.input;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Minimal flat {@code key: value} YAML reader (Production Input only).
 * Not a general YAML engine — keeps Input Layer free of extra platform deps.
 */
public final class FlatYaml {

    private FlatYaml() {
    }

    public static Map<String, String> read(Path file) throws IOException {
        if (file == null || !Files.isRegularFile(file)) {
            return Collections.emptyMap();
        }
        Map<String, String> map = new HashMap<String, String>();
        BufferedReader reader = Files.newBufferedReader(file, Charset.forName("UTF-8"));
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                int colon = trimmed.indexOf(':');
                if (colon <= 0) {
                    throw new IOException("invalid yaml line in " + file + ": " + line);
                }
                String key = trimmed.substring(0, colon).trim();
                String value = trimmed.substring(colon + 1).trim();
                if ((value.startsWith("\"") && value.endsWith("\""))
                        || (value.startsWith("'") && value.endsWith("'"))) {
                    value = value.substring(1, value.length() - 1);
                }
                map.put(key, value);
            }
        } finally {
            reader.close();
        }
        return map;
    }

    public static String require(Map<String, String> map, String key, Path source) throws IOException {
        String value = map.get(key);
        if (value == null || value.trim().isEmpty()) {
            throw new IOException("missing required key '" + key + "' in " + source);
        }
        return value.trim();
    }
}
