# RFC-0012 · Project Profile SPI

- Status: **Accepted-Draft**
- Related: ADR-0013, Architecture 19

## SPI

```
ProjectProfile {
  id, version, projectId, runtimeApiVersion,
  plugins[], workflowRouting, permissions, budget,
  qualityGates, knowledge, repository, checkpoint,
  model, runMode, rules
}

ProfileEngine {
  load(profileId): ProjectProfile
  validate(profile): ValidationReport
  snapshot(profileId): ProfileRevision
  merge(snapshot, TaskRequestOverrides): EffectiveConfig
  activate(projectId, profileId): void
}
```

## Merge policy

- deny 列表：并集（更严）
- budget：取更小
- permissions baseline：交集（更严）除非 Grant
- workflowId：Task 指定优先，否则 routing
