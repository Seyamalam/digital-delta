# Custody and 4 GB phone-target checkpoint

Scope: follow-up implementation after `d33fe10`. This is an engineering checkpoint,
not a final project review, fair acceptance certificate or production deployment.

## Implemented

- Coordinator-signed custody paths among the mission's frozen readers, before the
  first receipt. Intermediate nodes require driver/coordinator credentials.
- Independent hub → driver → hospital signatures and receipt replication. Every
  leg retains the first receipt's mission/cargo commitment. Hash links, not phone
  clock order, determine the chain.
- Explicit retained-cargo reconciliation for edits crossing a receipt. The signed
  decision includes a reason and reviewed revision IDs. A stale review dialog
  cannot acknowledge edits the coordinator did not see.
- Bangla/English current-custodian and path display, coordinator review dialog,
  and role-filtered path editing. Controls retain the native Material 3 layout
  and 52 dp minimum action height.
- Room 8 → 9 migration and historical public-credential archive. Older credential
  replay cannot roll active authority backward; revoking an old credential leaves
  its replacement active. Historical receipt checks evaluate signing-time authority.
- Authority refresh preserves the last audit label only for the same identity;
  asynchronous audit completion cannot populate a different identity's UI.
- Standard 4 GB RAM Android phones recorded as the fair target. Models, OS versions
  and measured PSS/route latency remain unknown.

## Evidence and limits

`scripts/verify-local.sh --connected` passed with 102 JVM, 78 connected Android,
21 dashboard and 11 Worker tests. It also built debug/minified release and Next.js,
ran fresh Go race/vet/build checks, validated schema/localization/map/model assets,
and completed a Wrangler dry run. No hosted deployment was performed.

The independent-replica test exercises two handoffs, crossing edits, forbidden
driver reconciliation, stale review rejection, retained cargo, chain completion,
and receipt verification after replacing the hub's public keys. Separate connected
tests cover migration, credential replay and delayed revocation of an old key.

The development captures in `artifacts/screenshots/field/2026-09-05-custody/` show
the running accepted-request screen in Bangla and English on emulator-5554
(Android 15 / API 35). They use separate generated test requests, not a matched-ID
pitch pair. They precede the audit-label correction and are development evidence,
not final release screenshots. The request is genuinely persisted by the app;
packaged journey times remain visibly labelled and no physical delivery is claimed.

The opt-in inspection fixture originally timed out waiting for the audit label
after its 45-second pause. That exposed the label reset on authority refresh;
the correction is included here. Those failed inspection runs are not green
acceptance evidence. The ordinary full gate is separate from this manual fixture.
After the correction, the same English fixture completed its 45-second inspection
window and remaining authorization/revocation assertions successfully (one test,
zero failures, 1 minute 11 seconds). This was an unchanged app build after the full
gate, not a physical-device or complete demo pass.

## Still open

- Live coordinator assignment/reconciliation dialog journeys in both languages.
- Post-handoff reassignment, lost-phone recovery and backlog re-encryption. Retaining
  public keys for old receipts does not recover a lost private key.
- Operational mission-driven fleet/preemption, general graph-validated endpoints,
  and per-mission headquarters selection/route/SLA projection from the prior review.
- Three physical-phone radio, camera, memory and latency checks, plus three unchanged
  offline rehearsals. Emulator tests cannot close these gates.
- A final independent review and reconciliation of report, deck, PDF and hosted
  claims after the remaining integration work.
