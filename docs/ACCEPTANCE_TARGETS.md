# Acceptance targets

These targets define the final fair evidence gate. They were frozen on 2026-09-04 before the physical-phone rehearsal. Emulator and laptop measurements are useful development evidence, but they do not substitute for a named target phone where the row requires one.

| Area | Target | Measurement | Current evidence |
|---|---|---|---|
| Route recomputation | p95 below 2,000 ms across 30 repeated active-edge failures | Release APK on the lowest-spec fair phone | Sub-millisecond emulator samples only; target-phone run pending |
| Field memory | Peak proportional set size below 150 MB after map load, ONNX inference, reroute, and custody verification | Three readings from the release APK on the lowest-spec fair phone | 67,504 KB peak on Android 16 ARM64 emulator; target-phone run pending |
| Three-phone relay | 10 of 10 A to B to C deliveries durably accepted with commercial internet disabled | Three physical phones, fixed envelope, recorded message IDs | Pending physical devices |
| Relay recovery | B restarts mid-relay; C receives exactly one accepted copy within 30 seconds of B returning | Three physical phones with duplicate count and recovery time recorded | Durable retry, deduplication, and restart tests pass; radio run pending |
| Battery policy | Normal broadcast interval increases by 60 percent or more below 30 percent battery | Policy unit test and visible field state | Automated and single-emulator evidence pass |
| QR handoff | 10 of 10 fresh offers accepted; tampered and replayed offers rejected with no extra custody row | Two physical cameras in airplane mode | Cryptographic, Room, UI, and bundled-model tests pass; camera run pending |
| Observer replay | No sequence gap after the browser disconnects and events publish while absent | Go integration test and local live run | Passed sequence 7 to 14 replay |
| Dashboard update | p95 below 500 ms from local observer receipt to visible projection under the fixed fair drill | Local production build, 30 events | Instrumentation report pending |
| Go capacity | 10,000 simultaneous gRPC streams, 10,000 durable acknowledgements, five-second full-concurrency hold, zero server errors | Reproducible laptop load harness | Capacity passed; acknowledgement p95 was 57.645 seconds and is recorded as a limitation |
| Language parity | Every critical task passes in Bangla and English, including a mid-flow language switch where applicable | Connected UI matrix and screenshot manifest | Automated parity and emulator journeys pass; large-text and final physical matrix pending |

## Evidence rules

- Record commit hash, build variant, device model, OS version, scenario seed, and wall-clock date.
- Do not average away a failed run. Record failures and recovery beside successful samples.
- Keep simulated rainfall, saturation, battery, route failure, and vehicle movement visibly labelled.
- A change to code, model, fixture, or demo script resets the three-pass final rehearsal count.
- Hosted Vercel and Cloudflare availability is not a field-system success criterion.
