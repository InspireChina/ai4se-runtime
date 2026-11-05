package com.ai4se.execution.model;

import com.ai4se.execution.api.AdapterRequest;
import com.ai4se.runtime.common.util.Strings;

/**
 * Resolves which model id a CLI Adapter should pass as {@code --model}.
 *
 * <ol>
 *   <li>{@code AI4SE_MODEL} on the AdapterRequest env (Control-injected per role)</li>
 *   <li>Adapter instance default (constructor)</li>
 *   <li>blank → omit {@code --model}, use vendor CLI default</li>
 * </ol>
 */
public final class RoleModelResolver {

    private RoleModelResolver() {
    }

    public static String modelFor(AdapterRequest request, String adapterDefault) {
        if (request != null && request.env() != null) {
            String fromReq = request.env().get(RoleModelConfig.ENV_MODEL);
            if (!Strings.isBlank(fromReq)) {
                return fromReq.trim();
            }
            if (request.role() != null) {
                String key = RoleModelConfig.ENV_PREFIX
                        + RoleModelConfig.normalizeRole(request.role()).toUpperCase();
                // DEVELOPMENT vs DEV
                String keyed = request.env().get(key);
                if (Strings.isBlank(keyed) && "development".equals(RoleModelConfig.normalizeRole(request.role()))) {
                    keyed = request.env().get(RoleModelConfig.ENV_PREFIX + "DEV");
                }
                if (!Strings.isBlank(keyed)) {
                    return keyed.trim();
                }
            }
        }
        if (!Strings.isBlank(adapterDefault)) {
            return adapterDefault.trim();
        }
        return null;
    }
}
