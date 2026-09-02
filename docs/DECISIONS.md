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

## DD-007: proposed implementation stack

**Status:** Proposed

**Decision:** Flutter for the field interface, native Android proximity adapter where required, Go for node and simulation services, React and TypeScript for the dashboard, SQLite for local state, and ONNX Runtime Mobile for inference.

**Reason:** This separates field UI, radio-specific code, systems simulation, and projector presentation while keeping each part testable.

**Before acceptance:** Record developer familiarity, target device versions, setup time, package maintenance, and a small nearby-transfer spike.

## DD-008: event log with selective CRDTs

**Status:** Proposed

**Decision:** Store immutable signed operations and apply different merge rules by field. Do not use last-write-wins for custody, active priority, or safety-sensitive destination changes.

**Reason:** Those fields need provenance and, in some cases, human resolution.

**Before acceptance:** Build convergence property tests for the chosen types.

## New decision template

```md
## DD-NNN: short decision

**Status:** Proposed, Accepted, Superseded, or Rejected

**Decision:** What we chose.

**Reason:** Why this fits the product and constraints.

**Consequences:** Work, risks, and claims affected.

**Evidence:** Prototype, test, documentation, or measurement.
```

