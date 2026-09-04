# Testing and evidence strategy

## Principle

A working screen is not enough. Each public claim needs a repeatable test, measured result, or visible simulation label.

## Local commands

Digital Delta deliberately uses local verification rather than GitHub Actions or hosted CI.

```bash
# JVM tests plus debug APK build
scripts/verify-local.sh

# The same checks plus Compose journeys on a connected emulator or phone
scripts/verify-local.sh --connected

# Build, install, and launch the field app with the official Android CLI
scripts/run-field-android.sh --device=emulator-5554
```

The local runner lints Protobuf schemas, rejects JSON references in mesh packages, verifies the checksum-pinned PMTiles archive, reproduces and byte-compares the synthetic dataset, metrics, configuration, Android assets, and ONNX model, runs Android tests and APK assembly, runs Go race tests, vet, and all Go command builds, and then tests and production-builds the command dashboard. The Android suite tests the public domain seams for authorization, vector clocks, relay policy, routing, signed handoff verification, triage, route-risk classification and fallback, fleet rendezvous, hybrid recipient encryption, signed provisioning credentials, enrollment requests, binary Protobuf envelope compatibility, Protobuf-only Nearby frames, and runtime permission selection from Android 9 through Android 17. The current 37-test connected suite covers language parity, mid-flow state preservation, offline request queuing, replay rejection, non-exportable Android Keystore identity behavior, durable credential storage and restoration, the production enrollment screen, preserving Room migrations, atomic mesh ingress, duplicate/TTL/hop rejection, interrupted-send retry, dead letters, database restart recovery, bilingual relay-control state, bundled ONNX execution, the bilingual risk-to-reroute journey, the packaged drone-required graph, hybrid-fleet Room events, custom boat-to-drone custody, and the bilingual M8 journey. `ProductionRequestFlowTest` additionally provisions N6 through the production Room directory, completes the visible request journey through the Hilt graph, parses the persisted Protobuf envelope, and decrypts it with the intended N6 private key. Go integration tests exercise durable mesh restart recovery, signed enrollment issuance and verification, bidirectional gRPC acknowledgements, ordered observer persistence, idempotent publication, cursor replay, SSE sanitization, and deterministic drill labelling. Thirteen command-app tests cover Bangla-first content parity, deterministic scenario completion, observer-disconnect messaging, reset behavior, syncing and security rejections, relay loss and vehicle delay, automatic replay pause, observer connection/cursor behavior, reconstruction of route, hazard, rendezvous and vehicle state, local-only map sources, geographic mission coordinates, and viewport coverage.

The live Android 15 emulator check starts the real Nearby `CLUSTER` adapter in a `connectedDevice` foreground service, grants the app-requested nearby and notification permissions, verifies advertise/discover state and battery cadence in Bangla and English, and stops the service through the field UI. This proves the single-device lifecycle only. It does not replace the required A to B to C transfer on three physical phones.

The M2 tests compare causal, equal, and concurrent vector clocks; prove deterministic safe-field convergence in both arrival orders; reject automatic resolution of destination, priority, and medical-quantity disagreement; and merge grow-only sets and per-replica PN-counters. A connected Room test raises a simulated destination conflict, closes and reopens the database, resolves the restored conflict, and verifies the operation count, selected projection, and 64-character convergence hash. The Compose journey proves both languages and the explicit human choice. Physical two-phone convergence and assignment tombstones remain release evidence.

The M4 unit tests parse the JSON fixture, validate graph references, normalize `river` to `WATERWAY`, enforce truck/boat/drone edge constraints, exclude failed edges, and inject a monotonic clock to verify reported recomputation latency. The actual bundled asset is parsed in a connected test so a stale or malformed packaged fixture fails the local gate. The Compose journey starts on truck edges `E1 + E3`, fails `E3`, verifies boat edges `E6 + E7`, checks ETA and visible latency, and switches languages without losing the route state. Current sub-millisecond emulator timing is development evidence only; the final under-two-second claim still needs the named target-phone report.

The integrated M6 tests consume those exact M4 route ETAs. The initial 65-minute truck route reaches the 120-minute P0 boundary under 30-percent slowdown without being classified late; the 200-minute boat fallback produces a 235-minute baseline and 295-minute stressed arrival, raises a proposal, and selects safe waypoint `N3`. Domain tests reject equal-priority transitions and a proposal with no safe waypoint. The ViewModel test proves a second confirmation tap is ignored while the first local write is pending. A connected Room test parses the persisted Protobuf event and verifies the reason code, policy, confirmer, affected cargo, waypoint, and estimated gain. The Compose journey confirms the proposal and switches languages without losing the result. Assignment-projection mutation, stale-input arbitration, and target-phone evidence remain release work.

The integrated M5 tests generate and parse the same Protobuf QR payload shown by the field UI. JVM tests verify RSA-2048-PSS signatures and trusted delivery, mission, sender, recipient, payload-hash, and timestamp fields, including tamper, wrong-delivery, and clock-window failures. Connected tests use Android Keystore identities and Room to prove atomic nonce claiming, replay rejection without a second custody event, sender and recipient signatures, previous-receipt hash linking, chain reconstruction, and the bounded ten-minute disconnected-clock tolerance. The Compose journey verifies that a second tap is shown as replay rejection while the verified chain remains unchanged. Current live captures cover Bangla and English acceptance, replay, and altered-field rejection. Camera scanning between two phones, unknown or expired credential rejection, and target-phone evidence remain release work.

The M7 pipeline generates 6,000 deterministic synthetic rows, uses separate training, validation, and held-out test splits, compares against a simple rule baseline, exports ONNX opset 17, and checks probability parity. The checked held-out result is precision 0.612766, recall 0.837209, and F1 0.707617; these values measure synthetic labels only. Android connected tests run the exact bundled model and the Compose journey shows simulated inputs, model/runtime identity, probability and threshold, predicted-risk map state, and a proactive boat reroute without adding a confirmed failed edge. JVM tests cover the explicit deterministic fallback. The local gate also builds the R8-minified release; a live release rehearsal found and fixed a Protobuf Lite field-name shrinking issue. On the API 36 ARM64 emulator, three post-inference readings peaked at 67,504 KB PSS with no explicit memory trim, below the 150 MB C3 threshold. The model card and memory report record hashes, confusion matrices, limitations, runtime compatibility, and measurement conditions. Target-phone latency/memory and real-data evaluation remain release work.

The integrated M8 tests classify air-only N7 as drone-required and reject an entirely unreachable graph. Candidate tests prove deterministic selection, changed-destination replanning, and rejection when every option violates the configured drone reserve. The workflow test advances exactly once through rendezvous, boat arrival, signed offer, simulated-drone arrival, local verification, and transfer. Connected tests parse the persisted `RendezvousPlanned` and `VehicleStateChanged` Protobuf events, then use Android Keystore and Room to prove that the boat and simulated-drone identities both sign the custody record. The Compose journey checks the simulation label and switches from English to Bangla without losing the transferred state. Live paired captures show the ready calculation and linked receipt. Delayed-boat replanning, process-death restoration of the in-progress UI phase, and physical-phone evidence remain.

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

- 1366 by 768 and 1920 by 1080 layouts, currently live-inspected with stored visual-regression baselines pending;
- keyboard navigation;
- reconnect and full projection rebuild; automated component coverage and a live local sequence 7 to 14 replay pass now exist;
- delayed and out-of-order observer events; the pure projection test applies deliberately unordered input;
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
| M8 | Reachability, reserve, event, and custody tests | Bilingual boat to simulated-drone handoff | No feasible rendezvous; delayed boat pending |

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
