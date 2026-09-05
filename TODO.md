# Build checklist

This checklist tracks implementation. Milestone exit criteria live in [MILESTONES.md](MILESTONES.md). Requirement identifiers map to [docs/TRACEABILITY.md](docs/TRACEABILITY.md).

The earlier 90.4 percent estimate is withdrawn: checklist coverage was not end-to-end
acceptance. All eight module areas remain in scope, but both software integration and
physical evidence remain. Older checked items describe their implementation slice,
not a certified release. The current evidence and finding matrix are in
[the September 5 remediation review](artifacts/reports/code-review/2026-09-05-remediation-review.md).

## Remediation and remaining integration

- [x] Apply authenticated received requests and replicated mission edits/resolutions to Room.
- [x] Test three independent writers, delivery permutations, relay restart and duplicate handling.
- [x] Use provisioned origin/recipient identities for operational custody and replicate signed receipts.
- [x] Pin receipt mission versions, retry missing revisions, and flag concurrent post-delivery edits.
- [x] Add durable optional Android request/plan publication with source-bound Hono authentication.
- [x] Separate observer and exercise state; retain projections and reset across stream generations.
- [x] Bound QR generation with a bilingual full-code fallback.
- [x] Implement signed pre-handoff driver paths and retained-cargo reconciliation, with independent-replica tests.
- [ ] Verify the coordinator assignment/reconciliation dialogs live in both languages and support post-handoff reassignment recovery.
- [ ] Connect received missions to operational fleet/preemption confirmation, not only local exercises.
- [ ] Add general origin/destination selection, validated against the offline graph.
- [x] Add mission-indexed routes/SLA evaluations and persistent headquarters selection, with regression tests and local D1/SSE browser checks in both languages. See the [mission checkpoint](artifacts/reports/code-review/2026-09-05-mission-headquarters-checkpoint.md). This is read-only planning, not fleet dispatch acceptance.
- [x] Verify retained public-key history against existing multi-leg receipts after credential rotation in independent-replica tests.
- [ ] Verify physical device replacement and encrypted backlog recovery; old private keys are not recoverable from the public archive.
- [ ] Reconcile final pitch/report claims and screenshots with the newly integrated build.

## Documentation and decisions

- [x] Record Bangla and English as equal product interfaces.
- [x] Record the no-external-hardware boundary.
- [x] Define module-level live proofs.
- [x] Create milestone, screenshot, demo, security, testing, and traceability plans.
- [ ] Confirm Innovation Exhibitor acceptance, selected package, payment status, stall allocation, event date, booth rules, and any pitch duration. Touhidul Alam Seyam registered as an Innovation Exhibitor under BGC Trust University Bangladesh. The official site review is recorded in `docs/FAIR_SUBMISSION.md`, but organizer logistics still require the receipt or direct confirmation.
- [ ] Record available Android phones, operating-system versions, laptops, and projector resolution.
- [x] Record standard 4 GB RAM Android phones as the target; exact models/OS and measured performance remain pending.
- [x] Confirm the implementation stack in `docs/DECISIONS.md`.
- [x] Decide whether strict HackFusion gRPC transport compliance is a target for this fair build. DD-006 records gRPC for supported IP links and framed Protobuf for Nearby instead of making a false transport claim.
- [x] Define measurable targets for latency, memory, battery, relay success, and load in `docs/ACCEPTANCE_TARGETS.md`.

## Repository foundation

- [x] Create the monorepo folders described in `README.md`.
- [x] Add root setup, format, lint, test, seed, recoverable reset, and demo commands through the Makefile and local scripts.
- [x] Add an environment example with the local observer URL and no secrets.
- [x] Scaffold the Android project with Gradle Kotlin DSL and a version catalog.
- [x] Add the complete Android dependency set. Compose, Material 3, Navigation 3, ZXing, Protobuf Lite, gRPC OkHttp, Hilt, Room, DataStore, Nearby Connections, WorkManager, ONNX Runtime, CameraX, bundled ML Kit, MapLibre Native, and test dependencies are present. Direct audited JCA primitives replace Tink.
- [x] Add a local Kotlin and Android verification runner; extend it for Go, TypeScript, and Protobuf compatibility as those projects land. Hosted CI and GitHub Actions are deliberately excluded.
- [x] Add deterministic clocks, random seeds, and device IDs for demo scenarios. The fixed drill, scenario reducer, injectable route clock, stable event IDs, and test fixtures cover the demo paths.
- [x] Add a fixture validator for node and edge references through `SylhetMapParser` and the bundled-asset connected test.
- [x] Correct the supplied chaos-server formatting and keep it as a visibly simulated development fixture only under `packages/scenario/`.
- [x] Add structured logs with event ID, node ID, mission ID, and correlation ID. The observer logs visible domain identifiers; mesh logs mark event and mission contents as encrypted instead of opening payloads.
- [x] Decide feature-flag policy for unfinished work. DD-019 keeps all eight implemented modules visible and uses explicit evidence labels and release gates instead of hiding working paths behind flags.

## Protocol and domain contracts

- [x] Define `Envelope`, `VectorClock`, `Signature`, `EncryptedPayload`, and `Acknowledgement` messages.
- [x] Define identity, request, cargo, route, vehicle, handoff, receipt, prediction, and signed authorization-audit events in Protobuf.
- [x] Add schema version and minimum reader version.
- [x] Add TTL, hop count, creation time, sender, recipient, payload hash, and nonce fields.
- [x] Generate Kotlin/Java Lite, Go, and TypeScript Protobuf bindings. The Next.js package owns the local `protoc-gen-es` tool and generated v2 descriptors are checked in under `apps/command/src/gen`.
- [x] Add backward-compatibility tests for stored fixtures. The local gate decodes a checked-in schema-version-1 binary Envelope fixture with the current contract and verifies its stable identity and recipient fields.
- [x] Ban JSON serialization from the mesh package through the local verification script.
- [x] Document gRPC links and framed-Protobuf links separately in the README, architecture, stack, and DD-006.

## Bangla and English foundation

- [x] Bundle Bangla and English strings into the app.
- [x] Add `values/strings.xml`, `values-bn/strings.xml`, and generated locale configuration.
- [x] Add a first-run language chooser with `বাংলা` first and `English` second.
- [x] Persist the selected language in Proto DataStore.
- [x] Bundle Noto Sans Bengali with its offline SIL Open Font License and verify the font hash locally.
- [x] Create the glossary in `packages/localization/glossary.csv`.
- [x] Add a local-gate test requiring identical Bangla and English Android string keys.
- [x] Add tests that reject raw user-facing strings in critical field screens. The local gate now rejects literal English `Text` and accessibility descriptions in the field UI, and the remaining map, quantity, priority, and prediction labels use paired resources.
- [x] Test Bengali combining marks, wrapping, truncation, and large text. A connected Compose test renders the critical bilingual shell at 150 percent font scale, switches languages in place, and asserts that the Bangla fixture exercises combining marks.
- [x] Keep P0 to P3, coordinates, cryptographic fingerprints, and delivery IDs language-neutral.
- [x] Provide bilingual status text when a term could affect safety. P0 urgency, predicted-versus-confirmed route risk, identity verification, replay rejection, custody state, and relay role all have paired Bangla and English resources.
- [x] Add accessible labels in both languages for the implemented field surfaces; continue auditing new screens.
- [x] Verify that no language change clears the implemented request and identity state.

## M1 secure identity

- [x] Generate device-bound RSA-2048 encryption and signing identities, the accepted C5 alternative to Ed25519.
- [x] Keep private identity keys non-exportable in Android Keystore. Hardware-backed availability still requires target-phone evidence.
- [ ] Implement offline administrator provisioning QR. Signed enrollment, administrator trust pinning, credential issue/verify, expiry checks, durable storage, bilingual display/paste, and CameraX scanning with bundled ML Kit models are complete; a real two-phone camera pass remains.
- [x] Add a salted local six-digit PIN unlock with a persisted five-attempt offline lockout.
- [x] Implement least-privilege roles and permission policy for coordinator, clinic, hospital, and driver/operator credentials.
- [x] Hide forbidden actions and enforce the same signed-credential policy below the user interface.
- [x] Add RSA-PSS-signed, hash-chained authorization audit events and a visible bilingual latest-entry ID.
- [x] Add a persisted five-attempt, 30-second offline PIN lockout.
- [x] Add signed credential revocation propagation and enforce signed credential expiry at every use. Revocations are encrypted separately for known peers, stored and relayed as Protobuf envelopes, applied by the intended recipient, and forwarded onward after verification.
- [x] Test valid, expired, revoked, malformed, and wrong-role credentials. Valid, expired, signed revoked, malformed/tampered, future-dated, untrusted-issuer, wrong-target, profile/role/key mismatch, stale-credential replay, encrypted propagation, idempotence, and ciphertext/AAD mutation cases pass.

## M2 distributed data and CRDT sync

- [x] Implement an append-only Protobuf operation log in SQLite for the current request and conflict paths.
- [x] Implement vector-clock comparison.
- [x] Select and document CRDT or merge behavior per field.
- [x] Use a grow-only set primitive for receipt and audit identifiers; production receipt projection wiring remains.
- [x] Use an observed-remove set with explicit operation-tag tombstones for assignments.
- [x] Implement a per-replica PN-counter that cannot lose concurrent stock changes; inventory projection wiring remains.
- [x] Route concurrent destination, priority, and medical-quantity conflicts to human review.
- [x] Add the persistent sync queue and bounded retry policy through the shared Room mesh outbox.
- [x] Add a policy-versioned SHA-256 convergence hash per mission projection.
- [x] Build the conflict screen in Bangla and English with vector clocks and explicit resolution.
- [x] Test concurrent update, deletion, duplicate, late arrival, and clock-skew cases. Assignment tests prove deletion tombstones, idempotent duplicate operations, late observed-add suppression, and concurrent unseen re-add survival.

## M3 nearby mesh

- [x] Define the byte-oriented `PeerTransport` interface; transport connection-state reporting remains with the Nearby adapter.
- [x] Implement Nearby Connections using the cluster strategy; physical three-phone evidence remains.
- [x] Add Android 12 and newer Bluetooth and nearby-device permission handling, including Android 17 local-network policy.
- [x] Implement the active relay as an Android `connectedDevice` foreground service.
- [x] Use WorkManager only for deferred inbox application, retry, deduplication cleanup, and expired-queue maintenance; the live mesh remains in its visible foreground service.
- [x] Implement neighbor advertising and discovery with an explicit human accept or reject step.
- [ ] Authenticate peers before accepting payloads. Nearby comparison digits are followed by mutual fresh-nonce challenge-response using administrator-signed credentials and device RSA-PSS keys; envelopes and acknowledgements are blocked until verification, and signed receipts are checked before the outbox advances. Physical multi-phone evidence remains.
- [x] Persist distinct N1 coordinator, N4 clinic, N6 hospital, and RLY-01 relay profiles; stop the active relay before profile changes and generate role-bound enrollment identities from the selected profile.
- [x] Build persistent Android inbox/outbox/seen-message state with bounded retry and dead-letter transitions; Go retains its durable Bolt inbox.
- [x] Implement the store-and-forward relay engine with atomic durable receipt and connect it to the Nearby byte transport.
- [x] Implement TTL and hop-limit enforcement in both Android and Go durable ingress.
- [x] Implement durable duplicate rejection before domain-event application in both Android and Go.
- [x] Encrypt payloads for the final recipient through the signed public-key directory using RSA-OAEP-wrapped AES-256-GCM; production instrumentation decrypts the persisted request with only the intended recipient key.
- [x] Keep relay-visible routing metadata outside recipient-only ciphertext; relays never receive a content decryption key.
- [x] Select relay behavior using battery, link quality, queue size, and proximity. Because Nearby Connections does not expose RSSI, the tested policy uses acknowledgement round-trip time as the honest link-quality signal and authenticated contact recency as the proximity signal; unknown telemetry remains visibly unmeasured.
- [x] Reduce broadcast frequency by 60 percent below 30 percent battery.
- [x] Display topology, queue depth, last contact, link quality, and relay reason in the bilingual live mesh card alongside state, peers, battery, broadcast interval, discovery, candidates, and errors.
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
- [x] Render the real computed edge sequence using bundled offline scenario data; the Compose canvas is retained only as an explicit renderer-failure fallback.
- [x] Embed MapLibre Native Android in Compose through a lifecycle-aware adapter with a local-only style and graceful route-diagram fallback.
- [x] Package and verify the offline Sylhet geographic region in Android. Its 1,576 OSM-derived features are deterministically exported from the same checksum-pinned PMTiles archive as the projector and retain visible OpenStreetMap attribution.
- [x] Explain why the preferred truck path was selected or rejected and why boat precedes simulated air fallback.

## M5 proof of delivery

- [x] Generate QR images with ZXing Core.
- [ ] Scan with the bundled ML Kit barcode model and verify airplane-mode behavior. The CameraX scanner, workflow-specific QR gate, automatic verification handoff, permission fallback, JVM tests, APK model check, and connected-test compilation pass; a real camera-in-airplane-mode run remains.
- [x] Build the signed Protobuf QR payload with the required delivery, identity, hash, nonce, timestamp, and previous-receipt fields.
- [x] Verify the sender key, signature, payload hash, nonce, timestamp, delivery, mission, and recipient offline. Non-simulated handoffs require an installed administrator-signed sender credential; the self-contained seeded flow is allowed only while visibly simulated.
- [x] Add atomic Room nonce persistence and replay rejection.
- [x] Add bounded ten-minute field clock-skew handling with boundary tests.
- [x] Add credential expiry and manual override policy. Offer time and sender-credential validity are separately enforced; expired or revoked credentials cannot be manually resurrected and require a new administrator-signed credential.
- [x] Link each receipt to the previous custody receipt hash.
- [x] Display verifier result and exact rejection reason in Bangla and English.
- [x] Reconstruct and cryptographically verify the complete local receipt chain.
- [x] Test altered QR fields, reused QR, unknown key, expired key, and wrong delivery. Each rejection preserves the nonce store and custody chain; revoked credential rejection is also covered.

## M6 triage and priority preemption

- [x] Implement P0, P1, P2, and P3 policy data.
- [x] Add SLA deadlines and a live countdown derived from the remaining P0 SLA window.
- [x] Calculate baseline ETA and 30 percent slowdown ETA.
- [x] Flag predicted SLA breaches.
- [x] Define and test allowed transitions: only P0/P1 may preempt P2/P3; equal-priority and inverted transitions are rejected.
- [x] Find a safe waypoint for lower-priority cargo.
- [x] Require confirmation for a real assignment change. The confirmed Protobuf event and vector-clocked cargo-assignment projection commit atomically; a duplicate-event fault proves projection rollback and the v5-to-v6 migration preserves existing operations.
- [x] Record the reason, policy version, confirmer, affected cargo, waypoint, and estimated gain in Protobuf.
- [x] Render the decision explanation in Bangla and English.
- [x] Test equal priorities, missing waypoint, stale ETA, and simultaneous P0 requests. A five-minute freshness limit blocks both initial proposals and confirmations from stale route data; concurrent urgent cargo is ordered by priority tier, remaining SLA, and stable cargo ID while every non-selected P0/P1 stays queued.

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

- [x] Classify destinations by road, water, and air reachability.
- [x] Mark destinations with no valid ground or water path as drone-required.
- [x] Define boat and simulated-drone speed and battery assumptions.
- [x] Compute candidate rendezvous points.
- [x] Minimize delivery-completion time while preserving the configured battery reserve.
- [x] Explain the chosen rendezvous with coordinates, three ETAs, and projected battery.
- [x] Simulate boat and drone arrival events as visibly labelled Protobuf ledger entries.
- [x] Reuse M5 for a sender-and-recipient-signed boat-to-drone custody transfer.
- [x] Display simulated vehicle state clearly in Bangla and English.
- [ ] Complete fleet edge-case evidence. No feasible rendezvous, low battery, unreachable graph, changed destination, and delayed-boat R3-to-R2 replanning pass at the unit/domain layer; the bilingual journey passes on Android 15 and 16 emulators, and paired persistent replanned-state screenshots now exist. The target-phone run remains.

## Command dashboard and Disaster Control

- [x] Build projector layout for 1366 by 768 and 1920 by 1080.
- [x] Rebuild the headquarters as a Next.js 16 App Router dashboard with shadcn/ui and project-owned styling.
- [x] Publish the locally verified prebuilt headquarters to Vercel without GitHub Actions or hosted CI.
- [x] Add an optional Cloudflare Worker and D1 archive for allow-listed presentation summaries; keep field state and encrypted mesh content out of it.
- [x] Add route map, inventory, node status, mesh queue, risk, and custody panels.
- [x] Add Offline, Syncing, Conflict, and Verified states.
- [x] Add Bangla and English dashboard modes.
- [x] Add simulated rainfall, saturation, edge failure, node failure, delay, and battery controls.
- [x] Add duplicate-message and QR-tamper injection with unchanged-chain narration.
- [x] Add deterministic event narration with module-labelled evidence rows.
- [x] Add scenario pause, resume, step, reset, and automatic replay.
- [x] Make every implemented control deterministic under scenario seed `20260412`.
- [ ] Prove field phones continue after dashboard disconnection.

## Testing, evidence, and submission

- [x] Create module unit tests and contract fixtures across Android, Go, dashboard, archive, Protobuf, routing, ML, and deterministic scenario layers.
- [x] Create integration tests for the event lifecycle, including encrypted request persistence, durable relay, convergence, rerouting, triage assignment, observer replay, and signed custody.
- [x] Create a three-phone manual test sheet in `docs/PHYSICAL_DEVICE_TEST.md`.
- [x] Run the automated fault-injection baseline and record its limits. Physical three-phone, process-kill, storage-pressure, and booth-power rehearsals remain manual release gates.
- [x] Run 10,000 simulated connections against the Go service and record the conditions. All 10,000 independent gRPC streams received durable acknowledgements and remained open together for five seconds; measured limitations are recorded in `artifacts/reports/load/2026-09-04-go-10000.md`.
- [ ] Measure route recomputation on target hardware.
- [ ] Measure field-app RAM on target hardware.
- [ ] Measure relay recovery and duplicate rejection.
- [ ] Complete the bilingual and accessibility matrices.
- [ ] Capture every required item in `SCREENSHOTS.md`. The live observer dashboard overview is now stored in both languages at 1366x768 and 1920x1080; remaining field, fault-state, and physical-device evidence stays listed there.
- [ ] Rehearse the ten-minute script twenty times.
- [ ] Record three unchanged successful final passes.
- [x] Create the architecture diagram, model card, final report, and editable fair deck. The Mermaid architecture diagram, model card, 16-page DOCX/PDF report, and validated eight-slide PPTX are complete.
- [ ] Record the optional backup video.
- [x] Audit every public claim for evidence or a visible simulation label. The signed-off matrix is in `artifacts/reports/claim-audit/2026-09-04.md`.
