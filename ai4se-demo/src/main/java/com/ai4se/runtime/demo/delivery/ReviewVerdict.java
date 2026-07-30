package com.ai4se.runtime.demo.delivery;

/**
 * Sprint C: Review verdict derived only from VerificationMatrix (+ shell stage facts).
 * Delivery must consume this — not Runtime AllStagesSucceeded alone.
 */
public final class ReviewVerdict {

    public final boolean shellVerificationOk;
    public final boolean matrixComplete;
    public final boolean deliveryPass;
    public final String reviewMarkdown;
    public final VerificationMatrix matrix;

    public ReviewVerdict(
            boolean shellVerificationOk,
            VerificationMatrix matrix,
            String reviewMarkdown) {
        this.shellVerificationOk = shellVerificationOk;
        this.matrix = matrix;
        this.matrixComplete = matrix != null && matrix.complete;
        // Delivery PASS only if shell verify ran OK AND every Acceptance is COVERED.
        this.deliveryPass = shellVerificationOk && this.matrixComplete;
        this.reviewMarkdown = reviewMarkdown;
    }
}
