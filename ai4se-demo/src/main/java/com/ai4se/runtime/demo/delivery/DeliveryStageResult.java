package com.ai4se.runtime.demo.delivery;

import com.ai4se.runtime.common.id.ArtifactId;
import com.ai4se.runtime.engine.api.RuntimeResult;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** One stage of the First Production Delivery pipeline. */
public final class DeliveryStageResult {

    private final String stage;
    private final String requirementNote;
    private final RuntimeResult result;

    public DeliveryStageResult(String stage, String requirementNote, RuntimeResult result) {
        this.stage = stage;
        this.requirementNote = requirementNote;
        this.result = result;
    }

    public String getStage() {
        return stage;
    }

    public String getRequirementNote() {
        return requirementNote;
    }

    public RuntimeResult getResult() {
        return result;
    }

    public List<String> artifactIdValues() {
        List<String> ids = new ArrayList<String>();
        for (ArtifactId id : result.getCommittedArtifactIds()) {
            ids.add(id.value());
        }
        return Collections.unmodifiableList(ids);
    }
}
