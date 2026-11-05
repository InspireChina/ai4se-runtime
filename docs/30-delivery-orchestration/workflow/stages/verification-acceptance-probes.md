# Frozen Acceptance Probes

`mvn clean test` (or another repository entry) proves only that the configured entry command
exited successfully. It does not, by itself, prove a natural-language Acceptance item.

For an unattended Delivery claim, the operator may freeze probes before the Story starts at:

```text
.ai4se/acceptance-probes/<story-id>/probes.properties
```

The manifest covers every Acceptance item in order. Each item requires a command, an external
probe path, and the SHA-256 of that probe:

```properties
ac.count=2
ac.1.path=.ai4se/acceptance-probes/example/ac1.sh
ac.1.sha256=<64 lowercase hex characters>
ac.1.command=sh .ai4se/acceptance-probes/example/ac1.sh
ac.2.path=.ai4se/acceptance-probes/example/ac2.sh
ac.2.sha256=<64 lowercase hex characters>
ac.2.command=sh .ai4se/acceptance-probes/example/ac2.sh
```

The manifest and probe files are operator-owned baseline inputs: they must be committed before
`run`, must remain outside Allowed files, and their path/hash are checked before and after
Verification. A missing manifest is allowed, but every AC is recorded as `UNPROVEN` and Control
will not permit a Review `PASS` to reach Delivery.

Verification records every AC as `PROVEN`, `FAILED`, or `UNPROVEN`, including its command, exit
code, timeout state, probe path, and probe SHA. Entry-command success plus every AC `PROVEN` is
required for automatic Delivery. A failed probe is a Verification failure; an unproven item remains
an honest conditional-review case.
