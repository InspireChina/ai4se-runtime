package com.ai4se.runtime.demo.delivery;

import java.util.Map;

/** Demo-only scenario description for a single real delivery (not Runtime Planning). */
public interface DeliveryScenario {

    String id();

    String typeLabel();

    String requirement();

    /** RuntimeRequest.projectId — default may be derived from id. */
    String projectId();

    String fixtureDirName();

    String discoveryCommand();

    String verifyCommand();

    Map<String, String> planFiles();

    Map<String, String> executionFiles();
}
