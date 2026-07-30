package com.example.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/** BUG: always returns true; ignores app.feature.enabled. */
public final class FeatureFlags {

    private final Properties properties;

    public FeatureFlags(Properties properties) {
        this.properties = properties;
    }

    public static FeatureFlags load() throws IOException {
        Properties properties = new Properties();
        InputStream in = FeatureFlags.class.getResourceAsStream("/application.properties");
        if (in != null) {
            try {
                properties.load(in);
            } finally {
                in.close();
            }
        }
        return new FeatureFlags(properties);
    }

    public boolean isFeatureEnabled() {
        return true;
    }
}
