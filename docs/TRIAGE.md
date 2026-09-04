# Offline triage and priority preemption

This runbook covers the implemented M6 slice. It is a deterministic logistics policy demo, not a medical-triage tool. Route failures, weather conditions, and vehicle movement in this scenario are simulated and visibly labelled.

## Policy

| Priority | Meaning | SLA |
|---|---|---:|
| P0 | Critical medical | 120 minutes |
| P1 | High | 360 minutes |
| P2 | Standard | 1,440 minutes |
| P3 | Low | 4,320 minutes |

`triage-v2` evaluates urgent cargo using elapsed mission time plus a timestamped local route ETA. It also calculates a stress case with route ETA increased by 30 percent. Arrival exactly at the SLA is protected; only an arrival later than the SLA is a predicted breach. An ETA older than five minutes cannot produce or confirm a preemption; the interface asks for a fresh local route calculation instead.

The current fair scenario has 35 elapsed minutes:

- Initial truck route: 65-minute ETA, 100-minute baseline arrival, 120-minute stressed arrival. P0 remains protected.
- Confirmed simulated `E3` failure: the M4 engine selects a 200-minute boat route, producing a 235-minute baseline arrival and 295-minute stressed arrival. P0 is predicted to breach its 120-minute SLA.

## Preemption proposal

For the breach case, the engine permits only P0/P1 cargo to preempt P2/P3 cargo; equal-priority and inverted transitions are rejected. It proposes depositing `cargo-tarpaulin-p2` at the safest eligible waypoint so `cargo-medicine-p0` can continue. Unsafe candidates are excluded, then the remaining candidates are ordered by handling time and node ID. The deterministic result is `N3`, Sunamganj Sadar Camp, with an estimated 25 minutes gained.

When more than one urgent request is active, arbitration never demotes or deposits another P0/P1 item. It orders candidates by priority tier, then least remaining SLA, then stable cargo ID. The selected cargo proceeds and every other urgent cargo remains visible in the retained queue. The seeded, visibly simulated demonstration selects `cargo-medicine-p0` and retains `cargo-blood-p0`.

The proposal never mutates an assignment automatically. A coordinator must press the bilingual confirmation control. While the decision is being written, the interface enters a single-flight recording state so repeated taps cannot enqueue duplicate confirmations. A persistence failure restores the proposal for retry.

Confirmation appends a binary Protobuf `DomainEvent` to the Room operation log. Its `PreemptionConfirmed` payload records:

- urgent and deposited cargo IDs;
- safe waypoint node ID;
- coordinator identity ID;
- `triage-v2` policy version;
- reason code `SLA_BREACH_30_PERCENT`;
- estimated minutes gained.

The event and visible scenario remain marked simulated. Updating a generalized assignment projection and signing this audit event are later integration steps, so the current interface says the coordinator decision was recorded rather than claiming a completed physical handoff.

## Live proof

1. Open **Route & mesh / পথ ও মেশ** while commercial internet is unavailable.
2. Show the initial `E1 + E3` truck route and protected P0 SLA state.
3. Trigger the visibly simulated `E3` failure.
4. Show the real M4 boat result `E6 + E7`, then the M6 calculations: `235` baseline, `295` at 30 percent slowdown, and `120` SLA.
5. Read the proposed P2 deposit at Sunamganj Sadar Camp and the 25-minute estimate.
6. Confirm once, then show the local event suffix and preserved state after switching between Bangla and English.

## Automated evidence

- `TriageEngineTest`: priority SLA logic, slowdown calculation, allowed transitions, missing-waypoint failure, freshness rejection, urgent arbitration, and safe waypoint selection.
- `TriageWorkflowTest`: exact initial-route and boat-fallback decisions plus refusal to persist a proposal after its ETA ages out.
- `MainScreenViewModelTest`: M4-to-M6 propagation and duplicate-tap suppression.
- `RoomTriageWorkflowTest`: parses the persisted Protobuf event and verifies its audit fields.
- `MainScreenTest`: triggers the route failure, confirms the proposal, and checks bilingual state preservation.

Run all local evidence with:

```bash
scripts/verify-local.sh --connected
```

## Remaining M6 hardening

- generalized assignment-projection mutation and signed audit events;
- target-phone performance and full rehearsal evidence.
