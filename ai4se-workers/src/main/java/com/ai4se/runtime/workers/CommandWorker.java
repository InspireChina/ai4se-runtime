package com.ai4se.runtime.workers;

import com.ai4se.runtime.common.context.ExecutionContextView;
import com.ai4se.runtime.common.id.WorkerId;
import com.ai4se.runtime.worker.api.WorkRequest;
import com.ai4se.runtime.worker.api.WorkResult;
import com.ai4se.runtime.worker.api.Worker;
import com.ai4se.runtime.worker.api.WorkerDescriptor;
import com.ai4se.runtime.worker.api.WorkerHealth;
import java.util.List;

/**
 * Legacy alias for Milestone-2 demos/tests.
 * Sprint-7 real worker is {@link ShellWorker}; execution delegates to it.
 */
public final class CommandWorker implements Worker {

    public static final WorkerId ID = new WorkerId("worker_command");

    private final ShellWorker shell = new ShellWorker();
    private final WorkerDescriptor descriptor;

    public CommandWorker() {
        WorkerDescriptor shellDesc = shell.descriptor();
        this.descriptor = new WorkerDescriptor(
                ID,
                shellDesc.getKind(),
                "CommandWorker",
                shellDesc.getVersion(),
                shellDesc.getCapabilities(),
                shellDesc.getMaxConcurrent(),
                shellDesc.getResourceClasses(),
                shellDesc.isIdempotentByDefault());
    }

    @Override
    public WorkerId workerId() {
        return ID;
    }

    @Override
    public WorkerDescriptor descriptor() {
        return descriptor;
    }

    @Override
    public WorkerHealth health() {
        return shell.health();
    }

    @Override
    public WorkResult execute(WorkRequest request, ExecutionContextView context) {
        return shell.execute(request, context);
    }

    static List<String> toArgv(String commandLine) {
        return ShellWorker.toArgv(commandLine);
    }
}
