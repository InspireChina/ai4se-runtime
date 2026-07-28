# RFC-0009 · Checkpoint Store SPI

- Status: **Accepted-Draft**
- Related: ADR-0010, Architecture 16

## SPI

```
CheckpointRecord { checkpointId, taskId, sequence, payload, integrityHash, createdAt }

CheckpointStore {
  save(record): void
  loadLatest(taskId): Optional<CheckpointRecord>
  load(checkpointId): Optional<CheckpointRecord>
  list(taskId, limit): List<CheckpointRecord>
  prune(taskId, keepLastN): void
}

CheckpointPayload {
  workflowCursor,
  iterationIndex,
  taskContextSnapshot,
  budgetRemaining,
  graphSnapshotId,
  artifactsIndex,
  workspaceFingerprint,
  traceSpanId
}
```

## Transaction

`save` 与 Task 状态更新、Idempotency 热键应在同一事务边界（由 persistence adapter 保证）。

## WorkspaceFingerprint

`{ gitCommit?, fileHashesDigest, strategy }`
