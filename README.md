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
- React, TypeScript, Vite, and MapLibre GL JS command dashboard
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

The shared Protobuf contract now lints and generates Android Lite and Go bindings. The Go node exposes a bidirectional gRPC stream backed by a durable Bolt inbox with TTL, hop-limit, and duplicate rejection. On Android, the bilingual relief-request form creates a real Protobuf domain event, resolves the signed destination key, applies recipient-only hybrid encryption, and atomically writes its operation and binary envelope to the Room outbox. Instrumentation decrypts that persisted request with only the intended recipient private key. RSA-2048 device-bound Android Keystore identities, signed offline provisioning credentials, a durable public-key directory, and the bilingual enrollment/trust interface also have JVM and emulator evidence. The laptop-side `delta-provision` tool creates an administrator trust anchor and signs device enrollment requests without internet.

These are integrated foundations, not a claim that all eight modules are complete. Camera scanning for provisioning codes, Nearby Connections, signed peer acknowledgements, ONNX inference, MapLibre offline regions, and the command dashboard remain under active implementation. Current evidence is recorded in `artifacts/` and can be reproduced with `scripts/verify-local.sh --connected`.
