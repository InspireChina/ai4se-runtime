package com.ai4se.runtime.common.error;

import java.util.Objects;

public final class ErrorRef {

    private final ErrorTaxonomy taxonomy;
    private final ReasonCode reasonCode;
    private final String message;

    public ErrorRef(ErrorTaxonomy taxonomy, ReasonCode reasonCode, String message) {
        this.taxonomy = Objects.requireNonNull(taxonomy, "taxonomy");
        this.reasonCode = Objects.requireNonNull(reasonCode, "reasonCode");
        this.message = message;
    }

    public ErrorTaxonomy getTaxonomy() {
        return taxonomy;
    }

    public ReasonCode getReasonCode() {
        return reasonCode;
    }

    public String getMessage() {
        return message;
    }
}
