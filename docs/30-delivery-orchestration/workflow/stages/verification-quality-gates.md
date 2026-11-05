# Verification Quality Gates

`.ai4se/repository/entries.yaml` may declare an optional `quality` list:

```yaml
test:
  - mvn -pl service -am test
quality:
  - ./scripts/check-changed-source-style.sh
```

The commands are repository-owner controlled. AI4SE runs every `test` command first, then every
`quality` command, then frozen acceptance probes. Any non-zero quality result is a Verification
failure: it creates a Defect Package and returns to the bounded Development loop before Review.

Do not infer commands from Husky, Git hooks, or package-manager metadata. Those hooks can mutate
files, depend on staging state, or perform arbitrary operations. Capture an equivalent, read-only,
scoped command in `quality` after the repository owner has verified it on the baseline. The real
Git hook remains the final Delivery authority.

Planning receives `entries.yaml` as a P1 repository fact and must describe any quality gates in
`test-strategy.md`. Verification reports expose `quality_gate_count` and `quality_gates_passed`.
