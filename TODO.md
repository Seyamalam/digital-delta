# Build checklist

This checklist tracks implementation. Milestone exit criteria live in [MILESTONES.md](MILESTONES.md). Requirement identifiers map to [docs/TRACEABILITY.md](docs/TRACEABILITY.md).

## Documentation and decisions

- [x] Record Bangla and English as equal product interfaces.
- [x] Record the no-external-hardware boundary.
- [x] Define module-level live proofs.
- [x] Create milestone, screenshot, demo, security, testing, and traceability plans.
- [ ] Confirm the fair participation status, date, booth rules, and pitch duration.
- [ ] Record available Android phones, operating-system versions, laptops, and projector resolution.
- [x] Confirm the implementation stack in `docs/DECISIONS.md`.
- [ ] Decide whether strict HackFusion gRPC transport compliance is a target for this fair build.
- [ ] Define measurable targets for latency, memory, battery, relay success, and load.

## Repository foundation

- [ ] Create the monorepo folders described in `README.md`.
- [ ] Add root setup, format, lint, test, seed, reset, and demo commands.
- [ ] Add environment example files with no secrets.
- [x] Scaffold the Android project with Gradle Kotlin DSL and a version catalog.
- [ ] Add the complete Android dependency set. Compose, Material 3, Navigation 3, ZXing, Protobuf Lite, gRPC OkHttp, and test dependencies are present; Hilt, Room, DataStore, WorkManager, Nearby, Tink, MapLibre, and ONNX remain.
- [x] Add a local Kotlin and Android verification runner; extend it for Go, TypeScript, and Protobuf compatibility as those projects land. Hosted CI and GitHub Actions are deliberately excluded.
- [ ] Add deterministic clocks, random seeds, and device IDs for demo scenarios.
- [ ] Add a fixture validator for node and edge references.
- [ ] Correct the supplied chaos-server formatting and keep it as a development fixture only.
- [ ] Add structured logs with event ID, node ID, mission ID, and correlation ID.
- [ ] Add feature flags for each unfinished module.

## Protocol and domain contracts

- [x] Define `Envelope`, `VectorClock`, `Signature`, `EncryptedPayload`, and `Acknowledgement` messages.
- [ ] Define identity, request, cargo, route, vehicle, handoff, receipt, prediction, and audit events. Enrollment and signed identity-credential contracts plus request, cargo, route, handoff, prediction, conflict, SLA, and preemption events exist; vehicle and audit events remain.
- [x] Add schema version and minimum reader version.
- [x] Add TTL, hop count, creation time, sender, recipient, payload hash, and nonce fields.
- [ ] Generate Kotlin/Java Lite gRPC and Go clients. TypeScript generation remains.
- [ ] Add backward-compatibility tests for stored fixtures.
- [x] Ban JSON serialization from the mesh package through the local verification script.
- [ ] Document gRPC links and framed-Protobuf links separately.

## Bangla and English foundation

- [x] Bundle Bangla and English strings into the app.
- [x] Add `values/strings.xml`, `values-bn/strings.xml`, and generated locale configuration.
- [ ] Add a first-run language chooser with `বাংলা` first and `English` second.
- [x] Persist the selected language in Proto DataStore.
- [ ] Add Noto Sans Bengali or another tested Bengali font with an offline license file.
- [ ] Create the glossary in `packages/localization/glossary.csv`.
- [ ] Add translation-key completeness tests.
- [ ] Add tests that reject raw user-facing strings in critical field screens.
- [ ] Test Bengali combining marks, wrapping, truncation, and large text.
- [x] Keep P0 to P3, coordinates, cryptographic fingerprints, and delivery IDs language-neutral.
- [ ] Provide bilingual status text when a term could affect safety.
- [x] Add accessible labels in both languages for the implemented field surfaces; continue auditing new screens.
- [x] Verify that no language change clears the implemented request and identity state.

## M1 secure identity

- [x] Generate device-bound RSA-2048 encryption and signing identities, the accepted C5 alternative to Ed25519.
- [x] Keep private identity keys non-exportable in Android Keystore. Hardware-backed availability still requires target-phone evidence.
- [ ] Implement offline administrator provisioning QR. Signed enrollment, administrator trust pinning, credential issue/verify, expiry checks, durable storage, and the bilingual display/paste journey are complete; bundled camera scanning remains.
- [ ] Add local PIN or device-authentication unlock.
- [ ] Implement roles and permission policy.
- [ ] Hide forbidden actions and enforce the same policy below the user interface.
- [ ] Add signed audit events.
- [ ] Add failed-login delay and lockout policy suitable for offline operation.
- [ ] Add key revocation and expiry events that propagate later.
- [ ] Test valid, expired, revoked, malformed, and wrong-role credentials. Valid, expired, tampered, and untrusted-issuer cases pass; revocation and role-specific provisioning cases remain.

## M2 distributed data and CRDT sync

- [ ] Implement an append-only operation log in SQLite.
- [ ] Implement vector-clock comparison.
- [ ] Select CRDT or merge behavior per field.
- [ ] Use grow-only sets for receipt and audit identifiers.
- [ ] Use an observed-remove set or explicit tombstones for assignments.
- [ ] Use counters that cannot lose concurrent stock changes.
- [ ] Route safety-sensitive conflicts to human review.
- [ ] Add sync queue and retry policy.
- [ ] Add convergence hash per mission projection.
- [ ] Build the conflict screen in Bangla and English.
- [ ] Test concurrent update, deletion, duplicate, late arrival, and clock-skew cases.

## M3 nearby mesh

- [ ] Define the `PeerTransport` interface and transport-neutral connection state.
- [ ] Implement Nearby Connections using the cluster strategy.
- [ ] Add Android 12 and newer Bluetooth and nearby-device permission handling.
- [ ] Implement the active relay as an Android foreground service.
- [ ] Use WorkManager only for deferred retries, queue cleanup, and maintenance.
- [ ] Implement neighbor advertising and discovery.
- [ ] Authenticate peers before accepting payloads.
- [ ] Build persistent inbox, outbox, retry, and dead-letter queues. Android has an atomic Room operation/outbox write and Go has a durable inbox; retry and dead-letter behavior remain.
- [ ] Implement store-and-forward relay.
- [ ] Implement TTL and hop-limit enforcement. The Go durable relay enforces both; Android queue enforcement remains.
- [ ] Implement deduplication before domain-event application. The Go durable relay rejects duplicate message IDs; Android persistence remains.
- [x] Encrypt payloads for the final recipient through the signed public-key directory using RSA-OAEP-wrapped AES-256-GCM; production instrumentation decrypts the persisted request with only the intended recipient key.
- [ ] Allow relays to inspect routing metadata only.
- [ ] Select relay behavior using battery, signal, queue size, and proximity.
- [ ] Reduce broadcast frequency by 60 percent below 30 percent battery.
- [ ] Display topology, queue depth, last contact, and relay reason.
- [ ] Test interrupted transfer and app restart.
- [ ] Test A to B to C with no commercial internet.

## M4 multi-modal routing

- [ ] Parse and validate the Sylhet node and edge fixture.
- [ ] Normalize `river` to the documented waterway edge type.
- [ ] Add airway edges and test fixtures.
- [ ] Implement a weighted directed graph.
- [ ] Implement Dijkstra or A* with deterministic tie-breaking.
- [ ] Apply truck, boat, and drone constraints.
- [ ] Exclude failed edges.
- [ ] Apply M7 risk penalties without treating predictions as facts.
- [ ] Recompute active missions after relevant graph events.
- [ ] Record computation latency and selected alternatives.
- [ ] Render routes using bundled offline data.
- [ ] Embed MapLibre Native Android in Compose through an adapter.
- [ ] Package and verify the offline Sylhet map region and attribution.
- [ ] Explain why a route was selected or rejected.

## M5 proof of delivery

- [ ] Generate QR images with ZXing Core.
- [ ] Scan with the bundled ML Kit barcode model and verify airplane-mode behavior.
- [ ] Build the signed QR payload.
- [ ] Verify sender identity, signature, payload hash, nonce, timestamp, and delivery state.
- [ ] Add nonce persistence and replay rejection.
- [ ] Add bounded clock-skew handling.
- [ ] Add expiry and manual override policy.
- [ ] Link each receipt to the previous custody event.
- [ ] Display verifier result and exact rejection reason.
- [ ] Reconstruct the complete receipt chain.
- [ ] Test altered QR fields, reused QR, unknown key, expired key, and wrong delivery.

## M6 triage and priority preemption

- [ ] Implement P0, P1, P2, and P3 policy data.
- [ ] Add SLA deadlines and countdowns.
- [ ] Calculate baseline ETA and 30 percent slowdown ETA.
- [ ] Flag predicted SLA breaches.
- [ ] Define allowed preemption transitions.
- [ ] Find a safe waypoint for lower-priority cargo.
- [ ] Require confirmation for a real assignment change.
- [ ] Record the reason, policy version, confirmer, and affected cargo.
- [ ] Render the decision explanation in Bangla and English.
- [ ] Test equal priorities, missing waypoint, stale ETA, and simultaneous P0 requests.

## M7 predictive route decay

- [ ] Define training data provenance and licensing.
- [ ] Build the synthetic scenario generator only if real labelled data is unavailable.
- [ ] Label all synthetic data in reports and screens.
- [ ] Engineer rainfall, elevation, and saturation features.
- [ ] Create training, validation, and held-out test splits.
- [ ] Establish a non-ML baseline.
- [ ] Train and select the smallest defensible classifier.
- [ ] Report precision, recall, F1, confusion matrix, and threshold.
- [ ] Export and validate the ONNX model.
- [ ] Run inference through ONNX Runtime Android on the field device.
- [ ] Add risk overlay and explanation panel.
- [ ] Feed risk penalties into M4.
- [ ] Write the model card with failure modes and safety limits.

## M8 hybrid fleet and simulated drone handoff

- [ ] Classify destinations by road, water, and air reachability.
- [ ] Mark destinations with no valid ground or water path as drone-required.
- [ ] Define boat and simulated-drone speed and battery assumptions.
- [ ] Compute candidate rendezvous points.
- [ ] Minimize combined or maximum arrival time according to the documented objective.
- [ ] Explain the chosen rendezvous.
- [ ] Simulate boat and drone arrival events.
- [ ] Reuse M5 for signed custody transfer.
- [ ] Display simulated vehicle state clearly.
- [ ] Test no feasible rendezvous, low battery, late boat, and changed destination.

## Command dashboard and Disaster Control

- [ ] Build projector layout for 1366 by 768 and 1920 by 1080.
- [ ] Add route map, inventory, node status, mesh queue, risk, and custody panels.
- [ ] Add Offline, Syncing, Conflict, and Verified states.
- [ ] Add Bangla and English dashboard modes.
- [ ] Add simulated rainfall, saturation, edge failure, node failure, delay, and battery controls.
- [ ] Add duplicate-message and QR-tamper injection.
- [ ] Add event narration and module evidence panel.
- [ ] Add scenario pause, resume, step, reset, and replay.
- [ ] Make every control deterministic under a scenario seed.
- [ ] Prove field phones continue after dashboard disconnection.

## Testing, evidence, and submission

- [ ] Create module unit tests and contract fixtures.
- [ ] Create integration tests for the full event lifecycle.
- [ ] Create a three-phone manual test sheet.
- [ ] Run fault-injection tests.
- [ ] Run 10,000 simulated connections against the Go service and record the conditions.
- [ ] Measure route recomputation on target hardware.
- [ ] Measure field-app RAM on target hardware.
- [ ] Measure relay recovery and duplicate rejection.
- [ ] Complete the bilingual and accessibility matrices.
- [ ] Capture every required item in `SCREENSHOTS.md`.
- [ ] Rehearse the ten-minute script twenty times.
- [ ] Record three unchanged successful final passes.
- [ ] Create the architecture diagram, model card, deck, and backup video.
- [ ] Audit every claim for evidence or a visible simulation label.
