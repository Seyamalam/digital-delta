# Milestones

All eight modules remain in scope. Milestones control build order and evidence, not whether a module exists.

## Status vocabulary

| Status | Meaning |
|---|---|
| Planned | Scope and acceptance criteria exist |
| Skeleton | Interface and test fixture exist, but behavior may be stubbed |
| Integrated | Connected to the shared event model and user interface |
| Demoable | Live proof works from a clean reset |
| Verified | Automated checks and three unchanged full demo passes succeed |
| Hardened | Failure recovery, bilingual QA, and performance evidence are complete |

## Milestone 0: project foundation

**Goal:** Create one buildable repository with shared contracts and repeatable scenarios.

Deliverables:

- Proposed stack approved or replaced in `docs/DECISIONS.md`
- Monorepo folders and development commands
- Local verification automation for formatting, tests, and Protobuf compatibility; no hosted CI
- Versioned Protobuf envelopes and domain events
- Seeded Sylhet mission fixture
- Bangla and English localization framework
- One-command reset and seed scripts
- Explicit real-versus-simulated data labels

Exit criteria:

- A fresh machine can build the empty field app, dashboard, and Go service.
- Both languages render without network access.
- The same scenario seed produces the same event sequence.

## Milestone 1: bilingual field shell and local mission

**Goal:** Complete a relief request locally before adding distributed behavior.

Modules advanced: M1, M4, M5, M6.

Deliverables:

- First-run Bangla or English selection
- Role-aware navigation for coordinator, clinic, driver, relay, hospital, and auditor
- Create and inspect a P0 to P3 cargo request
- Local graph with road, waterway, and airway edges
- Valid route by vehicle type
- Local SLA calculation and priority decision
- Local signed delivery receipt
- Persistent offline event timeline

Exit criteria:

- One phone completes request, routing, triage, and receipt in both languages.
- Restarting the app preserves the mission.
- Forbidden role actions remain unavailable and fail safely when invoked directly.

## Milestone 2: identity and proof hardening

**Goal:** Make identity, custody, and audit claims defensible.

Modules completed toward demo: M1 and M5.

Deliverables:

- Offline administrator provisioning QR
- RSA-2048 device encryption and signing keys in Android Keystore
- PIN or device-secure-storage unlock
- Signed audit events
- Signed QR handoff with delivery ID, sender key, payload hash, nonce, and timestamp
- Tamper rejection
- Replay cache and rejection reason
- Reconstructable receipt chain

Current camera evidence: one bilingual CameraX scanner now captures administrator trust, recipient credential, and PoD QR codes; a workflow-purpose gate rejects accidental cross-flow codes before the existing cryptographic verifier runs. The ML Kit model is packaged in the APK and checked by the local verification script. This is implementation evidence only until the same paths pass on two real phones in airplane mode.

Exit criteria:

- Valid handoff succeeds.
- Modified payload, unknown signer, expired receipt, and reused nonce fail with distinct messages.
- An auditor can verify the receipt chain offline.

## Milestone 3: distributed data and conflict lab

**Goal:** Make disconnected devices converge without silently losing important changes.

Module advanced: M2.

Deliverables:

- Operation log and vector clock implementation
- CRDT types selected per domain field
- Deterministic merge rules
- Human conflict review for unsafe automatic merges
- Sync status and conflict interface
- Device convergence inspector on the dashboard

Exit criteria:

- Two devices edit the same mission offline.
- Safe fields merge automatically.
- A safety-sensitive conflict pauses for human resolution.
- All devices produce the same final projection after resolution.

## Milestone 4: store-and-forward mesh

**Goal:** Relay encrypted messages across nearby phones without internet.

Module advanced: M3.

Deliverables:

- Neighbor discovery and authenticated pairing
- Dual-role client and relay behavior
- Persistent outbox and inbox
- Message IDs, TTL, hop limit, acknowledgements, and deduplication
- Recipient encryption
- Battery-aware broadcast schedule
- Topology and queue visualization

Exit criteria:

- Phone A sends to Phone C through Phone B.
- Phone B disconnects during relay, restarts, and completes delivery later.
- Duplicate messages do not duplicate cargo or receipts.
- Phone B can display envelope metadata but cannot decrypt the cargo payload.

Current acknowledgement evidence: Android ingress signs each durable or rejected receipt with the receiving node's non-exportable RSA-PSS key. Dispatch verifies the exact receipt against the active provisioned peer record before changing queue state. JVM tests cover mutation, missing signatures, wrong node, stale time, unknown identity, expiry, and revocation; Android Keystore integration compiles for connected execution. Mutual connection challenge-response and the physical three-phone recovery run remain exit-criteria work.

## Milestone 5: dynamic routing dashboard

**Goal:** Make route selection, failure, and recovery visible and measurable.

Module completed toward demo: M4.

Deliverables:

- Directed multi-modal graph
- Dijkstra or A* implementation
- Vehicle constraints
- Failed-edge handling
- Risk penalties from M7
- Route explanation and alternatives
- Projector map and route animation
- Latency instrumentation

Exit criteria:

- Flooding an active edge triggers a valid route change within the target time.
- A truck never uses a waterway or airway.
- The dashboard explains why the chosen route changed.

## Milestone 6: autonomous triage

**Goal:** Prove that urgent cargo can change an active delivery plan safely.

Module completed toward demo: M6.

Deliverables:

- P0, P1, P2, and P3 taxonomy
- SLA clocks and 30 percent slowdown prediction
- Preemption policy
- Safe drop-waypoint selection
- Human confirmation boundary
- Bilingual decision explanation

Exit criteria:

- A projected SLA breach creates a warning.
- P0 or P1 can preempt lower-priority cargo.
- The system records who confirmed the drop-and-reroute decision and why.

## Milestone 7: predictive route decay

**Goal:** Run an honest on-device risk model and feed its output into routing.

Module advanced: M7.

Deliverables:

- Versioned dataset or clearly labelled synthetic scenario generator
- Rainfall, elevation, and saturation features
- Reproducible training pipeline
- Precision, recall, F1, confusion matrix, and threshold selection
- Exported ONNX classifier
- On-device inference
- Model card and limitations
- Risk overlay and explanation

Exit criteria:

- The field app runs inference without internet.
- Changing scenario features changes edge risk predictably.
- High-risk edges affect route cost.
- The dashboard identifies model output as a prediction, not a confirmed flood.

## Milestone 8: hybrid fleet and simulated drone handoff

**Goal:** Coordinate boat, truck, and simulated drone ownership without physical vehicles.

Module status: Demoable on the Android emulator; delayed-boat replanning now has passing domain tests and a compiled bilingual connected journey. Verification still requires running and capturing that new journey, target phones, and three unchanged full passes.

Deliverables:

- Reachability classification
- Drone-required zone state
- Rendezvous objective and calculation
- Boat and drone arrival events
- Signed custody transfer using M5
- Simulated vehicle panel with clear labels
- Battery-aware mesh throttling demonstration

Exit criteria:

- A destination with no valid road or water path becomes drone-required.
- The engine computes and explains a rendezvous coordinate.
- Boat-to-drone custody appears in the receipt chain.
- No screen or pitch implies that a physical drone was used.

Current evidence: N7 is air-only in the bundled graph, the engine chooses R3 by delivery-completion time while preserving a 20 percent reserve, Room stores `RendezvousPlanned` and `VehicleStateChanged` Protobuf events, and the existing M5 cryptography records a two-party boat-to-simulated-drone receipt. A reported simulated 18-minute delay plus updated boat position now causes a fresh local optimization from R3 to R2, writes the delayed vehicle event and revised rendezvous, and continues the handoff from that plan. Domain tests pass and the new bilingual Compose journey compiles; emulator execution and the delayed-state capture remain. The previous complete bilingual handoff journey passed in the 37-test connected baseline and has paired ready/transferred captures under `artifacts/screenshots/`.

## Milestone 9: command center and disaster control

**Goal:** Turn the integrated system into a legible projector experience.

Deliverables:

- Full-screen command dashboard
- Deterministic Disaster Control console
- Live node, queue, route, inventory, risk, and custody panels
- Controls for flood, recovery, delay, battery, conflict, duplicate, and tamper events
- Event narration panel in Bangla and English
- One-click scenario reset
- Automatic mission replay

Exit criteria:

- A viewer understands offline state, urgent cargo, route failure, and verified delivery within three seconds of each event.
- Disconnecting the dashboard does not stop field operation.

Current evidence: the React and TypeScript command app now provides a Bangla-first, English-equivalent full-screen surface with an offline MapLibre geographic map, P0 mission strip, route, inventory, node, mesh, custody, event, and local control panels. A checksum-pinned PMTiles archive supplies real OpenStreetMap-derived geography without runtime internet access; simulated mission facts occupy a separate labelled overlay. A deterministic six-step reducer demonstrates M7 risk, M4 road failure, M2 conflict, M3 battery throttling, and M5 custody verification. The tabbed fault lab adds observer syncing, relay loss with queue retention, simulated boat delay, duplicate rejection, and QR signature-tamper rejection; automatic replay can be paused without resetting the scenario. The Go observer and local SSE bridge now drive the same projection with durable ordered replay. Thirteen command tests pass, the production bundle contains its fonts, worker, and map locally, and live browser inspection confirms full mission-node viewport coverage. Persistent resolution-specific captures, rainfall/saturation controls, and a phone-continuity disconnect recording remain.

## Milestone 10: verification and fair package

**Goal:** Replace claims with recorded evidence.

Deliverables:

- Full automated test suite
- Fault-injection report
- Load-test report
- Mobile memory measurements
- Twenty rehearsal log
- Bilingual accessibility audit
- Screenshot set
- Architecture diagram
- Seven to eight slide fair deck
- Three to five minute backup walkthrough
- Ten-minute live script and ninety-second booth loop

Exit criteria:

- Three consecutive unchanged live demos pass after clean reset.
- The backup video and screenshots match the final build.
- Every public claim points to a test result, source, or explicit simulation label.

## Stretch milestones

These remain software-only:

- Multi-district offline map packs
- Shelter capacity and stock forecasting
- Missing-person and evacuation request types
- Compressed voice-note relay
- Training and after-action review mode
- Signed incident report export
- Scenario authoring interface
- Delayed cloud synchronization after connectivity returns
- Anonymous aggregate analytics for response planning
