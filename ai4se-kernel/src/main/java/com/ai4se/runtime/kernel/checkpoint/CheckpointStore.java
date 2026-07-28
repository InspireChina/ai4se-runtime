package com.ai4se.runtime.kernel.checkpoint;

import com.ai4se.runtime.common.id.CheckpointId;
import com.ai4se.runtime.common.id.TaskId;
import java.util.List;
import java.util.Optional;

/**
 * Checkpoint Store SPI — persistence port only.
 * No concrete storage dependencies (DB/FS/memory) in this interface.
 */
public interface CheckpointStore {

    void save(Checkpoint checkpoint);

    Optional<Checkpoint> load(CheckpointId checkpointId);

    Optional<Checkpoint> latest(TaskId taskId);

    List<Checkpoint> list(TaskId taskId);
}
