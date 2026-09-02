# Requirements traceability

This file connects claims to implementation, tests, demo actions, and evidence. Paths are planned until the repository skeleton exists.

| ID | Requirement | Planned component | Verification | Demo proof | Screenshot |
|---|---|---|---|---|---|
| L10N-01 | Bangla and English work fully offline | `packages/localization`, field app | Translation completeness and device matrix | Switch Phone C to English during mission | H02 plus English pair |
| L10N-02 | Language switch preserves state | Field localization controller | Mid-form and mid-mission tests | Switch during active mission | Field language pair |
| SYS-01 | Phones survive dashboard loss | Field event store and domain services | Dashboard disconnect integration test | Close observer link | Command overview and offline proof |
| SYS-02 | Simulation is always labelled | Scenario events and UI badge | UI and event validation tests | Change rainfall or edge | H04 and H08 |
| M1-01 | Offline identity provisioning | Identity feature | Credential fixtures | Provision or inspect demo identity | Sign-in shot |
| M1-02 | RBAC below interface | Policy service | Direct forbidden-command test | Coordinator-only action denied | Role denial shot |
| M1-03 | Signed audit history | Event signatures | Projection rebuild verification | Open audit event | Custody or audit shot |
| M2-01 | Vector-clock comparison | Sync package | Unit and property tests | Show concurrent histories | H07 |
| M2-02 | Safe convergence | CRDT and projection packages | Equal convergence-hash test | Reconnect devices | H07 |
| M2-03 | Unsafe conflict needs human | Conflict feature | Concurrent priority fixture | Resolve prepared conflict | H07 |
| M3-01 | Store-and-forward relay | Proximity and queues | Three-device integration test | A to B to C | H03 |
| M3-02 | Interrupted relay recovery | Persistent outbox and inbox | Kill and restart test | Disconnect Phone B | Relay evidence shot |
| M3-03 | TTL and deduplication | Envelope policy | Expiry and duplicate tests | Inject duplicate | Mesh queue shot |
| M3-04 | Relay cannot read payload | Crypto and envelope views | Recipient-key test | Inspect Phone B metadata | Encrypted-envelope evidence |
| M4-01 | Three edge modes | Routing graph | Fixture validation | Show map legend | Map overview |
| M4-02 | Dynamic recomputation | Routing engine | Timed edge-failure test | Flood active road | H04 |
| M4-03 | Vehicle constraints | Routing filters | Invalid-mode fixtures | Inspect rejected route | Route explanation |
| M5-01 | Signed QR handoff | PoD feature | Valid signature test | Scan valid QR | H05 |
| M5-02 | Tamper rejection | PoD verifier | Altered-field tests | Inject tamper | Rejection shot |
| M5-03 | Replay rejection | Nonce store | Reused-nonce test | Scan twice | H06 |
| M5-04 | Receipt chain | Custody projection | Chain verification | Open timeline | Custody shot |
| M6-01 | P0 to P3 taxonomy | Triage policy | Deadline fixtures | Create P0 | H02 |
| M6-02 | 30 percent SLA prediction | ETA service | Slowdown fixtures | Trigger risk and delay | SLA shot |
| M6-03 | Drop and reroute | Preemption feature | Safe-waypoint tests | Confirm P2 drop | Triage shot |
| M7-01 | Required features | Model pipeline | Feature-schema test | Adjust scenario inputs | Risk shot |
| M7-02 | On-device classifier | Field model adapter | ONNX parity and device test | Run risk update offline | Risk shot |
| M7-03 | Prediction affects route | Routing risk penalty | Comparative-route fixture | Display risk-adjusted route | H04 |
| M7-04 | Prediction is distinguishable | Risk interface | Label validation | Show simulated prediction | Risk shot |
| M8-01 | Drone-required reachability | Fleet engine | Unreachable fixture | Fail final land and water edges | H08 |
| M8-02 | Rendezvous computation | Fleet engine | Known-coordinate fixture | Display chosen coordinate | H08 |
| M8-03 | Boat to drone custody | M5 and fleet features | Handoff integration test | Perform signed transfer | H08 and H05 |
| M8-04 | Battery-aware broadcast | Mesh scheduler | Schedule-rate test | Set 25 percent battery | Low-battery shot |
| UI-01 | Projector-readable dashboard | Command app | Viewport screenshots | Full command overview | H01 |
| UI-02 | Offline and sync feedback | Field and command UI | State-machine tests | Move through offline and sync | H01 and field states |
| PERF-01 | Route target measured | Routing instrumentation | Median and p95 report | Show timer | H04 and evidence |
| PERF-02 | Mobile RAM measured | Release build | Target-device report | Show evidence summary | Engineering evidence |
| PERF-03 | Simulated load documented | Go load test | Reproducible report | Show result and conditions | Engineering evidence |

## Traceability rule

A pitch or README claim is ready only when its row has:

- an implemented component;
- a passing verification artifact;
- a repeatable demo step or an explicit reason it is evidence-only;
- a current screenshot or recording where visual proof helps.

