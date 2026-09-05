# Digital Delta

> **বন্যা মোকাবিলায় ইন্টারনেট ছাড়াই ত্রাণ সমন্বয়**  
> **Offline relief coordination for flood response**

Digital Delta is an offline-first disaster logistics system for Bangladesh. It coordinates urgent supply requests, mixed fleets, route failures, field handoffs, and eventual synchronization when commercial internet is unavailable.

Built by Touhidul Alam Seyam of BGC Trust University Bangladesh and registered through the Bangladesh Innovation Fair's Innovation Exhibitor route.

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

## Local commands

```bash
make setup       # install locked local dependencies
make verify      # unit, build, model, map, Go, and dashboard gate
make connected   # add connected Android journeys
make demo        # start Go node, observer, seeded drill, and projector
make reset       # move old laptop demo state to a recoverable local backup
make lint
make format
```

Open `http://127.0.0.1:3000/` after `make demo`. The runner publishes the fixed `fair-pass-01` Protobuf drill and labels every generated environment or vehicle event as simulated. The field phones do not require these laptop processes. A public, seeded headquarters view is also available at [digital-delta-headquarters.vercel.app](https://digital-delta-headquarters.vercel.app); the fair's live observer path stays on the local laptop.

## Software stack

- Kotlin and Jetpack Compose field application, Android first
- Material 3, Navigation Compose, ViewModel, Coroutines, Flow, and Hilt
- Room over SQLite for the event log, queues, projections, identities, and conflicts
- Proto DataStore for language and small device settings
- Google Nearby Connections with an Android foreground service for active relay
- WorkManager handles only deferred inbox application and durable maintenance; the active Nearby relay remains in a visible foreground service
- Go node services, deterministic drill publisher, load tests, and gRPC services
- Next.js 16 App Router, React, TypeScript, shadcn/ui, Tailwind CSS, MapLibre GL, a checksum-pinned offline PMTiles region derived from OpenStreetMap, and locally packaged fonts for the command dashboard
- Vercel for the optional public headquarters build; local Next.js remains the live fair runtime
- Cloudflare Workers and D1 for an optional sanitized headquarters observation archive that never becomes a field dependency or stores mesh payloads
- Protocol Buffers Kotlin Lite and Go generated contracts; the browser consumes a sanitized SSE presentation projection rather than mesh payloads
- gRPC on supported IP links, with framed Protobuf transport for nearby-radio links
- JCA/JCE and Android Keystore for RSA-2048-PSS signatures, RSA-OAEP key wrapping, AES-256-GCM payloads, and protected device keys
- MapLibre Native renders a checksum-pinned Android geographic extract generated from the projector's reviewed OpenStreetMap-derived PMTiles archive; both clients resolve graph edge IDs against committed OSM-following road and waterway polylines, while Compose remains responsible for controls and the renderer-failure diagram
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
  headquarters-archive/   Optional Cloudflare Worker and sanitized D1 archive
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
- [Bundled font and license](docs/THIRD_PARTY_FONTS.md)
- [Security model](docs/SECURITY.md)
- [Testing strategy](docs/TESTING.md)
- [Measured acceptance targets](docs/ACCEPTANCE_TARGETS.md)
- [Three-phone offline acceptance sheet](docs/PHYSICAL_DEVICE_TEST.md)
- [Requirements traceability](docs/TRACEABILITY.md)
- [Architecture decisions](docs/DECISIONS.md)
- [Offline provisioning runbook](docs/PROVISIONING.md)
- [Conflict and convergence runbook](docs/CONFLICTS.md)
- [Offline routing runbook](docs/ROUTING.md)
- [Triage and preemption runbook](docs/TRIAGE.md)
- [Proof-of-delivery runbook](docs/PROOF_OF_DELIVERY.md)
- [Local observer bridge runbook](docs/OBSERVER.md)
- [Innovation fair research](research/innovation-fair-fit.md)
- [Five-minute pitch deck manuscript](docs/PITCH_DECK.md)
- [Final technical and market report](docs/FINAL_REPORT.md)
- [Submission package and checksums](docs/SUBMISSION_PACKAGE.md)

## Definition of done

The project is fair-ready when:

- the complete mission succeeds three times consecutively with internet unavailable;
- each core module has a live proof, automated test, and screenshot or recording;
- every critical field screen passes the Bangla and English test matrix;
- the system visibly distinguishes real device behavior from simulation;
- a one-click reset restores the known starting scenario;
- measured results replace untested performance claims;
- the repository includes setup instructions, architecture diagrams, Protobuf schemas, model card, pitch material, and a backup demo video.

### Latest M6 hardening

`triage-v2` rejects route estimates older than five minutes before proposal and again at confirmation, so stale data cannot write a preemption event. Concurrent P0/P1 cargo is ordered deterministically by priority tier, remaining SLA, and stable cargo ID; non-selected urgent cargo remains visibly queued in the bilingual, clearly labelled simulation. Confirmation atomically commits the Protobuf event and a vector-clocked cargo assignment with a deterministic convergence hash; a duplicate-event fault test proves rollback, and the Room v5-to-v6 migration preserves existing operations.

## Current status

The prototype has implementations across all eight module areas, but it is **not
complete or field-validated**. The September 5 review found integration and
security gaps beyond the previously listed physical tests. Historical test counts
below do not certify subsequent changes.

The headquarters dashboard now uses seven Next.js App Router workspaces with a
persistent shadcn sidebar: overview, map, missions, resources, network, activity,
and a separate exercise lab. Larger controls and bilingual typography replace the
single crowded screen. See [the redesign checkpoint](docs/HQ-REDESIGN-2026-09.md).

The observer has moved to Hono/Workers with ordered D1 storage, source-bound
publisher authentication, replay, and a strictly sanitized SSE stream. Wrangler
runs it locally without internet. The Go mesh harness remains Protobuf/gRPC; the
old Go observer is opt-in legacy code. The local drill-to-Hono-to-Next.js path
works. A durable Android publisher also sends locally authored request and recorded
planning summaries; the actual emulator HTTP request path reached local Hono/D1
and was inspected in the browser. This authenticates the publisher, not individual
field-event signatures at headquarters.
See [the observer runbook](docs/OBSERVER.md).

Android provides offline PIN/enrollment, signed credentials and acknowledgements,
Room queues, routing, on-device synthetic-risk inference, triage and custody
rehearsals. The current hardening adds signed envelope origins, per-peer forwarding
receipts, and stricter received-event application. Independent Room/Keystore tests
now prove accepted requests through an interrupted relay, three-writer convergence,
replicated conflict resolution and origin-to-hospital custody with receipt return.
They exercise separate replicas on one emulator, not three physical radios.

Remaining release gates include:

- Physical three-phone request/edit/custody propagation and relay recovery with laptop off.
- Live bilingual checks of the implemented driver-path/reconciliation dialogs, plus generalized post-handoff reassignment recovery.
- Accepted-mission integration of fleet/preemption controls beyond the labelled exercises.
- Target-phone memory/latency measurements and real camera checks.
- Fresh UI evidence, human accessibility review and three unchanged offline passes.
- Review of hosted deployment configuration and public claims after migration.

See the [remediation and follow-up review](artifacts/reports/code-review/2026-09-05-remediation-review.md)
for individual finding status, current tests and the remaining software work.

Historical evidence: the September 4 Go load run held 10,000 gRPC streams with
approximately 1.19 GiB peak server RSS and 57.645-second p95 acknowledgement latency.
That is capacity evidence, not acceptable operational latency. The prior Android
16 emulator release measured 67,504 KB peak PSS over three post-inference samples;
it is not a physical-phone measurement. See the dated reports in `artifacts/`.

All environment and vehicle scenarios remain visibly simulated. Bundled
OpenStreetMap geography and road/waterway polylines are real map data, not proof
that any road is passable or any shelter is currently safe.

Headquarters now keeps each mission's route and SLA separately. Selecting a mission
in `/missions` carries its map/ETA into other sidebar pages. Android records a
route-bound SLA result for breach, within-SLA and no-route cases; old or unrelated
evaluations cannot clear the selected route's warning. Estimates older than five
minutes require replanning. See the [mission checkpoint](artifacts/reports/code-review/2026-09-05-mission-headquarters-checkpoint.md)
for local evidence and remaining fleet work.
