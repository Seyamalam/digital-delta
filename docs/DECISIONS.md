# Architecture decisions

Use this log for choices that affect scope, claims, compatibility, or safety.

## DD-001: Bangla and English are core interfaces

**Status:** Accepted

**Decision:** Bundle and release Bangla and English together. Put Bangla first on initial language selection. Require behavioral, visual, and accessibility parity.

**Reason:** The intended field setting is Bangladesh. A translated presentation screen would not prove that field workers can complete critical tasks in Bangla.

**Consequences:** Localization keys and layout tests begin in Milestone 0. Critical untranslated strings block release.

## DD-002: no external hardware

**Status:** Accepted

**Decision:** Use only phones, laptops, a projector, normal charging and display accessories, and software simulation.

**Excluded:** IoT sensors, microcontrollers, LoRa devices, Raspberry Pi relays, physical drones, robotic vehicles, and custom electronics.

**Reason:** The project should prove distributed software behavior without depending on hardware procurement or unreliable booth setup.

**Consequences:** Rainfall, saturation, batteries, route failures, and vehicle movement use deterministic simulated events with visible labels.

## DD-003: command dashboard is not a central dependency

**Status:** Accepted

**Decision:** Phones retain identity, events, routing, triage, handoff, and synchronization logic locally. The dashboard consumes rebuildable observer data.

**Reason:** A laptop-centered system would weaken the offline and decentralized claim.

**Consequences:** Dashboard-disconnection testing is a release gate.

## DD-004: all eight modules remain in the plan

**Status:** Accepted

**Decision:** Keep M1 to M8 as named milestones and acceptance groups. Track each module as Planned, Skeleton, Integrated, Demoable, Verified, or Hardened.

**Reason:** The project aims for the full system while preserving honest status and build order.

**Consequences:** Public material may describe incomplete modules as planned, not working.

## DD-005: simulation must be explicit

**Status:** Accepted

**Decision:** Environmental observations and vehicle movement are simulated in the fair build. Every related event and screen carries a machine-readable flag and visible label.

**Reason:** Simulation is useful for repeatable fault injection. Hiding it would make the demonstration misleading.

**Consequences:** Screenshot and demo reviews reject missing simulation labels.

## DD-006: transport claims follow actual implementation

**Status:** Accepted

**Decision:** Use Protocol Buffers for mesh payloads. Use gRPC on links that implement gRPC correctly. Describe nearby framed-Protobuf transport by its real name.

**Reason:** Protobuf is a serialization format. It does not turn a nearby-radio API into gRPC.

**Consequences:** If strict gRPC on every node-to-node path remains a goal, the chosen network transport must support it and pass a compliance test.

## DD-007: native Android implementation stack

**Status:** Accepted

**Decision:** Use Kotlin and Jetpack Compose for the field application. Use Room over SQLite for mission data, Proto DataStore for small settings, Nearby Connections for nearby communication, a foreground service for an active relay session, Hilt for dependency injection, and Coroutines with Flow for asynchronous state. Use Go for node and simulation services, React and TypeScript for the dashboard, and ONNX Runtime Android for inference.

**Reason:** The highest-risk features are Android-specific radio, background execution, local persistence, secure key handling, and device lifecycle behavior. Native Android removes a cross-platform plugin boundary from those paths.

**Consequences:** The fair build targets Android phones. The field app uses Gradle Kotlin DSL, a version catalog, KSP, Material 3, Navigation Compose, ViewModel, and Compose UI tests. Other mobile platforms can reuse schemas and domain rules later but are outside the first implementation.

## DD-008: event log with selective CRDTs

**Status:** Accepted

**Decision:** Store immutable signed operations and apply different merge rules by field. Do not use last-write-wins for custody, active priority, or safety-sensitive destination changes.

**Reason:** Those fields need provenance and, in some cases, human resolution.

**Consequences:** Concurrent destination, priority, or medical-quantity values create a durable `ConflictRaised` event and cannot update the projection until a coordinator records `ConflictResolved`. Description values may choose a deterministic winner while retaining merged causal history. Grow-only sets cover append-only identifiers and PN-counters preserve per-replica inventory deltas. Room schema version 4 adds conflict and mission-projection tables with a policy-versioned SHA-256 convergence hash.

**Evidence:** `MissionMergeEngineTest`, `RoomConflictCoordinatorTest`, `DeltaMigrationTest`, `MainScreenTest`, and the paired conflict-review screenshots under `artifacts/screenshots/`.

## DD-009: Android transport and background execution

**Status:** Accepted

**Decision:** Use Google Nearby Connections as the first nearby transport behind a `PeerTransport` interface. Keep an active disaster-relay session in an Android foreground service. Use WorkManager only for deferrable retries, cleanup, and maintenance.

**Reason:** Nearby Connections exchanges data through nearby Bluetooth and Wi-Fi without commercial internet. Android background limits make WorkManager unsuitable for a continuous live relay.

**Consequences:** The target phones need Google Play services for the first implementation. Version 19.5.0 is pinned for the current build. The app displays foreground-service state, battery cadence, permission errors, discovered candidates, and Nearby comparison digits. Although Google's current manifest sample caps `ACCESS_WIFI_STATE` and `CHANGE_WIFI_STATE` at API 31, Play services 19.5.0 returned `MISSING_PERMISSION_ACCESS_WIFI_STATE` on the Android 15 emulator with that cap; the build therefore retains both normal permissions on newer versions and records the behavior as tested compatibility evidence. A future Wi-Fi Direct transport can implement the same interface without changing domain logic.

**Evidence:** `NearbyPermissionPolicyTest`, `PeerFrameCodecTest`, `MainScreenTest`, and the paired API 35 relay screenshots in `artifacts/screenshots/`.

## DD-010: Android storage and cryptography

**Status:** Accepted

**Decision:** Use Room for structured mission data and Proto DataStore for small typed preferences. Use RSA-2048 identity keys generated and retained in Android Keystore, RSA-PSS/SHA-256 signatures, RSA-OAEP/SHA-256 content-key wrapping, and AES-256-GCM payload protection.

**Reason:** Room provides SQLite migrations and compile-time query checks. DataStore fits small transactional settings. RSA-2048 is an explicit C5 option and is consistently available across the Android baseline; Android Keystore keeps private keys non-exportable.

**Consequences:** Sensitive stores are excluded from cloud backup. Signature tests use deterministic Protobuf bytes. The implementation must verify target-device Keystore behavior before describing keys as hardware-backed. OAEP uses a SHA-256 main digest and MGF1/SHA-1 for Android provider compatibility.

## DD-011: Android mapping and QR

**Status:** Accepted

**Decision:** Embed MapLibre Native Android in Compose and package an offline Sylhet region. Use ZXing Core to generate QR images and the bundled ML Kit barcode model to scan them offline.

**Reason:** MapLibre Native has Android offline-region support. The newer MapLibre Compose wrapper is not yet API-stable. A bundled barcode scanner avoids model download during the demonstration.

**Consequences:** The map boundary sits behind a Compose adapter. Offline assets, tile licensing, attribution, storage size, and scanner availability become release checks.

## DD-012: acknowledge only after atomic durable relay receipt

**Status:** Accepted

**Decision:** Android records the seen-message claim, immutable inbox bytes, and next-hop outbox entry in one Room transaction. A durable acknowledgement is created only after that transaction commits. Outbound failures return the item to `PENDING` with bounded exponential backoff; terminal rejection or expiry moves it to `DEAD_LETTER`.

**Reason:** A relay that acknowledges before durable storage can lose relief data if the process or phone stops. Separate inbox and outbox commits can also strand a message between receipt and forwarding.

**Consequences:** Nearby Connections remains a replaceable byte transport. Room schema version 3 adds `mesh_inbox` and `seen_messages`; migrations and restart behavior are connected-test gates. Acknowledgement signing is a subsequent security step and unsigned acknowledgements cannot yet be treated as authenticated peer evidence.

**Evidence:** `RoomMeshIngressTest`, `MeshOutboxDispatcherTest`, and `DeltaMigrationTest`.

## DD-013: deterministic preferred-vehicle fallback

**Status:** Accepted

**Decision:** Validate the supplied Sylhet fixture into one weighted directed graph and normalize its `river` code to `WATERWAY`. Run deterministic Dijkstra per vehicle mode. For the current mission, try the assigned truck first, then a boat, then the visibly simulated drone; do not select the globally fastest vehicle and silently discard the assignment policy.

**Reason:** A heterogeneous fleet needs hard feasibility constraints and an explainable fallback order. A simulated drone should not replace a valid boat merely because its fixture time is smaller.

**Consequences:** Failing `E3` makes `N4` unreachable by truck, so the engine selects waterway edges `E6 + E7`. The UI displays the edge sequence, ETA, reason, simulation labels, and monotonic recomputation time. Risk penalties can alter costs later, but a predicted risk remains distinct from a confirmed closure.

**Evidence:** `RoutePlannerTest`, `RouteScenarioTest`, `SylhetMapAssetTest`, `MainScreenTest`, and the paired reroute screenshots under `artifacts/screenshots/`.

## DD-014: route-driven preemption requires human confirmation

**Status:** Accepted

**Decision:** Feed the selected M4 route ETA into `triage-v1`, evaluate the required 30-percent slowdown case, and treat arrival exactly at an SLA as protected. If P0 or P1 would arrive late, propose the safest deterministic lower-priority drop but do not apply it without a coordinator confirmation. Record one Protobuf decision event before displaying success.

**Reason:** Route and triage demonstrations must share real state, and a prediction should not silently change custody or assignments. A deterministic proposal is explainable; a human gate preserves operational accountability.

**Consequences:** The current seeded mission moves from protected to a P0 breach proposal when simulated `E3` failure changes its ETA from 65 to 200 minutes. The event records the policy version, reason code, coordinator, both cargo IDs, waypoint, and estimated time gain. Confirmation is single-flight to prevent rapid duplicate taps. The event is an auditable decision, not proof that a physical deposit occurred; generalized assignment projection and signed audit integration remain.

**Evidence:** `TriageEngineTest`, `TriageWorkflowTest`, `MainScreenViewModelTest`, `RoomTriageWorkflowTest`, `MainScreenTest`, and paired proposal/confirmation screenshots under `artifacts/screenshots/`.

## DD-015: atomic signed custody and bounded field time

**Status:** Accepted

**Decision:** Encode delivery offers as prefixed URL-safe Base64 Protobuf, sign the canonical offer bytes with RSA-2048-PSS/SHA-256 in Android Keystore, and compare every operational field with trusted local mission state. On acceptance, claim the nonce and append the custody event in one Room transaction. Sign the resulting receipt with both seeded sender and recipient identities, link it to the previous receipt hash, and permit at most ten minutes of disconnected clock drift.

**Reason:** A relay-readable JSON token would violate the mesh contract, and a UI-only success state would not prove custody. Atomic nonce use prevents two concurrent scans from recording the same handoff. A two-minute window proved too fragile during the live projector rehearsal; ten minutes remains bounded while tolerating realistic offline phone drift and demonstration pacing.

**Consequences:** Altered fields, wrong delivery state, stale timestamps, and reused nonces fail before custody changes. The chain can be rebuilt and signature-checked from local Protobuf events after restart. Current sender and recipient are protected demo identities on one phone; physical camera scanning, signed cross-phone credential binding, credential revocation/expiry, and an explicit override policy remain required before field deployment.

**Evidence:** `DeliveryOfferCodecTest`, `AndroidDeviceIdentityKeyStoreTest`, `RoomProofOfDeliveryWorkflowTest`, `MainScreenViewModelTest`, `MainScreenTest`, and paired acceptance/replay/tamper screenshots under `artifacts/screenshots/`.

## New decision template

```md
## DD-NNN: short decision

**Status:** Proposed, Accepted, Superseded, or Rejected

**Decision:** What we chose.

**Reason:** Why this fits the product and constraints.

**Consequences:** Work, risks, and claims affected.

**Evidence:** Prototype, test, documentation, or measurement.
```
