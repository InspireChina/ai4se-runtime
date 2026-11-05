package com.ai4se.runtime.engine.api;

import com.ai4se.runtime.common.util.Strings;
import com.ai4se.runtime.kernel.task.Budget;
import com.ai4se.runtime.kernel.task.GoalSpec;
import com.ai4se.runtime.kernel.task.RunMode;
import com.ai4se.runtime.kernel.task.WorkspaceRef;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Input to Runtime.submit — walking skeleton request. */
public final class RuntimeRequest {

    private final String projectId;
    private final String profileId;
    private final String profileRevision;
    private final String workflowId;
    private final GoalSpec goal;
    private final WorkspaceRef workspaceRef;
    private final RunMode runMode;
    private final Budget budget;
    private final Set<String> permissions;
    private final Map<String, String> labels;

    private RuntimeRequest(Builder b) {
        this.projectId = Strings.requireNonBlank(b.projectId, "projectId");
        this.profileId = Strings.requireNonBlank(b.profileId, "profileId");
        this.profileRevision = Strings.requireNonBlank(b.profileRevision, "profileRevision");
        this.workflowId = Strings.requireNonBlank(b.workflowId, "workflowId");
        this.goal = Objects.requireNonNull(b.goal, "goal");
        this.workspaceRef = Objects.requireNonNull(b.workspaceRef, "workspaceRef");
        this.runMode = b.runMode == null ? RunMode.UNATTENDED : b.runMode;
        this.budget = b.budget == null
                ? new Budget(10, 1, 1000L, Duration.ofMinutes(5), 0L)
                : b.budget;
        this.permissions = b.permissions == null ? Collections.<String>emptySet() : b.permissions;
        this.labels = b.labels == null ? Collections.<String, String>emptyMap() : b.labels;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getProjectId() { return projectId; }
    public String getProfileId() { return profileId; }
    public String getProfileRevision() { return profileRevision; }
    public String getWorkflowId() { return workflowId; }
    public GoalSpec getGoal() { return goal; }
    public WorkspaceRef getWorkspaceRef() { return workspaceRef; }
    public RunMode getRunMode() { return runMode; }
    public Budget getBudget() { return budget; }
    public Set<String> getPermissions() { return permissions; }
    public Map<String, String> getLabels() { return labels; }

    public static final class Builder {
        private String projectId;
        private String profileId = "default";
        private String profileRevision = "1";
        private String workflowId = "walking.skeleton";
        private GoalSpec goal;
        private WorkspaceRef workspaceRef;
        private RunMode runMode = RunMode.UNATTENDED;
        private Budget budget;
        private Set<String> permissions;
        private Map<String, String> labels;

        public Builder projectId(String projectId) { this.projectId = projectId; return this; }
        public Builder profileId(String profileId) { this.profileId = profileId; return this; }
        public Builder profileRevision(String profileRevision) { this.profileRevision = profileRevision; return this; }
        public Builder workflowId(String workflowId) { this.workflowId = workflowId; return this; }
        public Builder goal(GoalSpec goal) { this.goal = goal; return this; }
        public Builder workspaceRef(WorkspaceRef workspaceRef) { this.workspaceRef = workspaceRef; return this; }
        public Builder runMode(RunMode runMode) { this.runMode = runMode; return this; }
        public Builder budget(Budget budget) { this.budget = budget; return this; }
        public Builder permissions(Set<String> permissions) { this.permissions = permissions; return this; }
        public Builder labels(Map<String, String> labels) { this.labels = labels; return this; }

        public Builder workspacePath(String path) {
            this.workspaceRef = new WorkspaceRef(path, Optional.<String>empty());
            return this;
        }

        public RuntimeRequest build() {
            return new RuntimeRequest(this);
        }
    }
}
