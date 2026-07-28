package com.ai4se.runtime.worker.api;

public enum WorkResultStatus {
    OK,
    RETRYABLE_FAIL,
    FATAL_FAIL,
    POLICY_EXCEPTION
}
