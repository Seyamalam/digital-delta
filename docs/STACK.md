# Technology stack

This is the accepted implementation stack for the fair build. Dependency versions will live in the Gradle version catalog, Go modules, and `pnpm-lock.yaml` once implementation starts.

## Field application

| Concern | Choice | Reason |
|---|---|---|
| Language | Kotlin | Native access to Android radio, lifecycle, security, and storage APIs |
| User interface | Jetpack Compose and Material 3 | Adaptive phone and tablet UI, accessibility semantics, previews, and tests |
| Navigation | Navigation Compose | Role-aware field flows and state restoration |
| State | ViewModel, Coroutines, Flow, and StateFlow | Observable offline state and structured asynchronous work |
| Dependency injection | Hilt | Replaceable production, simulation, and test adapters |
| Mission database | Room over SQLite | Event log, queues, projections, identities, conflicts, nonces, and migrations |
| Small settings | Proto DataStore | Language, theme, accessibility, and device preferences |
| Nearby transport | Google Nearby Connections | Offline peer discovery and payload exchange through nearby Bluetooth and Wi-Fi |
| Active relay | Android foreground service | Keeps a user-visible relay session active during an exercise |
| Deferred jobs | WorkManager | Retry, cleanup, expiry, and maintenance that can run later |
| Binary contracts | Protocol Buffers Kotlin Lite | Compact language-neutral mesh envelopes |
| IP communication | gRPC Kotlin | RPC on direct local IP links that support gRPC correctly |
| Cryptography | JCA/JCE and Android Keystore | RSA-2048-PSS signatures, RSA-OAEP key wrapping, AES-256-GCM payload encryption, and SHA-256 hashes |
| Key protection | Android Keystore | Protect device-local keysets and restrict key use |
| Routing | Pure Kotlin A* or Dijkstra | Deterministic on-device multi-modal routing |
| Map | MapLibre Native Android | Offline regions, route overlays, and map attribution |
| QR generation | ZXing Core | Deterministic signed handoff QR rendering |
| QR scanning | Bundled ML Kit barcode scanner | Offline scanning without a runtime model download |
| ML inference | ONNX Runtime Android | Run the route-risk classifier on the phone |
| Localization | Android resources and Noto Sans Bengali | Bundled Bangla and English field interfaces |

## Android project conventions

- Gradle Kotlin DSL
- Gradle version catalog for dependency versions
- Kotlin Symbol Processing where supported
- Compose Bill of Materials for Compose libraries
- Package-by-feature organization
- Unidirectional screen state and user actions
- No database, transport, or crypto calls directly from composables
- No Android types in the language-neutral routing and CRDT algorithms
- Release builds tested on three physical Android phones
- Initial minimum target of Android 8, subject to the final device inventory

## Nearby relay design

Nearby Connections supplies peer discovery and byte transfer. Digital Delta supplies the disaster-network behavior:

- persistent inbox and outbox;
- store-and-forward relay;
- message ID and deduplication;
- TTL and hop limit;
- acknowledgements and retry;
- recipient encryption;
- priority and battery-aware scheduling.

The nearby path carries framed Protocol Buffer envelopes. It is not called gRPC. The transport is hidden behind `PeerTransport` so a later Wi-Fi Direct implementation can reuse the same queues and policies.

## Laptop command system

| Concern | Choice |
|---|---|
| Dashboard | React and TypeScript |
| Build tool | Vite |
| Package manager | pnpm |
| Map | Bundled SVG topology for the deterministic fair scenario; optional offline MapLibre region later |
| Styling | CSS variables and an accessible Digital Delta component system |
| Local connection | Protobuf gRPC publication/replay into Go; allow-listed SSE projection into the browser |
| Projector targets | 1366 by 768 and 1920 by 1080 |

The dashboard is an observer. It can disappear without stopping the phones.

## Go services

Go owns:

- gRPC services;
- deterministic Disaster Control scenarios;
- chaos and fault injection;
- observer aggregation for the dashboard;
- route and CRDT reference implementations;
- 10,000-connection simulation;
- evidence and report export.

The Go process is not required for field identity, local routing, mesh queues, triage, or proof of delivery.

## ML toolchain

Training uses Python, pandas, scikit-learn, and skl2onnx. The repository stores the training script, dataset manifest, evaluation report, exported model, model hash, and model card. Android uses ONNX Runtime Android for inference.

## Testing

| Layer | Tools |
|---|---|
| Kotlin domain | JUnit and property tests |
| Compose | Compose UI Test |
| Coroutines and Flow | kotlinx-coroutines-test and Turbine |
| Room | JVM and Android migration tests |
| Android integration | Instrumentation tests on physical phones |
| Go | Standard Go test, race detector, benchmarks, and load harness |
| Dashboard | Vitest, Testing Library, and browser tests |
| Protocol | Cross-language golden fixtures and compatibility checks |

## Deliberate exclusions

- Flutter and Dart
- Compose Multiplatform
- Firebase as a field dependency
- Cloud-hosted authentication
- Runtime translation services
- Python on the Android device
- IoT devices and custom hardware
- Physical-drone control
- Continuous mesh work through WorkManager
