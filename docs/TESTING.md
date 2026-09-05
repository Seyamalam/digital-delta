# Testing and evidence strategy

## Fair phone acceptance target

The exhibitor confirmed standard **4 GB RAM Android phones** as the target on
September 5. Exact models, Android versions and available storage remain to be
recorded when devices are connected; do not substitute an emulator model for them.
Keep the existing under-150 MB mobile processing budget and under-two-second route
recomputation target. Total installed RAM is not an app memory measurement.

The physical gate requires three independently provisioned phones for A→B→C
relay/recovery, camera provisioning and signed handoffs, with commercial internet
disabled and the command laptop disconnected during field operations. Record
model/OS, build hash, PSS readings, route latency, battery conditions and results
for three unchanged offline demo passes. Until these runs exist, physical radio,
camera and target-phone performance acceptance remain unverified.

## Custody checkpoint gate — 2026-09-05

### Later request-location gate

The full local connected gate passed **104 JVM tests, 79 connected Android tests,
27 dashboard tests and 12 Worker tests**, plus debug/minified release, Go,
Protobuf/localization/map/model and web build checks. The Android target was
Pixel_10_Pro_XL / Android 16. Regression tests cover non-default pickup in the
encrypted production request, graph membership validation before persistence,
bilingual picker selection retention, all map-node profile identities/roles, and
future/exactly-expired triage estimates. See the [request-location checkpoint](../artifacts/reports/code-review/2026-09-05-request-locations-checkpoint.md)
and [local gate log](../artifacts/reports/verification/2026-09-05-request-locations-local.log).

`MainScreenTest#requestLocationPickersKeepSelectionsWhenLanguageChanges` accepts
`captureRequestEvidence=true` and optional `qaDarkMode=true`. This opt-in path
saves settled screenshots of its coordinator UI fixture under the test target's
external files directory. It does not provision production authority or certify
physical phones. Normal local verification leaves screenshot capture disabled.

### Later mission-headquarters gate

After the custody checkpoint, the full local connected gate passed **102 JVM tests,
78 connected Android tests, 27 dashboard tests and 12 Worker tests** on the
Pixel_10_Pro_XL AVD running Android 16. Debug/minified release assembly, Go
race/vet/build, Protobuf/localization/map/model checks, Next.js production build and
Wrangler dry run passed too. See the [mission checkpoint](../artifacts/reports/code-review/2026-09-05-mission-headquarters-checkpoint.md)
and [gate log](../artifacts/reports/verification/2026-09-05-mission-headquarters-local.log).

Regression coverage includes mission isolation, out-of-order route/evaluation
publication, publisher/route binding, warning recovery, no-route and same-node
journeys, stale/future timestamps, selection through navigation and reset, and the
fresh-event clock-tick regression found in the live browser check. The connected
test also verifies durable Android publication of all three SLA outcomes after an
offline/restart cycle. Physical-device acceptance remains separate.

The follow-up local gate passed **102 JVM tests, 78 connected Android tests,
21 dashboard tests and 11 Worker tests**, with debug/minified release assembly,
fresh Go race/vet/build checks, schema/localization/map/model checks, Next.js build
and Wrangler dry run. The connected target was emulator-5554, Android 15 / API 35.
The added tests cover multi-leg assigned custody with crossing-edit reconciliation,
historical public-key rotation/revocation, and the Room 8 → 9 migration.

`ProductionRequestFlowTest` optionally accepts `holdMissionForVisualQa=true` and
`visualQaLanguage=en` for a bounded 45-second inspection window. This window is off
in the full gate. Argent development captures show the actual Room-backed mission
in both languages; coordinator-dialog and physical-phone acceptance remain open.
After fixing authority-refresh loss of the audit label, the English 45-second
inspection variant passed its remaining authorization/revocation assertions too.
See the [custody checkpoint](../artifacts/reports/code-review/2026-09-05-custody-checkpoint.md).

## Earlier combined remediation gate — 2026-09-05

`ANDROID_SERIAL=emulator-5554 scripts/verify-local.sh --connected` passed after the
final recorder, custody-version and test-isolation corrections: **102 Android JVM
tests, 75 connected Android tests, 21 dashboard tests and 11 Worker tests**. The gate
also passed debug/minified release assembly, schema/localization/map checks,
reproducible model export, Go race/vet/build, Next.js typecheck/build and Wrangler
dry run. Go results in this final gate were cached; an earlier same-turn gate ran
the changed Go packages freshly. No hosted deployment was performed.

The six independent-field regressions now cover three-writer convergence, receipt
return to the origin, receipt-before-prerequisite retry, historical custody despite
a crossing edit, unauthorized dual-signer rejection, known revoked peer proof and
recorded route/SLA publication recovery. E5 is computed from an accepted N1→N6
mission; P0 produces a breach warning, P3 does not, and N7 explicitly has no truck
or boat route. Timings stay labelled as packaged simulation.

The final combined gate does not contain the temporary HTTP publication token.
Actual emulator→Hono/D1→browser evidence was collected separately earlier in this
turn, then the ignored debug asset was removed. See the
[remediation review](../artifacts/reports/code-review/2026-09-05-remediation-review.md)
and [fresh reduced load result](../artifacts/reports/load/2026-09-05-reduced-go-10000.md).
Historical counts below are not additive and do not certify physical devices.

## Independent field workflow regressions

`IndependentFieldWorkflowTest` uses three separate Room files and disjoint Keystore
namespaces. It exercises signed A→B→C forwarding with A still connected, relay and
recipient restart, duplicate admission, accepted medical quantity, independent edits,
coordinator resolution, equal convergence hashes, and provisioned cross-identity PoD
with tamper/replay rejection. This is protocol/storage evidence, not physical-radio QA.

Its publication recovery test always exercises offline retry and restart with a
controlled transport. If the ignored debug asset `observer-local-evidence.json`
is supplied, it additionally sends the actual phone-created, visibly simulated
request to a local Hono/D1 instance and checks the returned event ID exactly once.
The asset contains `{endpoint, sourceNodeId, token}` and must never be committed or
distributed. Only debug permits `http://10.0.2.2`; release requires HTTPS.

An earlier September 5 remediation checkpoint passed 70 connected tests on emulator-5554 with
zero failures/skips, including that actual HTTP extension against local port 7073.
That checkpoint did not prove receipt return; the later 75-test run above does at
the independent-replica layer. Neither run proves a physical camera scan, generalized
driver assignment, target-phone memory, or three unchanged venue rehearsals.

## September 5 migration checkpoint

The current observer is Hono/Workers with D1, not the legacy Go observer. Its local
runtime tests cover publisher authentication/source binding, D1 sequencing and
replay, event-ID collisions, SSE cancellation, exact CORS and actual request-byte
limits. The local gate passed after the migration and dashboard routing redesign:
19 dashboard tests and 9 Worker tests, plus Android builds/unit tests, Go
race/vet/build checks, schema, model and map checks. Connected Android checks for
the current hardening are recorded separately; older counts below are dated
baselines, not current certification. See [OBSERVER.md](OBSERVER.md).

The subsequent final run passed **100 Android JVM tests and 65 connected tests**
on the explicitly selected API 35 emulator. Fixes, earlier failures and interactive
verification limits are recorded in
[the refresh report](../artifacts/reports/2026-09-05-hq-field-refresh.md).

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

The local runner lints Protobuf schemas, rejects JSON references in mesh packages, verifies the checksum-pinned PMTiles archive, its deterministic Android geographic extract, and the shared OSM-following route geometry, reproduces and byte-compares the synthetic dataset, metrics, configuration, Android assets, and ONNX model, runs Android tests and APK assembly, runs Go race tests, vet, and all Go command builds, and then tests, typechecks, and production-builds the Next.js command dashboard. It also tests and typechecks the optional Cloudflare archive and performs a Wrangler deployment dry run. The Android suite tests the public domain seams for authorization, vector clocks, relay policy, routing, signed handoff verification, triage, route-risk classification and fallback, fleet rendezvous, hybrid recipient encryption, signed provisioning credentials and revocations, enrollment requests, signed mesh acknowledgements, binary Protobuf envelope compatibility, Protobuf-only Nearby frames, and runtime permission selection from Android 9 through Android 17. All 61 connected tests passed on both the Android 15 Mento and Android 16 Pixel emulators on 2026-09-04. They cover signed local-credential binding and wrong-role rejection, RSA-PSS authorization-audit hash chaining and tamper rejection, signed revocation application, recipient-specific encrypted forwarding, idempotent receiver processing, altered-ciphertext rejection, WorkManager scheduling, Room 4→5 migration preservation, bilingual role-denial UI, the persistent first-run language choice, salted offline PIN creation and lockout, language parity, 150 percent text scaling, mid-flow state preservation, offline request queuing, replay rejection, non-exportable Android Keystore identity behavior, durable credential and device-profile persistence, production enrollment and scanner entry points, atomic mesh ingress, duplicate/TTL/hop rejection, interrupted-send retry, forged-ack retry, dead letters, database restart recovery, adaptive relay policy, bilingual relay-control state, bundled ONNX execution, the bilingual risk-to-reroute journey, the packaged drone-required graph, delayed-boat replanning, hybrid-fleet Room events, custom boat-to-drone custody, packaged-map and route-geometry provenance, native MapLibre rendering, and the bilingual M8 journey. `ProductionRequestFlowTest` provisions both the real N4 app identity and N6 recipient through the production Room directory, passes through the production language and PIN gates, completes the visible request journey through the Hilt graph, displays the signed audit ID, verifies its full local chain, parses the persisted Protobuf envelope, decrypts it with the intended N6 private key, imports an administrator-signed N4 revocation through the visible identity form, and verifies immediate local authorization withdrawal. JVM security tests verify exact acknowledgement and revocation signatures and reject mutation, missing signatures, wrong nodes, stale clocks, unknown identities, and revoked credentials; connected tests bind the same checks to the actual non-exportable Keystore key. Go integration tests exercise durable mesh restart recovery, signed enrollment and revocation issuance/verification, bidirectional gRPC acknowledgements, ordered observer persistence, idempotent publication, cursor replay, SSE sanitization, and deterministic drill labelling. Command-app tests cover Bangla-first content parity, deterministic scenario completion, labelled rainfall and soil-saturation control, proactive route staging, observer-disconnect messaging, reset behavior, syncing and security rejections, relay loss and vehicle delay, automatic replay pause, observer connection/cursor behavior, reconstruction of route, hazard, rendezvous and vehicle state, local-only map sources, multi-point OSM road and waterway geometry, direct simulated airways, viewport coverage, and sanitized archive behavior. Three archive tests reject sensitive, malformed, and extra fields before D1 insertion.

The full connected gate was repeated after the assignment-tombstone and observer-disconnect additions at commit `73dd13c`: both emulator suites again passed 42/42 with zero skipped or failed tests. The dated baseline and fault matrix are stored under `artifacts/reports/unit-tests/` and `artifacts/reports/fault-injection/`.

The live Android 15 emulator check starts the real Nearby `CLUSTER` adapter in a `connectedDevice` foreground service, grants the app-requested nearby and notification permissions, verifies advertise/discover state and battery cadence in Bangla and English, and stops the service through the field UI. This proves the single-device lifecycle only. It does not replace the required A to B to C transfer on three physical phones. A Go race test separately closes the dashboard SSE client, publishes two events while no browser is present, and proves ordered cursor replay on reconnection; conditions and limits are recorded in `artifacts/reports/fault-injection/2026-09-04-dashboard-disconnect.md`.

Peer-authentication JVM tests issue a real administrator-signed credential, answer a fresh Protobuf challenge with RSA-2048-PSS, and verify the complete transcript. Separate cases reject changed claims, unknown signing keys, expired challenges, and a consumed challenge replay. The Nearby adapter keeps transport-accepted endpoints in an `Authenticating` set, promotes them only after the expected proof, and rejects pre-authentication envelopes and acknowledgements. The Compose journey compiles bilingual authenticating and verified-key states. Actual radio exchange, hostile-phone rejection, and reconnection replay still require the physical multi-phone pass.

Profile tests enforce a closed catalog with unique node and identity IDs and explicit clinic, hospital, and driver roles. Unknown explicit profile input is rejected, while an empty or old persisted value migrates to the N4 default. ViewModel tests prove switching to the hospital profile regenerates N6 identity state, and the production Compose test compiles all three profile controls. The foreground relay resolves the persisted profile before constructing its Nearby identity, ingress, peer proof signer, and acknowledgement signer.

The M2 tests compare causal, equal, and concurrent vector clocks; prove deterministic safe-field convergence in both arrival orders; reject automatic resolution of destination, priority, and medical-quantity disagreement; and merge grow-only sets, per-replica PN-counters, and operation-tagged observed-remove assignment sets. Assignment cases prove idempotent duplicates, late-arriving observed additions cannot resurrect deleted values, and truly concurrent unseen reassignment survives. A connected Room test raises a simulated destination conflict, closes and reopens the database, resolves the restored conflict, and verifies the operation count, selected projection, and 64-character convergence hash. The Compose journey proves both languages and the explicit human choice. Physical two-phone convergence and production assignment-projection wiring remain release evidence.

The M4 unit tests parse the JSON fixture, validate graph references, normalize `river` to `WATERWAY`, enforce truck/boat/drone edge constraints, exclude failed edges, and inject a monotonic clock to verify reported recomputation latency. The actual bundled asset is parsed in a connected test so a stale or malformed packaged fixture fails the local gate. The Compose journey starts on truck edges `E1 + E3`, fails `E3`, verifies boat edges `E6 + E7`, checks ETA and visible latency, and switches languages without losing the route state. Current sub-millisecond emulator timing is development evidence only; the final under-two-second claim still needs the named target-phone report.

The integrated M6 tests consume those exact M4 route ETAs. The initial 65-minute truck route reaches the 120-minute P0 boundary under 30-percent slowdown without being classified late; the 200-minute boat fallback produces a 235-minute baseline and 295-minute stressed arrival, raises a proposal, and selects safe waypoint `N3`. The decision exposes the remaining P0 window and the Compose card renders it as a live bilingual countdown. Domain tests reject equal-priority transitions and a proposal with no safe waypoint. They also prove that an ETA older than five minutes cannot authorize either a proposal or its later confirmation, and that simultaneous P0 requests are deterministically ranked while the non-selected requests remain urgent and queued. The ViewModel test proves a second confirmation tap is ignored while the first local write is pending. Connected Room tests parse the persisted Protobuf event, verify the reason code, `triage-v2` policy, confirmer, affected cargo, waypoint, and estimated gain, then verify the deposited assignment's vector clock and convergence hash. A duplicate-event fault proves the event and assignment are atomic, while the v5-to-v6 migration preserves older operations. The Compose journey visibly retains the competing P0, confirms the local assignment update in both languages, and switches languages without losing the result. Signed operational-event envelopes and target-phone evidence remain release work.

The integrated M5 tests generate and parse the same Protobuf QR payload shown by the field UI. JVM tests verify RSA-2048-PSS signatures and trusted delivery, mission, sender, recipient, payload-hash, and timestamp fields, including tamper, wrong-delivery, and clock-window failures. Connected tests use Android Keystore identities and Room to prove atomic nonce claiming, replay rejection without a second custody event, sender and recipient signatures, previous-receipt hash linking, chain reconstruction, and the bounded ten-minute disconnected-clock tolerance. The Compose journey verifies that a second tap is shown as replay rejection while the verified chain remains unchanged. Camera capture uses a CameraX `LifecycleCameraController` and bundled ML Kit QR model; JVM tests accept only the expected trust, credential, or PoD prefix, connected tests compile the three scanner entry points, and the local gate inspects the assembled APK for the barcode `.tflite` assets. Current live captures cover Bangla and English acceptance, replay, and altered-field rejection. Camera scanning between two phones in airplane mode, unknown or expired credential rejection, and target-phone evidence remain release work.

The M7 pipeline generates 6,000 deterministic synthetic rows, uses separate training, validation, and held-out test splits, compares against a simple rule baseline, exports ONNX opset 17, and checks probability parity. The checked held-out result is precision 0.612766, recall 0.837209, and F1 0.707617; these values measure synthetic labels only. Android connected tests run the exact bundled model and the Compose journey shows simulated inputs, model/runtime identity, probability and threshold, predicted-risk map state, and a proactive boat reroute without adding a confirmed failed edge. JVM tests cover the explicit deterministic fallback. The local gate also builds the R8-minified release; a live release rehearsal found and fixed a Protobuf Lite field-name shrinking issue. On the API 36 ARM64 emulator, three post-inference readings peaked at 67,504 KB PSS with no explicit memory trim, below the 150 MB C3 threshold. The model card and memory report record hashes, confusion matrices, limitations, runtime compatibility, and measurement conditions. Target-phone latency/memory and real-data evaluation remain release work.

The integrated M8 tests classify air-only N7 as drone-required and reject an entirely unreachable graph. Candidate tests prove deterministic selection, changed-destination replanning, delayed-boat position replanning from R3 to R2, and rejection when every option violates the configured drone reserve. The workflow test records the delay and new rendezvous before advancing exactly once through boat arrival, signed offer, simulated-drone arrival, local verification, and transfer. Connected tests parse persisted `RendezvousPlanned` and `VehicleStateChanged` Protobuf events, including the simulated delayed-vehicle position, then use Android Keystore and Room to prove that the boat and simulated-drone identities both sign the custody record. Both the complete bilingual handoff and delayed-boat R3-to-R2 journeys pass on Android 15 and 16 emulators. Live paired captures show the ready calculation and linked receipt; the replanned-state capture, process-death restoration of the in-progress UI phase, and physical-phone evidence remain.

## Test layers

### Go connection load

`scripts/load-test-10000.sh` builds an isolated server and client, creates fresh temporary Bolt stores, opens one bidirectional gRPC stream per simulated node, sends one unique Protobuf envelope, requires its durable acknowledgement, and holds every acknowledged stream simultaneously. A 2026-09-04 local run passed 10,000/10,000 streams with a five-second full-concurrency hold and no error-level server log entry. The measured p95 acknowledgement latency was 57.645 seconds and peak server RSS was approximately 1.19 GiB, so this is connection-capacity evidence rather than a production latency claim. Full conditions and limits are in `artifacts/reports/load/2026-09-04-go-10000.md`; the ordinary Go test suite runs the same harness at 64 connections.

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

- 1366 by 768 and 1920 by 1080 layouts, stored as live-observer PNG evidence in Bangla and English;
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
| M8 | Reachability, reserve, delay/replan, event, and custody tests | Bilingual boat-to-simulated-drone handoff and delayed-boat journey on Android 15/16 emulators | No feasible rendezvous; persistent delayed-state capture pending |

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
