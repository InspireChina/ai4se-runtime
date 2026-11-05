package com.ai4se.orchestration.analysis;

/** Hard gate failure for Analysis → Plan / Plan → Dev. */
public final class StageGateException extends RuntimeException {

    public StageGateException(String message) {
        super(message);
    }
}
