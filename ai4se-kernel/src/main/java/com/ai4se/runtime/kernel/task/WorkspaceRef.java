package com.ai4se.runtime.kernel.task;

import com.ai4se.runtime.common.util.Strings;
import java.util.Optional;

public final class WorkspaceRef {

    private final String rootPath;
    private final Optional<String> gitRef;

    public WorkspaceRef(String rootPath, Optional<String> gitRef) {
        this.rootPath = Strings.requireNonBlank(rootPath, "rootPath");
        this.gitRef = gitRef == null ? Optional.<String>empty() : gitRef;
    }

    public String getRootPath() { return rootPath; }
    public Optional<String> getGitRef() { return gitRef; }
}
