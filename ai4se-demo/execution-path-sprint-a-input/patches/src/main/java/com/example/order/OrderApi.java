package com.example.order;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/** Order HTTP-style handler with configurable timeout. */
public final class OrderApi {

    private final int timeoutMs;

    public OrderApi() {
        this(loadTimeoutMs());
    }

    public OrderApi(int timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public int timeoutMs() {
        return timeoutMs;
    }

    public String createOrder(String payload) {
        return "{\"status\":\"ok\",\"id\":\"o1\",\"timeoutMs\":" + timeoutMs + "}";
    }

    private static int loadTimeoutMs() {
        Properties properties = new Properties();
        InputStream in = OrderApi.class.getResourceAsStream("/application.properties");
        if (in != null) {
            try {
                properties.load(in);
            } catch (IOException ignored) {
                // fall through to default
            } finally {
                try {
                    in.close();
                } catch (IOException ignored) {
                    // ignore
                }
            }
        }
        String raw = properties.getProperty("app.order.timeout.ms", "3000");
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ex) {
            return 3000;
        }
    }
}
