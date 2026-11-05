package com.ai4se.runtime.demo.delivery;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Loads Acceptance ID → verification evidence refs.
 * Formats:
 * <pre>
 * A1: OrderApiTest#timeoutMs_readsFromApplicationProperties
 * A2: README.md#contains:app.order.timeout.ms
 * </pre>
 * Demo/Delivery layer — not Runtime Kernel.
 */
public final class VerificationMapping {

    private VerificationMapping() {
    }

    public static Map<String, String> load(Path file) throws IOException {
        if (file == null || !Files.isRegularFile(file)) {
            return Collections.emptyMap();
        }
        String body = new String(Files.readAllBytes(file), Charset.forName("UTF-8"));
        return parse(body);
    }

    public static Map<String, String> parse(String body) {
        Map<String, String> out = new LinkedHashMap<String, String>();
        if (body == null || body.trim().isEmpty()) {
            return out;
        }
        String[] lines = body.split("\\r?\\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            int colon = trimmed.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            String id = trimmed.substring(0, colon).trim();
            String ref = trimmed.substring(colon + 1).trim();
            if (id.matches("A\\d+") && !ref.isEmpty()) {
                out.put(id, ref);
            }
        }
        return out;
    }
}
