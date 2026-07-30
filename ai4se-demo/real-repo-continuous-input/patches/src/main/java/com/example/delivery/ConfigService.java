package com.example.delivery;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/** Loads application.properties and exposes configured timeout. */
public final class ConfigService implements TimeoutSource {

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

    @Override
    public int timeoutMs() {
        String raw = properties.getProperty("app.timeout.ms", "30");
        return Integer.parseInt(raw.trim());
    }
}
