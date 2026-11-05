package com.ai4se.orchestration.production;

import com.ai4se.execution.model.RoleModelConfig;
import com.ai4se.runtime.common.util.Strings;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Narrow production run input — no fixture / suite / wave knobs. */
public final class ProductionRunRequest {

    public final Path workspace;
    public final String storyId;
    /** Required when Story is not already open. */
    public final Path seedRequirement;
    public final List<String> writeScope;
    public final Duration adapterTimeout;
    /** Hard ceiling for Development↔Verify defect rounds (default 3 when unset). */
    public final int maxDevelopmentRounds;
    public final RoleModelConfig roleModels;

    private ProductionRunRequest(Builder b) {
        this.workspace = b.workspace;
        this.storyId = b.storyId;
        this.seedRequirement = b.seedRequirement;
        this.writeScope = Collections.unmodifiableList(new ArrayList<String>(b.writeScope));
        this.adapterTimeout = b.adapterTimeout == null ? Duration.ofMinutes(15) : b.adapterTimeout;
        if (b.maxDevelopmentRounds < 1) {
            throw new IllegalArgumentException("maxDevelopmentRounds must be >= 1");
        }
        this.maxDevelopmentRounds = b.maxDevelopmentRounds;
        this.roleModels = b.roleModels == null ? RoleModelConfig.empty() : b.roleModels;
        if (workspace == null) {
            throw new IllegalArgumentException("workspace required");
        }
        if (Strings.isBlank(storyId)) {
            throw new IllegalArgumentException("storyId required");
        }
    }

    public static Builder builder(Path workspace, String storyId) {
        return new Builder(workspace, storyId);
    }

    public static final class Builder {
        private final Path workspace;
        private final String storyId;
        private Path seedRequirement;
        private final List<String> writeScope = new ArrayList<String>();
        private Duration adapterTimeout;
        private int maxDevelopmentRounds = 3;
        private RoleModelConfig roleModels;

        private Builder(Path workspace, String storyId) {
            this.workspace = workspace;
            this.storyId = storyId;
        }

        public Builder seedRequirement(Path seed) {
            this.seedRequirement = seed;
            return this;
        }

        public Builder writeScope(String relativePathOrDir) {
            this.writeScope.add(relativePathOrDir);
            return this;
        }

        public Builder writeScopes(List<String> scopes) {
            if (scopes != null) {
                this.writeScope.addAll(scopes);
            }
            return this;
        }

        public Builder adapterTimeout(Duration timeout) {
            this.adapterTimeout = timeout;
            return this;
        }

        public Builder maxDevelopmentRounds(int rounds) {
            this.maxDevelopmentRounds = rounds;
            return this;
        }

        public Builder roleModels(RoleModelConfig models) {
            this.roleModels = models;
            return this;
        }

        public ProductionRunRequest build() {
            return new ProductionRunRequest(this);
        }
    }
}
