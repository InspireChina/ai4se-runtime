package com.ai4se.runtime.demo.input;

import com.ai4se.runtime.demo.delivery.DeliveryScenario;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** DeliveryScenario backed by Production Input files (not hardcoded Java patches). */
public final class InputDeliveryScenario implements DeliveryScenario {

    private final String id;
    private final String typeLabel;
    private final String projectId;
    private final String requirement;
    private final String discoveryCommand;
    private final String verifyCommand;
    private final Map<String, String> planFiles;
    private final Map<String, String> executionFiles;

    public InputDeliveryScenario(
            String id,
            String typeLabel,
            String projectId,
            String requirement,
            String discoveryCommand,
            String verifyCommand,
            Map<String, String> planFiles,
            Map<String, String> executionFiles) {
        this.id = id;
        this.typeLabel = typeLabel;
        this.projectId = projectId;
        this.requirement = requirement;
        this.discoveryCommand = discoveryCommand;
        this.verifyCommand = verifyCommand;
        this.planFiles = Collections.unmodifiableMap(new LinkedHashMap<String, String>(planFiles));
        this.executionFiles = Collections.unmodifiableMap(new LinkedHashMap<String, String>(executionFiles));
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String typeLabel() {
        return typeLabel;
    }

    @Override
    public String requirement() {
        return requirement;
    }

    @Override
    public String projectId() {
        return projectId;
    }

    @Override
    public String fixtureDirName() {
        return "";
    }

    @Override
    public String discoveryCommand() {
        return discoveryCommand;
    }

    @Override
    public String verifyCommand() {
        return verifyCommand;
    }

    @Override
    public Map<String, String> planFiles() {
        return planFiles;
    }

    @Override
    public Map<String, String> executionFiles() {
        return executionFiles;
    }
}
