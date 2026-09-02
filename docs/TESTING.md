# Testing and evidence strategy

## Principle

A working screen is not enough. Each public claim needs a repeatable test, measured result, or visible simulation label.

## Test layers

### Unit tests

- domain-event validation;
- role policy;
- vector-clock comparison;
- CRDT merge behavior;
- deduplication;
- TTL and hop limit;
- routing and vehicle constraints;
- SLA and slowdown prediction;
- preemption policy;
- rendezvous calculation;
- cryptographic verification wrappers;
- localization key completeness.

### Contract tests

- Protobuf encode and decode across Kotlin, Go, and TypeScript;
- schema compatibility with stored fixtures;
- signature stability over canonical bytes;
- unsupported-version preservation;
- observer data cannot enter the mesh domain path accidentally.

### Integration tests

- local event to projection lifecycle;
- outbox to peer inbox to domain acceptance;
- interruption after durable receipt but before acknowledgement;
- duplicate arrival through different peers;
- concurrent offline edit and later convergence;
- route failure feeding triage and assignment;
- prediction feeding route cost;
- signed handoff feeding custody and inventory.

### Device tests

- three physical Android phones;
- Compose navigation and state restoration;
- Room migration and process-restart recovery;
- Proto DataStore language persistence;
- Nearby Connections permission, discovery, and transfer behavior;
- foreground-service start, stop, notification, and process recovery;
- commercial internet unavailable;
- process kill and restart during transfer;
- screen lock and unlock;
- low battery or simulated battery policy;
- language switch during an active form and mission;
- QR camera and manual-code fallback;
- small and large screens.

### Dashboard tests

- 1366 by 768 and 1920 by 1080 layouts;
- keyboard navigation;
- reconnect and full projection rebuild;
- delayed and out-of-order observer events;
- Bangla and English parity;
- dashboard disconnection while phones continue.

## Module evidence matrix

| Module | Automated evidence | Live evidence | Failure evidence |
|---|---|---|---|
| M1 | Credential and policy tests | Offline provision and role denial | Expired or wrong-role credential |
| M2 | Convergence property tests | Conflict and equal final hash | Restart during sync |
| M3 | Queue, TTL, and dedup tests | A to B to C relay | B disconnect and recovery |
| M4 | Graph and constraint fixtures | Edge failure and timed reroute | No valid route state |
| M5 | Signature and nonce tests | Valid QR acceptance | Tamper and replay rejection |
| M6 | SLA and policy fixtures | P0 preemption proposal | Missing safe waypoint |
| M7 | Model evaluation script | Risk input and overlay | Model unavailable baseline |
| M8 | Reachability and rendezvous tests | Boat to simulated-drone handoff | No feasible rendezvous |

## Bilingual test matrix

For every critical flow, run:

| Case | Bangla | English | Switch mid-flow |
|---|---:|---:|---:|
| First run and provision | Required | Required | Not applicable |
| Sign-in and role denial | Required | Required | Required |
| Create P0 request | Required | Required | Required |
| View route failure | Required | Required | Required |
| Resolve conflict | Required | Required | Required |
| Confirm preemption | Required | Required | Required |
| Generate and verify QR | Required | Required | Required |
| Read rejection reason | Required | Required | Required |
| Inspect custody chain | Required | Required | Required |

Check text meaning, wrapping, font rendering, accessible label, focus order, state preservation, and screenshot result.

## Fault injection

Run each fault from a clean known seed:

- peer leaves before transfer;
- peer leaves after durable receipt but before acknowledgement;
- app process dies while queue is non-empty;
- duplicate envelope arrives through two paths;
- message expires while offline;
- device clock differs within and beyond tolerated skew;
- active edge fails;
- prediction conflicts with confirmed edge state;
- inventory and priority change concurrently;
- two custody offers compete;
- QR is altered or reused;
- dashboard disconnects;
- model file is missing or invalid;
- storage is nearly full.

Each fault report records expected result, actual result, logs, device versions, seed, and recovery.

## Performance measurements

Targets must be approved before implementation. Reports must distinguish phone, laptop, simulated-node, and real-device results.

Measure:

- route recomputation median and 95th percentile;
- message relay latency and recovery time;
- duplicate suppression count;
- event projection rebuild time;
- application memory on the lowest target phone;
- encrypted envelope size;
- local database growth per 1,000 events;
- model load time, inference latency, and memory;
- Go service connection count, throughput, errors, CPU, and memory;
- dashboard update latency under the demo load.

Do not report `10,000 concurrent connections` without documenting transport, payload, duration, machine, success criteria, error rate, and whether clients were simulated.

## ML evaluation

- Freeze and hash the dataset.
- Document labels and provenance.
- Keep training, validation, and held-out test sets separate.
- Compare against a simple non-ML baseline.
- Report precision, recall, F1, confusion matrix, and selected threshold.
- Test inference output parity before and after ONNX export.
- Test missing, out-of-range, and simulated feature values.
- Include examples of false positives and false negatives.
- Do not imply that metrics on synthetic data estimate real flood performance.

## Rehearsal log

Use this format for at least twenty complete runs:

```text
Run:
Commit:
Release build:
Scenario seed:
Phones and OS versions:
Dashboard viewport:
Languages used:
Modules passed:
Failures or manual recovery:
Total time:
Artifacts:
```

Final release requires three consecutive unchanged passes. A code, data, model, or script change resets the consecutive-pass count.

## Evidence output

```text
artifacts/
  screenshots/
  recordings/
  reports/
    unit-tests/
    integration-tests/
    fault-injection/
    load-test/
    mobile-memory/
    route-latency/
    ml-evaluation/
    localization/
  rehearsals/
```
