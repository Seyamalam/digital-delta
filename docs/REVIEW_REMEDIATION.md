# Deep-review remediation ledger

Baseline: `b2b0f9d`. Original findings: [2026-09-05 review](../artifacts/reports/code-review/2026-09-05-deep-review.md).

This is an acceptance ledger, not a completion percentage. A passing engine test
does not close an integrated workflow. Physical-radio, camera and target-phone
performance evidence cannot be substituted with an emulator or an in-memory test.

| Finding | Current work | Closure evidence still required |
|---|---|---|
| R1 | Request/revision admission exists; missing creation now retries | Independent replicas, replicated decisions, operational mission screens |
| R2 | Previous-hop provenance, per-peer receipts, direct-destination preference | Connected regression and physical three-phone recovery |
| R3 | Optional Android publisher integration pending | Phone event to Hono to browser, disconnect/replay |
| R4 | Operational custody integration pending | Independent provisioned identities and linked receipts |
| R5 | Typed routes, closures and rendezvous reach map | Full route/no-route/reopening matrix |
| R6 | Projection independent of bounded activity history | Stream reset semantics and restart/reload acceptance |
| R7 | Threshold and two-hour evidence age now separate from closure | Regression and browser inspection |
| S1 | Signed immutable origin metadata; inactive sender refused | Multi-hop credential/tamper regressions |
| S2 | Fresh proof and outbound/inbound authority checks | Background revocation/session tests |
| S3 | Room authority subscription and expiry refresh | Every protected command after background revocation |
| S4 | Numeric admission and visible retained-record rejection | Reopen/replay regression |
| S5 | Hono source-bound publishing authentication | Retest with Android publisher; deployed configuration not claimed |
| S6 | Streaming byte-counted request cap | Existing Worker regressions |
| S7 | Reduced load harness has separate gRPC service, loopback-only | Reject field-service interoperability; constrain fresh load report |
| S8 | Both supported loopback browser origins allowed | Existing Worker regressions |
| S9 | Document language and map action labels follow selection | Browser bilingual inspection, assistive-technology evidence |

## Remaining non-software gates

- Three physical phones: relay while origin remains connected, interruption and recovery.
- Physical camera provisioning and custody scan under venue lighting.
- Release RAM and route latency on the intended target phone.
- Three complete unchanged offline rehearsals at both projector resolutions.
- Real flood-model validation and verified shelter/resource feeds are not established.
- The optional public observer is not an emergency-service authority and must never
  receive confidential cargo contents, encrypted mesh payloads or authentication secrets.
