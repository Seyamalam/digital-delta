# Digital Delta

> **বন্যা মোকাবিলায় ইন্টারনেট ছাড়াই ত্রাণ সমন্বয়**  
> **Offline relief coordination for flood response**

Digital Delta is an offline-first disaster logistics system for Bangladesh. It coordinates urgent supply requests, mixed fleets, route failures, field handoffs, and eventual synchronization when commercial internet is unavailable.

The project is designed for an innovation-fair demonstration using Android phones, laptops, and a projector. It does not require IoT sensors, LoRa devices, ESP32 boards, physical drones, or any other external hardware.

## Product rules

1. Bangla and English are equal product interfaces. Every critical task must work in both languages without downloading a language pack.
2. Field phones continue to work when the command laptop is closed or disconnected.
3. Every claimed module needs a visible live proof and a repeatable test.
4. Simulated environmental data and vehicles must be labelled as simulated.
5. The app supports human coordinators. It does not replace medical, evacuation, or government decisions.
6. The mesh carries Protocol Buffer payloads. Relay devices cannot read encrypted delivery contents.
7. The complete demonstration must run without commercial internet.

## The eight core modules

| Module | Capability | Main live proof |
|---|---|---|
| M1 | Secure offline identity and role enforcement | Provision a user offline, deny a forbidden action, inspect the audit entry |
| M2 | Distributed database and conflict handling | Make conflicting offline edits, reconnect, resolve, and converge |
| M3 | Store-and-forward mesh relay | Send A to C through B, interrupt B, resume, reject a duplicate |
| M4 | Multi-modal routing | Block a road and reroute a valid truck, boat, or drone path in under two seconds |
| M5 | Signed proof of delivery | Accept a signed QR handoff and reject tampering and replay |
| M6 | Triage and priority preemption | Predict an SLA breach and move P0 cargo ahead of P2 or P3 cargo |
| M7 | Predictive route decay | Change simulated rainfall and saturation, display risk, and reroute |
| M8 | Hybrid fleet and drone handoff | Mark a zone drone-required and transfer custody at a computed rendezvous |

## Demonstration setup

- **Phone A:** clinic or relief-camp requester
- **Phone B:** volunteer relay and boat operator
- **Phone C:** field hospital, recipient, or drone operator
- **Laptop A and projector:** Delta Command dashboard
- **Laptop B:** deterministic Disaster Control console and test runner

All phones enter airplane mode before the mission. Bluetooth and local Wi-Fi may be enabled for nearby communication. The laptops may join the local demonstration network, but the field workflow must not depend on them.

## Software stack

- Kotlin and Jetpack Compose field application, Android first
- Material 3, Navigation Compose, ViewModel, Coroutines, Flow, and Hilt
- Room over SQLite for the event log, queues, projections, identities, and conflicts
- Proto DataStore for language and small device settings
- Google Nearby Connections with an Android foreground service for active relay
- WorkManager for deferred retries and maintenance, not continuous mesh operation
- Go node services, chaos simulator, load tests, and gRPC services
- React, TypeScript, Vite, MapLibre GL, a checksum-pinned offline PMTiles region derived from OpenStreetMap, and locally packaged fonts for the command dashboard
- Protocol Buffers Kotlin Lite, Go, and TypeScript generated contracts
- gRPC on supported IP links, with framed Protobuf transport for nearby-radio links
- JCA/JCE and Android Keystore for RSA-2048-PSS signatures, RSA-OAEP key wrapping, AES-256-GCM payloads, and protected device keys
- MapLibre Native Android embedded in Compose with bundled offline regions
- ZXing Core for QR generation and bundled ML Kit barcode scanning
- ONNX Runtime Android for the on-device route-risk classifier
- Python, pandas, scikit-learn, and skl2onnx for model training and export

The transport distinction above is deliberate. Nearby radio APIs do not magically provide gRPC. We will document which links use gRPC and will not claim strict gRPC compliance for a transport that only carries framed Protobuf messages.

## Repository plan

```text
apps/
  field-android/          Kotlin and Jetpack Compose field application
  command/                Projector-friendly web dashboard
services/
  node/                   Go node, gRPC, simulation, and load-test code
packages/
  proto/                  Shared .proto schemas and generated clients
  domain/                 Language-neutral domain rules and fixtures
  localization/           Bangla and English source strings and glossary
  scenario/               Seeded disaster scenarios and expected outcomes
models/
  route-decay/            Training code, exported model, metrics, model card
docs/                     Product, architecture, safety, and test documents
scripts/                  Setup, reset, test, and demo commands
research/                 Source-backed problem and fair research
```

## Documentation

- [Milestones](MILESTONES.md)
- [Build checklist](TODO.md)
- [Screenshot plan](SCREENSHOTS.md)
- [Live demo script](DEMO.md)
- [Product requirements](docs/PRD.md)
- [Technology stack](docs/STACK.md)
- [Technical architecture](docs/ARCHITECTURE.md)
- [Bangla and English requirements](docs/LOCALIZATION.md)
- [Security model](docs/SECURITY.md)
- [Testing strategy](docs/TESTING.md)
- [Requirements traceability](docs/TRACEABILITY.md)
- [Architecture decisions](docs/DECISIONS.md)
- [Offline provisioning runbook](docs/PROVISIONING.md)
- [Conflict and convergence runbook](docs/CONFLICTS.md)
- [Offline routing runbook](docs/ROUTING.md)
- [Triage and preemption runbook](docs/TRIAGE.md)
- [Proof-of-delivery runbook](docs/PROOF_OF_DELIVERY.md)
- [Local observer bridge runbook](docs/OBSERVER.md)
- [Innovation fair research](research/innovation-fair-fit.md)

## Definition of done

The project is fair-ready when:

- the complete mission succeeds three times consecutively with internet unavailable;
- each core module has a live proof, automated test, and screenshot or recording;
- every critical field screen passes the Bangla and English test matrix;
- the system visibly distinguishes real device behavior from simulation;
- a one-click reset restores the known starting scenario;
- measured results replace untested performance claims;
- the repository includes setup instructions, architecture diagrams, Protobuf schemas, model card, pitch material, and a backup demo video.

## Current status

Implementation is active. The native Android field shell now builds and runs with Bangla-first and English interfaces, four interactive demo surfaces, an animated offline initialization sequence, real QR generation, and device-tested state preservation. Language-neutral engines and passing JVM tests exist for authorization, vector clocks, mesh policy, multimodal routing, proof-of-delivery verification, triage, route-risk prediction, and hybrid-fleet rendezvous.

The shared Protobuf contract now lints and generates Android Lite and Go bindings. The Go node exposes a bidirectional gRPC stream backed by a durable Bolt inbox with TTL, hop-limit, and duplicate rejection. Android now mirrors the critical store-and-forward semantics in Room: it atomically claims a message ID, stores the inbox envelope, and queues the next hop before returning a durable acknowledgement; retries, dead letters, expiry, hop limits, duplicates, and restart recovery have connected tests. Google Nearby Connections 19.5.0 now drives a `CLUSTER` radio adapter and visible Android `connectedDevice` foreground service. Every radio frame is Protobuf, connection candidates expose Nearby's comparison digits for human confirmation, and Android 12 through 17 permission selection is unit-tested. A single Android 15 emulator proves the bilingual start, permission, foreground-service, advertise/discover, battery-cadence, and stop lifecycle; the three-phone relay proof remains a required physical-device test. M2 now has an append-only Protobuf operation log, vector clocks, deterministic grow-only sets and PN-counters, per-field merge policy, Room-backed conflict and projection tables, and convergence hashes. Its bilingual drill creates two simulated disconnected destination edits, refuses to let wall-clock time choose a safety-sensitive winner, survives database restart, records the coordinator's choice, and rebuilds a hashed projection. M4 now loads and validates the supplied Sylhet graph from a bundled offline asset, normalizes river edges to waterways, includes a visibly simulated airway, runs deterministic Dijkstra routing with strict vehicle modes, and measures each recomputation. The live route starts with truck edges `E1 + E3`; failing `E3` removes the truck path and animates the policy-ordered boat fallback `E6 + E7` with its ETA, reason, and measured device latency. M7 now generates a deterministic synthetic dataset, trains a small logistic classifier, reports held-out and baseline metrics, exports and parity-checks ONNX, and runs the bundled model on Android. Its bilingual risk drill clearly labels simulated rainfall, elevation, and saturation, displays probability, threshold, and runtime, adds an E3 cost rather than claiming a closure, and proactively selects the boat route. That real route output now feeds M6: the initial route protects the P0 SLA, while the boat fallback predicts a breach under the required 30-percent slowdown and proposes a safe P2 deposit at `N3`. The bilingual confirmation uses a single-flight recording state and appends a Protobuf event containing the policy, reason, confirmer, affected cargo, waypoint, and estimated gain to Room. M5 now generates a real Protobuf QR offer, signs it with an RSA-2048-PSS Android Keystore identity, verifies all trusted delivery fields offline, atomically claims the nonce, and appends a sender-and-recipient-signed custody event. Its chain links each receipt hash to the next offer, rebuilds from Room, and visibly rejects altered fields, expired offers, and nonce replay without mutating custody. The bilingual relief-request form creates a real Protobuf domain event, resolves the signed destination key, applies recipient-only hybrid encryption, and atomically writes its operation and binary envelope to the Room outbox. Instrumentation decrypts that persisted request with only the intended recipient private key. RSA-2048 device-bound Android Keystore identities, signed offline provisioning credentials, a durable public-key directory, and the bilingual enrollment/trust interface also have JVM and emulator evidence. The laptop-side `delta-provision` tool creates an administrator trust anchor and signs device enrollment requests without internet.

M8 is now demoable on that same emulator. The bundled graph includes air-only N7, the field engine classifies it as drone-required, evaluates three rendezvous candidates, chooses R3 by delivery-completion time, and rejects plans below the 20 percent battery reserve. A visibly simulated 18-minute delay and updated boat position trigger a fresh on-phone optimization to R2; the delayed vehicle report and revised rendezvous are written as Protobuf events before the handoff continues. Its bilingual animated flow persists rendezvous and boat/drone state, creates a real RSA-PSS boat-to-simulated-drone QR offer, and appends the two-party receipt to the local custody chain. The last complete 37-test connected baseline passed, and the new delayed-boat connected test compiles but still requires execution. Paired ready and transferred captures are in `artifacts/screenshots/`; the replanned-state capture is pending.

The Delta Command projector app is runnable offline. It starts in Bangla, switches to an information-equivalent English view without resetting the exercise, fits the complete operations surface at 1366 by 768 and 1920 by 1080, and retains its deterministic seeded fallback and fault lab. Its MapLibre map reads a checksum-pinned PMTiles region from the repository, displays real OpenStreetMap-derived geography without making a commercial-internet request, preserves attribution, and draws simulated mission routes as explicitly labelled overlays. A working local observer path now accepts Protobuf `DomainEvent` publications over gRPC, assigns durable ordered sequences in BoltDB, resumes after a cursor, and exposes only an allow-listed SSE presentation projection to the browser. The dashboard uses the stream to rebuild risk, failed-edge, route, ETA, rendezvous, delayed-vehicle, and event-ledger state. A live local check disconnected the projector at sequence 7, published sequences 8 through 14 while it was absent, and replayed the complete gap after reconnection. The rehearsal publisher marks every synthetic disaster and vehicle fact as simulated. Thirteen command tests, map-integrity verification, and the production build now run inside `scripts/verify-local.sh`.

The minified ARM64 release has also run the model and language switch on an Android 16 emulator at a measured peak of 67,504 KB PSS across three post-inference readings. These are integrated foundations, not a claim that all eight modules are complete. Provisioning and handoff now share a lifecycle-bound CameraX QR scanner, purpose-specific input gate, bilingual permission/error states, and the bundled ML Kit model; the local gate also inspects the assembled APK for its `.tflite` barcode assets. A real two-phone camera pass in airplane mode remains before this is live evidence. Cross-phone credential binding, signed application-layer peer identity and acknowledgements, the physical three-phone relay, physical-phone memory evidence, an Android MapLibre region, authenticated observer publication, and several Disaster Control injections remain under active implementation. Delayed-boat replanning is implemented and unit-tested, with its new emulator journey still awaiting execution. Current evidence is recorded in `artifacts/` and can be reproduced with `scripts/verify-local.sh --connected`.
