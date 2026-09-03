# Conflict and convergence runbook

Digital Delta does not use one last-write-wins rule for every field. The fair build demonstrates why: two disconnected phones can both make valid local changes, and their wall clocks may be wrong.

## Implemented policy

| Data shape | Rule | Current evidence |
|---|---|---|
| Receipt and audit IDs | Grow-only set union | JVM merge test |
| Inventory deltas | Per-replica PN-counter; merge each component by maximum | JVM commutativity and no-loss test |
| Description | Deterministic timestamp then event-ID winner with merged vector clock | Both arrival orders converge in JVM test |
| Destination, priority, medical quantity | Concurrent or equal-clock disagreement requires human review | JVM policy test, Room restart test, bilingual screen |
| Custody | Signed state transition; never a CRDT winner | Planned M5 integration |
| Assignment removal | Observed-remove set or explicit tombstone | Not implemented yet |

The projection hash is SHA-256 over the policy version and canonical field, value, and vector-clock state. Matching hashes are evidence that two devices rebuilt the same projection; they are not signatures and do not prove who authorized the events.

## Live M2 drill

1. Open **Operations / অপারেশন** and tap **Simulate concurrent offline edits / সমসাময়িক অফলাইন সম্পাদনা চালান**.
2. Confirm the card is visibly labelled `SIMULATED / সিমুলেটেড`.
3. Explain that Phone A selected `N3` with clock `A:2, B:1` while Phone B selected `N6` with clock `A:1, B:2`.
4. Point out that neither vector clock dominates the other; a newer wall-clock timestamp is therefore not treated as authority.
5. Choose one destination as the coordinator.
6. Show the resolved value and the first twelve characters of the convergence hash.
7. Restart the app. The resolution remains because the conflict, immutable operations, merged vector clock, and projection are stored in Room.

## Automated proof

```bash
scripts/verify-local.sh --connected
```

Relevant tests:

- `MissionMergeEngineTest`: causality, equal-clock disagreement, concurrent merge ordering, G-set, and PN-counter.
- `RoomConflictCoordinatorTest`: atomic operation/conflict/projection persistence, database restart, resolution, and convergence hash.
- `DeltaMigrationTest`: Room version 3 to 4 migration without losing mesh state.
- `MainScreenTest`: small-phone scrolling, Bangla and English parity, explicit choice, and resolved feedback.

## Honest limitation

The current drill creates deterministic simulated edits inside one phone so it is always demoable at a fair booth. The same conflict records are Protobuf domain events suitable for mesh transfer, but identical convergence on two physical phones is not yet recorded as evidence. Do not describe the physical two-phone proof as complete until that test passes unchanged.
