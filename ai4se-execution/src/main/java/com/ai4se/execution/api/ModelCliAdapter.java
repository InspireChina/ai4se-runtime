package com.ai4se.execution.api;

/** Tool Adapter — executes a Context Package; does not own Control. */
public interface ModelCliAdapter {

    String name();

    AdapterResult execute(AdapterRequest request);
}
