package com.ai4se.runtime.worker.api;

import com.ai4se.runtime.common.id.WorkerId;
import java.util.List;
import java.util.Optional;

public interface WorkerRegistry {

    void register(Worker worker);

    Optional<Worker> find(WorkerId workerId);

    List<Worker> findByCapability(String capabilityId);
}
