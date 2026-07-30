# Production Input Contract

Loader reads this directory and drives existing Demo serial delivery
(`SerialDeliveryRunner` → `Runtime.submit` → Workers). **Not** part of Runtime Kernel.

## Layout

```text
input/
  requirement.md      # required — delivery goal (plain text / markdown)
  profile.yaml        # required — id / labels
  verify.yaml         # required — verification command
  discovery.yaml      # optional — discovery command; omit or empty = echo skip-discovery
  plan.md             # required — plan body (written to workspace as PLAN.md)
  patches/            # required — files to write under workspace (relative paths)
    src/.../Foo.java
    README.md
```

## profile.yaml

```yaml
id: sample-config
typeLabel: 修改配置
projectId: production-input
```

| Field | Required | Meaning |
|-------|----------|---------|
| id | yes | Bundle id (logs / reports) |
| typeLabel | no | Human label |
| projectId | no | RuntimeRequest.projectId (default: production-input) |

## discovery.yaml

```yaml
command: git status
```

Empty / missing → `echo skip-discovery` (stage still runs; thin discovery).

## verify.yaml

```yaml
command: mvn -f pom.xml -q test
```

Must be ShellWorker-allowlisted today (`echo`, `pwd`, `git status`, `mvn -f <pom> -q test`).

## plan.md / patches/

- `plan.md` → Execution writes `PLAN.md` via FileEditWorker
- `patches/**` → Execution FileEdit map (path relative to `patches/` = path in workspace)

## What is NOT in the contract

- Auto-generated requirements
- AI planning
- Human-wait
- Resume / Workflow / StageRunner in Kernel
