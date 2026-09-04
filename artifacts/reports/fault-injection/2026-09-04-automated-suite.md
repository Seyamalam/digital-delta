# Automated fault-injection evidence

Status: **Passed locally on 2026-09-04 at commit `73dd13c`.**

The complete local gate ran with two connected Android emulators. Each emulator executed the same 42 instrumentation tests with zero failures and zero skipped tests.

```text
Android 15 Mento_API_35:       42 / 42 passed
Android 16 Pixel_10_Pro_XL:   42 / 42 passed
Command dashboard:            13 / 13 passed
Go node:                       race tests, vet, and build passed
Android APKs:                  debug and R8-minified release passed
ML pipeline:                   regenerated artifacts matched checked files
Offline map:                   PMTiles checksum passed
```

Command:

```bash
scripts/verify-local.sh --connected
```

## Injected failure coverage

| Fault | Automated result | Remaining live evidence |
|---|---|---|
| Send interrupted before acknowledgement | Outbox returns to bounded retry and later advances only after a valid signed acknowledgement | Physical relay interruption |
| Forged or altered acknowledgement | Signature verifier rejects it and keeps the message queued | Hostile physical peer |
| Database closes with queued work | Room restart restores the pending envelope | Process-kill recording on a phone |
| Duplicate, expired, or over-hop envelope | Durable ingress rejects it without a second domain effect | Three-phone duplicate arrival |
| Concurrent safety-sensitive edit | Vector clocks force human review; resolution survives Room restart | Two-phone convergence |
| Assignment deletion arrives late | Observed-remove tombstone prevents resurrection | Two-phone assignment projection |
| Confirmed route edge fails | Vehicle-constrained planner rejects the road and selects a valid water route | Target-phone timing |
| Prediction conflicts with confirmed state | Prediction adds a labelled penalty; confirmation remains authoritative | Live Disaster Control capture |
| Model cannot produce a result | Deterministic fallback returns an explicitly labelled result | Corrupt-file device rehearsal |
| P0 slowdown threatens its SLA | Engine proposes a safe P2 deposit and waits for confirmation | Target-phone timing |
| Equal priority or missing waypoint | Preemption is rejected | None beyond rehearsal |
| QR payload changes or nonce repeats | Verification rejects the handoff without extending the receipt chain | Two-phone camera pass |
| Disconnected field clock exceeds tolerance | Timestamp validation rejects it; values inside the bounded window pass | Phone clock rehearsal |
| Drone battery violates reserve | Rendezvous plan is rejected | None beyond rehearsal |
| Boat reports an 18-minute simulated delay | The engine replans from R3 to R2 before custody continues | Persistent delayed-state capture |
| Projector observer disconnects | Field events persist and replay in sequence after cursor reconnection | Booth cable/power rehearsal |
| Battery drops below 30 percent | Relay policy reduces broadcast frequency by 60 percent | Target-phone battery rehearsal |

## Not yet claimed

This suite does not prove Bluetooth or local Wi-Fi behavior across three physical phones, real camera capture in airplane mode, recovery from an operating-system process kill, nearly-full storage behavior, target-phone RAM, or target-phone route latency. These remain explicit manual gates rather than simulated successes.
