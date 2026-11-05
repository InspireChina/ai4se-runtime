package com.ai4se.runtime.engine.store;

import com.ai4se.runtime.common.id.CheckpointId;
import com.ai4se.runtime.common.id.TaskId;
import com.ai4se.runtime.kernel.checkpoint.Checkpoint;
import com.ai4se.runtime.kernel.checkpoint.CheckpointStore;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory CheckpointStore — no DB / filesystem. */
public final class MemoryCheckpointStore implements CheckpointStore {

    private final Map<String, Checkpoint> byId = new ConcurrentHashMap<String, Checkpoint>();

    @Override
    public void save(Checkpoint checkpoint) {
        Objects.requireNonNull(checkpoint, "checkpoint");
        String id = checkpoint.checkpointId().value();
        Checkpoint previous = byId.putIfAbsent(id, checkpoint);
        if (previous != null) {
            throw new IllegalStateException("Checkpoint already saved (immutable): " + id);
        }
    }

    @Override
    public Optional<Checkpoint> load(CheckpointId checkpointId) {
        Objects.requireNonNull(checkpointId, "checkpointId");
        return Optional.ofNullable(byId.get(checkpointId.value()));
    }

    @Override
    public Optional<Checkpoint> latest(TaskId taskId) {
        List<Checkpoint> all = list(taskId);
        if (all.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(all.get(all.size() - 1));
    }

    @Override
    public List<Checkpoint> list(TaskId taskId) {
        Objects.requireNonNull(taskId, "taskId");
        List<Checkpoint> list = new ArrayList<Checkpoint>();
        for (Checkpoint checkpoint : byId.values()) {
            if (checkpoint.taskId().equals(taskId)) {
                list.add(checkpoint);
            }
        }
        Collections.sort(list, new Comparator<Checkpoint>() {
            @Override
            public int compare(Checkpoint a, Checkpoint b) {
                return Long.compare(a.sequence(), b.sequence());
            }
        });
        return Collections.unmodifiableList(list);
    }
}
