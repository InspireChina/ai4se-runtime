package com.ai4se.runtime.kernel.task;

import com.ai4se.runtime.common.id.ArtifactId;
import com.ai4se.runtime.common.util.Collections2;
import com.ai4se.runtime.common.util.Strings;
import java.util.List;
import java.util.Map;

public final class GoalSpec {

    private final String type;
    private final String acceptanceRef;
    private final List<ArtifactId> inputArtifactIds;
    private final Map<String, Object> params;

    public GoalSpec(
            String type,
            String acceptanceRef,
            List<ArtifactId> inputArtifactIds,
            Map<String, Object> params) {
        this.type = Strings.requireNonBlank(type, "type");
        this.acceptanceRef = acceptanceRef;
        this.inputArtifactIds = Collections2.copyList(inputArtifactIds);
        this.params = Collections2.copyMap(params);
    }

    public String getType() { return type; }
    public String getAcceptanceRef() { return acceptanceRef; }
    public List<ArtifactId> getInputArtifactIds() { return inputArtifactIds; }
    public Map<String, Object> getParams() { return params; }
}
