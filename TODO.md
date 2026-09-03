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
- [ ] Add the complete Android dependency set. Compose, Material 3, Navigation 3, ZXing, Protobuf Lite, gRPC OkHttp, Hilt, Room, DataStore, Nearby Connections, and test dependencies are present; WorkManager, Tink, MapLibre, and ONNX remain.
- [x] Add a local Kotlin and Android verification runner; extend it for Go, TypeScript, and Protobuf compatibility as those projects land. Hosted CI and GitHub Actions are deliberately excluded.
- [ ] Add deterministic clocks, random seeds, and device IDs for demo scenarios.
- [x] Add a fixture validator for node and edge references through `SylhetMapParser` and the bundled-asset connected test.
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

- [x] Implement an append-only Protobuf operation log in SQLite for the current request and conflict paths.
- [x] Implement vector-clock comparison.
- [x] Select and document CRDT or merge behavior per field.
- [x] Use a grow-only set primitive for receipt and audit identifiers; production receipt projection wiring remains.
- [ ] Use an observed-remove set or explicit tombstones for assignments.
- [x] Implement a per-replica PN-counter that cannot lose concurrent stock changes; inventory projection wiring remains.
- [x] Route concurrent destination, priority, and medical-quantity conflicts to human review.
- [x] Add the persistent sync queue and bounded retry policy through the shared Room mesh outbox.
- [x] Add a policy-versioned SHA-256 convergence hash per mission projection.
- [x] Build the conflict screen in Bangla and English with vector clocks and explicit resolution.
- [ ] Test concurrent update, deletion, duplicate, late arrival, and clock-skew cases. Concurrent, duplicate/equal-clock safety, causal late arrival, and wall-clock disagreement are covered; deletion tombstones remain.

## M3 nearby mesh

- [x] Define the byte-oriented `PeerTransport` interface; transport connection-state reporting remains with the Nearby adapter.
- [x] Implement Nearby Connections using the cluster strategy; physical three-phone evidence remains.
- [x] Add Android 12 and newer Bluetooth and nearby-device permission handling, including Android 17 local-network policy.
- [x] Implement the active relay as an Android `connectedDevice` foreground service.
- [ ] Use WorkManager only for deferred retries, queue cleanup, and maintenance.
- [x] Implement neighbor advertising and discovery with an explicit human accept or reject step.
- [ ] Authenticate peers before accepting payloads. Nearby comparison digits are displayed, but signed application identity binding remains.
- [x] Build persistent Android inbox/outbox/seen-message state with bounded retry and dead-letter transitions; Go retains its durable Bolt inbox.
- [x] Implement the store-and-forward relay engine with atomic durable receipt and connect it to the Nearby byte transport.
- [x] Implement TTL and hop-limit enforcement in both Android and Go durable ingress.
- [x] Implement durable duplicate rejection before domain-event application in both Android and Go.
- [x] Encrypt payloads for the final recipient through the signed public-key directory using RSA-OAEP-wrapped AES-256-GCM; production instrumentation decrypts the persisted request with only the intended recipient key.
- [x] Keep relay-visible routing metadata outside recipient-only ciphertext; relays never receive a content decryption key.
- [ ] Select relay behavior using battery, signal, queue size, and proximity.
- [x] Reduce broadcast frequency by 60 percent below 30 percent battery.
- [ ] Display topology, queue depth, last contact, and relay reason. The live card currently shows relay state, peers, battery, broadcast interval, discovery, candidates, and errors.
- [x] Test interrupted transfer retry and Room close/reopen recovery; physical process-kill evidence remains.
- [ ] Test A to B to C with no commercial internet.

## M4 multi-modal routing

- [x] Parse and validate the Sylhet node and edge fixture from a bundled offline asset.
- [x] Normalize `river` to the documented waterway edge type.
- [x] Add a visibly simulated airway edge and test fixtures.
- [x] Implement a weighted directed graph.
- [x] Implement Dijkstra with deterministic edge and node tie-breaking.
- [x] Apply truck, boat, and simulated-drone constraints.
- [x] Exclude failed edges.
- [x] Apply M7 risk penalties without treating predictions as facts.
- [x] Recompute the active demo mission after the `E3` edge-failure event; generalized multi-mission observation remains.
- [x] Record computation latency and selected fallback policy in the live route state; persistent reports remain.
- [x] Render the real computed edge sequence using bundled offline scenario data and a Compose canvas.
- [ ] Embed MapLibre Native Android in Compose through an adapter.
- [ ] Package and verify the offline Sylhet map region and attribution.
- [x] Explain why the preferred truck path was selected or rejected and why boat precedes simulated air fallback.

## M5 proof of delivery

- [x] Generate QR images with ZXing Core.
- [ ] Scan with the bundled ML Kit barcode model and verify airplane-mode behavior.
- [x] Build the signed Protobuf QR payload with the required delivery, identity, hash, nonce, timestamp, and previous-receipt fields.
- [x] Verify the seeded sender key, signature, payload hash, nonce, timestamp, delivery, mission, and recipient offline. Cross-phone credential binding remains.
- [x] Add atomic Room nonce persistence and replay rejection.
- [x] Add bounded ten-minute field clock-skew handling with boundary tests.
- [ ] Add credential expiry and manual override policy. QR timestamp expiry is enforced; credential override is not.
- [x] Link each receipt to the previous custody receipt hash.
- [x] Display verifier result and exact rejection reason in Bangla and English.
- [x] Reconstruct and cryptographically verify the complete local receipt chain.
- [ ] Test altered QR fields, reused QR, unknown key, expired key, and wrong delivery. Altered fields, reuse, clock expiry, and wrong delivery pass; unknown and expired credential cases remain.

## M6 triage and priority preemption

- [x] Implement P0, P1, P2, and P3 policy data.
- [ ] Add SLA deadlines and countdowns. Fixed SLA deadlines are implemented; a live countdown remains.
- [x] Calculate baseline ETA and 30 percent slowdown ETA.
- [x] Flag predicted SLA breaches.
- [x] Define and test allowed transitions: only P0/P1 may preempt P2/P3; equal-priority and inverted transitions are rejected.
- [x] Find a safe waypoint for lower-priority cargo.
- [ ] Require confirmation for a real assignment change. Human confirmation and a durable decision event are implemented; generalized assignment projection mutation remains.
- [x] Record the reason, policy version, confirmer, affected cargo, waypoint, and estimated gain in Protobuf.
- [x] Render the decision explanation in Bangla and English.
- [ ] Test equal priorities, missing waypoint, stale ETA, and simultaneous P0 requests. Equal-priority and missing-waypoint cases pass; stale ETA and simultaneous P0 arbitration remain.

## M7 predictive route decay

- [x] Define training data provenance and licensing.
- [x] Build the synthetic scenario generator because no governed real labelled dataset is available.
- [x] Label all synthetic data in reports and screens.
- [x] Engineer rainfall, elevation, and saturation features.
- [x] Create training, validation, and held-out test splits.
- [x] Establish a non-ML baseline.
- [x] Train and select the smallest defensible classifier.
- [x] Report precision, recall, F1, confusion matrix, and threshold.
- [x] Export and validate the ONNX model.
- [x] Run inference through ONNX Runtime Android on the field emulator; target-phone measurement remains.
- [x] Add risk overlay and explanation panel.
- [x] Feed risk penalties into M4.
- [x] Write the model card with failure modes and safety limits.

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
