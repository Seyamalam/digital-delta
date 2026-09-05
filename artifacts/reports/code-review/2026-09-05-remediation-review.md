# Digital Delta remediation and follow-up review

Date: 2026-09-05, Asia/Dhaka. Baseline findings: [deep review at 94a54a9](2026-09-05-deep-review.md).
Implementation checkpoints include `4414ab0`, `76a79e9` and `17fac8e`; the final
record-plan and evidence changes are in the commit containing this report.

## Result

The original review's failure paths have software corrections and regression
coverage. This does **not** mean the app or all eight modules are field-complete.
Independent Room databases and Keystore namespaces now exercise the missing core
workflow, but physical radio/camera acceptance and several broader operational
integrations remain. The earlier percentage-complete estimate is withdrawn.

The application remains native Kotlin/Compose. Headquarters remains Next.js with
shadcn workspaces; Hono/Workers and local D1 provide the optional observer. No hosted
CI, sensors, IoT or drone control was added.

## Original requirements findings

“Corrected” below describes the reported software failure, not the complete module's
live acceptance. The original report is retained unchanged as historical evidence.

| Finding | Software disposition | Evidence and remaining boundary |
|---|---|---|
| R1: received requests and distributed edits missing | Corrected | Authenticated idempotent Room application, frozen participant fan-out, raw revision frontier, three-writer conflicts/resolutions and equal hashes. Separate DB/Keystore tests; actual multi-phone radio convergence still required. |
| R2: relay retires a message after returning it to origin | Corrected | Previous-hop/per-peer forwarding state and destination preference. A remains connected while B forwards to C, including restart and duplicate admission. Physical topology not yet tested. |
| R3: no Android publisher | Corrected for requests and recorded planning estimates | Durable operation-log publication with per-destination receipts, retries, source-bound token and consent/configuration. An actual emulator-created request reached isolated Hono/D1 and appeared in the browser. Recorded E5/SLA/no-route snapshots have publication/restart regression coverage; full physical laptop-off acceptance remains. |
| R4: incompatible demo keys in operational custody | Corrected for origin-to-destination handoff | Provisioned independent keys, selected accepted mission, immutable version commitment, dual signatures, replay/tamper rejection and signed receipt return. Receipt-before-revision is retryable; crossing edits cannot rewrite history. Generalized driver reassignment/multi-hop custody remain separate work. |
| R5: map ignores actual route/rendezvous | Corrected | Typed event projection selects observed edges/mode/rendezvous, including E5 and explicit empty route. Packaged road/water geometry follows OSM; simulated airway geometry remains direct and labelled. No-route state does not invent a route. |
| R6: state lost beyond 100 observations | Corrected | Projection history is independent of the bounded visible log. Stream generation resets both cursor and state, including an empty replacement log; retention/reconnect tests cover the reported case. |
| R7: prediction becomes confirmed closure | Corrected | Confirmed closure and reopening events are separate from predicted probability, threshold and observation age. Boat selection alone does not create a closure. |

## Original standards/security findings

| Finding | Software disposition | Evidence and remaining boundary |
|---|---|---|
| S1: unsigned mesh origins | Corrected | Canonical origin signatures, immutable metadata/ciphertext checks, credential authority validation and tamper regressions. Relays cannot grant origin authority. |
| S2: known revoked peer authenticates | Corrected | Authoritative local revocation/expiry/key checks during proof and actions; active peers react to authority updates and expiry. A fresh valid proof using a known revoked credential is rejected in a connected test. Physical session invalidation remains to be exercised. |
| S3: stale ViewModel authority | Corrected | Current Room credential checks at protected boundaries and reactive display permissions. Background revocation and role-denial regressions; no restart/manual refresh is required for enforcement. |
| S4: invalid numeric record poisons SSE | Corrected | Finite/range validation before persistence, recoverable handling in the retained legacy observer, and strict Hono input validation. Invalid data no longer silently blocks subsequent replay. |
| S5: anonymous archive forgery | Corrected | Source-bound publisher bearer authentication; anonymous/wrong-source writes rejected. This authenticates the collector, not an end-to-end signature for every public event. |
| S6: body limit bypass | Corrected | Actual streamed byte count, independent of Content-Length; oversized and malformed input tests. |
| S7: reduced Go harness masquerades as secure mesh | Corrected by explicit isolation | Separate ReducedMeshLoadHarnessService contract, literal loopback only. Fresh 10,000-stream run is explicitly reduced-workload evidence, not secured Android capacity. |
| S8: documented local origin rejected | Corrected | Exact localhost and 127.0.0.1 fair origins accepted; unrelated origins rejected. |
| S9: English content retains Bangla language metadata | Corrected in code and browser checks | Language metadata follows the selected language; actionable map controls have paired labels. Human screen-reader review in both languages remains open. |

## Verification and fault evidence

- The local gate covers Protobuf compatibility/generation, Bangla/English resource
  parity, map checksums, deterministic synthetic model/ONNX reproduction, Android
  unit/debug/minified release builds, Go race/vet/build, 21 dashboard tests, 11 local
  Worker tests, TypeScript, seven Next.js routes and a Worker deployment dry run.
- The final combined gate passed **102 Android JVM tests, 75 connected tests,
  21 dashboard tests and 11 Worker tests**, after the P3 and PIN-isolation fixes.
  Connected execution used `emulator-5554`, Android 15/API 35. This is not a physical
  phone; Go checks in the final gate reused earlier same-turn cache entries.
- Six independent-field tests use separate databases and disjoint key namespaces.
  They cover relay restart, duplicates, accepted quantity, two/three writers,
  coordinator resolution, independent custody, role rejection, receipt reordering,
  crossing quantity edits, revocation and optional publication recovery.
- A maximum supported 128-UUID custody history remains a valid signed offer while
  QR encoding falls back safely to bilingual full-code copy. Encoding failure no
  longer crashes Compose. An oversized complete offer is rejected before issuance.
- The [fresh load run](../load/2026-09-05-reduced-go-10000.md) held 10,000 acknowledged
  connections simultaneously. Its p95 was 57.279 seconds and peak server RSS about
  1.13 GiB: capacity evidence, not emergency-response latency or mobile RAM.
- Browser inspection showed actual Android test request IDs in the local Hono feed
  with simulated labels. Before any route publication, it correctly showed “No
  route received” over real bundled OSM geography. This was not seeded telemetry
  presented as phone output.
- The isolated HTTP test configuration was removed from debug assets. Publishing
  tokens were not committed, embedded in Next.js, or included in screenshots.

### Failures found during remediation

The turn was not clean on its first attempt. Full connected runs exposed an obsolete
N1 provisioning fixture, a bilingual assertion targeting an off-screen node, a
reordering test that accidentally forwarded its prerequisite first, and production
journeys assuming the manual QA PIN. These were corrected in the tests, preserving
the intended assertions. The test-only PIN rule restores prior PIN fields afterwards.
The production request regression now selects the newly created payload hash rather
than whichever old pending ciphertext sorts first.

Software corrections found during implementation include multi-writer frontier
projection, receipt dependency retry, immutable custody snapshots, imported signer
role checks, active peer revocation, stream-generation reset and bounded QR creation.
The tests were extended to cover those failures rather than removing them.

## Standards

The independent standards pass found one additional P2 issue: a valid large custody
history could exceed a single QR's capacity and throw during composition. The bounded
encoder, localized copy fallback and maximum-history regression close that reported
path. The reviewer re-inspected the fix and found no residual defect in that scope;
the main agent ran the tests. This is a targeted follow-up, not security certification.

Remaining hardening priorities are a retained historical credential/key archive,
storage-pressure and long-running relay tests, bounded mission-history compaction,
and measured sustained secure transport throughput. The 128-event custody snapshot
limit is explicit; larger histories need a version/checkpoint protocol, not silent
truncation. Dense or multi-frame QR transfer needs real camera usability evidence.

## Spec

The independent integration pass found no new defect in the repaired receipt
dependency/convergence flow. A subsequent recorder review found a P2 semantic issue:
an on-time P3 plan was emitted as SlaBreachPredicted. The recorder now emits that
event only when the stressed arrival breaches the deadline, and the regression
requires a route-only result for P3. An emitted warning is a dated assessment, not
a permanent live alarm or vehicle assignment.

The following product work is still open; it is not solely hardware evidence:

1. **Operational assignment and reconciliation.** Add signed driver assignment,
   explicit custodian changes and a coordinator workflow to settle an edit that
   crosses an accepted receipt. Keep both histories visible. Current custody is
   one origin-to-destination handoff; local post-delivery edits are blocked.
2. **Received-mission fleet/preemption integration.** The accepted Missions screen
   computes route/SLA estimates, but M6/M8's confirmation controls still operate in
   explicitly labelled exercises. Connect them to selected real mission/cargo
   records and replicate the resulting authorized operations.
3. **General request geography.** The current field form clearly states the fair's
   N1→N6 workflow. Add an accessible offline node selector rather than an inert
   location control or arbitrary coordinates that the graph cannot route.
4. **Multi-mission headquarters.** Routes and rendezvous currently summarize the
   latest observation. Add per-mission selection/projections, timestamped SLA
   evaluation/clearance, and stale-plan indication before presenting a fleet-wide
   dispatch board. Inventory, shelters and exercise zones need separate governed
   data workflows; map layers are not verified shelter availability.
5. **Field acceptance.** Three physical phones, real provisioning/PoD camera scans,
   interrupted relay recovery, airplane-mode/laptop-off operation, target RAM and
   route-latency measurements, human bilingual accessibility, persistent final
   projector-resolution captures and three unchanged full demo passes.
6. **Fair package reconciliation.** Refresh the report/deck/PDF and hosted preview
   against the final accepted build. Older artifacts and deployments do not certify
   these changes. Confirm exhibitor logistics directly with the organizer.

## Additions worth considering after those gates

- A readiness page showing credential expiry, installed map version, pending
  messages, last peer contact, and last successful optional publication.
- Signed shelter-capacity/stock updates with source, age and explicit unverified
  status; do not turn static points into claims of current safety.
- Exportable, redacted after-action evidence bundles with event IDs and hashes.
- A rehearsable recovery workflow for a lost phone and credential rotation.
- Multi-district offline packs once the Sylhet workflow passes on physical phones.

These are backlog recommendations, not implemented features. No new live flood,
medical, market-research or organizer claims were verified in this engineering pass.
