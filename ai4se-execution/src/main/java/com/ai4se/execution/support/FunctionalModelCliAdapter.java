package com.ai4se.execution.support;

import com.ai4se.execution.api.AdapterRequest;
import com.ai4se.execution.api.AdapterResult;
import com.ai4se.execution.api.ModelCliAdapter;
import com.ai4se.runtime.common.util.Strings;
import java.io.IOException;
import java.util.Collections;
import java.util.Objects;

/**
 * Test / pathway hook Adapter: Control supplies behavior; still returns {@link AdapterResult}
 * as-is (no retry / stage hints). Used to prove Adapter-on-spine without a live CLI binary.
 */
public final class FunctionalModelCliAdapter implements ModelCliAdapter {

    public interface Handler {
        AdapterResult execute(AdapterRequest request) throws IOException;
    }

    private final String name;
    private final Handler handler;

    public FunctionalModelCliAdapter(String name, Handler handler) {
        this.name = Strings.isBlank(name) ? "functional" : name.trim();
        this.handler = Objects.requireNonNull(handler, "handler");
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public AdapterResult execute(AdapterRequest request) {
        try {
            AdapterResult result = handler.execute(request);
            return result == null
                    ? AdapterResult.failure(-1, "", "", "handler returned null", Collections.<String, String>emptyMap())
                    : result;
        } catch (IOException e) {
            return AdapterResult.failure(
                    -1, "", "", "handler IO: " + e.getMessage(), Collections.<String, String>emptyMap());
        } catch (RuntimeException e) {
            return AdapterResult.failure(
                    -1, "", "", "handler error: " + e.getMessage(), Collections.<String, String>emptyMap());
        }
    }
}
