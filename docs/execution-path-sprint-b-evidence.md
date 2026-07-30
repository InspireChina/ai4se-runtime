# Evidence Delta — Execution Path Sprint B

playbook: docs/build-pathway-playbook.md §2.1.1
question: Does Verification cover all Plan Acceptance IDs?

## Plan Acceptance

- acceptance IDs: [A1, A2, A3]
- status: **PASS**

## Patch Coverage (Sprint A metric retained)

- declared: 4
- patched: 4
- coverage: 4/4
- undeclared: 0
- status: **PASS**

## Shell Verification (necessary, not sufficient)

- mvn test success: true
- status: **PASS**

## Acceptance → Verification Matrix

# verification-matrix

Acceptance Provenance: rows generated from Plan Acceptance IDs only.
Status COVERED = evidence found in workspace; MISSING = no verify binding or evidence absent.
mvn test PASS alone is **not** sufficient. Consumers must not add Acceptance IDs.

| Acceptance | Criterion | Evidence | Status | Note |
|------------|-----------|----------|--------|------|
| A1 | `OrderApi.timeoutMs()` returns value from `app.order.timeout.ms` (fixture expects 3000) | `OrderApiTest#timeoutMs_readsFromApplicationProperties` | **COVERED** | test method present: OrderApiTest#timeoutMs_readsFromApplicationProperties |
| A2 | `createOrder` JSON embeds the configured `timeoutMs` | `OrderApiTest#createOrder_embedsConfiguredTimeout` | **COVERED** | test method present: OrderApiTest#createOrder_embedsConfiguredTimeout |
| A3 | README documents config key `app.order.timeout.ms` | `README.md#contains:app.order.timeout.ms` | **COVERED** | file contains needle |

## Summary

- covered: 3
- missing: 0
- complete: true

**Gate: PASS** — all Acceptance IDs COVERED.

- matrix file: `verification-matrix.md`
- status: **PASS**

## Gate self-check (MISSING must not default PASS)

- injected A99 without mapping → complete=false
- status: **PASS**

## Evidence Delta (new this round only)

| New evidence | Result |
|--------------|--------|
| Plan emits Acceptance IDs (A1…) | PASS |
| verification-mapping + verification-matrix.md | PASS |
| Unmapped Acceptance → MISSING (not silent PASS) | PASS |
| Patch Coverage metric (declared/patched/undeclared) | PASS |

Not claimed: JaCoCo, mutation, E2E, Promotion domain, Claude, Review depth.
Not re-scored as main: Sprint A Execution bound / Phase 1 Analysis.

## Sprint B verdict

**PASS** — Verification covers Plan Acceptance via explicit matrix; mvn test alone is no longer the definition of Verification.
