package com.ai4se.runtime.kernel.artifact;

public final class ArtifactKind {
    public static final String PLAN_DOCUMENT = "plan.document";
    public static final String CODE_PATCH = "code.patch";
    public static final String TEST_REPORT = "test.report";
    public static final String BUILD_LOG = "build.log";
    public static final String MODEL_STRUCTURED = "model.structured";
    public static final String REPORT_SUMMARY = "report.summary";
    public static final String VCS_COMMIT_REF = "vcs.commit-ref";
    public static final String BROWSER_SCREENSHOT = "browser.screenshot";

    private ArtifactKind() {}
}
