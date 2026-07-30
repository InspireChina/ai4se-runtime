# Evidence Delta — Acceptance Provenance

playbook: docs/build-pathway-playbook.md §2.1.1
question: Are Plan Acceptance IDs the sole source across Matrix/Review/Delivery?

## 1. Plan is sole Acceptance ID source

- plan IDs: [A1, A2, A3]
- each ID has inline `| verify:`: true
- status: **PASS**

## 2. Matrix auto-generated from Plan

- matrix IDs: [A1, A2, A3]
- complete: true
- IDs match Plan (no external ID list): true
- status: **PASS**

## 3. Review consumes Plan IDs only (no invent)

- review provenance IDs: [A1, A2, A3]
- invented IDs: []
- status: **PASS**

## 4. Delivery consumes Plan IDs only (no invent)

- delivery provenance IDs: [A1, A2, A3]
- invented IDs: []
- status: **PASS**

## Provenance Gate (ID set equality)

- Plan=Matrix=Review=Delivery ID sets equal: [A1, A2, A3]
- status: **PASS**

## Negative: Plan has extra ID Matrix dropped → Gate FAIL

- pass=false msg=Plan IDs != Matrix IDs: plan=[A1, A2, A3] matrix=[A1, A2]
- status: **PASS**

## Negative: Review invents A99 → detected

- invented: [A99]
- status: **PASS**

## Evidence Delta (new this round only)

| New evidence | Result |
|--------------|--------|
| Plan embeds Acceptance IDs + verify bindings | PASS |
| Matrix built from Plan only (no parallel ID list) | PASS |
| Review/Delivery ID sets == Plan; invent forbidden | PASS |
| Provenance Gate set equality | PASS |

Not claimed: Patch≠Acceptance evolution, Matrix V2, Claude, 真仓.
Stopped after exit criteria — no further Matrix polish.

## Acceptance Provenance verdict

**PASS** — Acceptance ID lifecycle is Plan-sourced end-to-end.
