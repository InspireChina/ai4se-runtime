package com.example.delivery;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Loads application.properties but currently ignores app.timeout.ms (bug).
 */
public final class ConfigService {

    private final Properties properties;

    public ConfigService(Properties properties) {
        this.properties = properties;
    }

    public static ConfigService loadFromClasspath() throws IOException {
        Properties properties = new Properties();
        InputStream in = ConfigService.class.getResourceAsStream("/application.properties");
        if (in != null) {
            try {
                properties.load(in);
            } finally {
                in.close();
            }
        }
        return new ConfigService(properties);
    }

    public int timeoutMs() {
        // BUG: should read app.timeout.ms from properties (expected 5000).
        return 30;
    }
}
