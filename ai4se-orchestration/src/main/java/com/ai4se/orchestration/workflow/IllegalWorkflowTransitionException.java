package com.ai4se.orchestration.workflow;

/** Illegal Control action (jump, advance while stopped, empty stop reason, …). */
public final class IllegalWorkflowTransitionException extends RuntimeException {

    public IllegalWorkflowTransitionException(String message) {
        super(message);
    }
}
