package com.ai4se.runtime.worker.api;

import com.ai4se.runtime.common.context.ExecutionContextView;
import com.ai4se.runtime.common.id.WorkItemId;
import com.ai4se.runtime.common.id.WorkerId;

public interface Worker {

    WorkerId workerId();

    WorkerDescriptor descriptor();

    WorkerHealth health();

    WorkResult execute(WorkRequest request, ExecutionContextView context);

    default void cancel(WorkItemId workItemId) {
        // optional cooperative cancel
    }
}
