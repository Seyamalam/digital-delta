# Digital Delta project review

Reviewed on 2026-09-05, Asia/Dhaka. Snapshot: `94a54a9` on `main`.

## Assessment

The repository has substantial working components, reproducible builds, real cryptographic operations, an offline geographic dataset, and labelled simulation. It does not yet implement the complete phone-to-phone-to-headquarters workflow described in the architecture. Several missing connections are software work, not simply physical-device evidence awaiting collection.

The previous assessment of approximately 94 percent software completion was too optimistic. It counted implemented engines and passing demonstrations as evidence of an integrated mission. A percentage should not be used again until the acceptance matrix includes a request that actually reaches and changes a recipient's state, independent phone custody, replicated edits, and phone-originated dashboard events.

This review reports requirements separately from standards and security, following the code-review skill's two-axis approach. The scope is the complete current snapshot, not a historical commit diff. P1 means a defect or missing integration that should be fixed before claiming the associated workflow is complete. P2 means a narrower implementation or deployment problem. Neither designation implies a demonstrated real-world disaster incident.

No application implementation was changed. Diagnostic tests were temporary and are preserved as a text artifact beside this report. No deployment, remote archive insertion, commit, or push was performed.

## Verification performed

| Check | Result | Limits |
|---|---|---|
| `scripts/verify-local.sh` | Passed | Includes schema, localization, map checksums, model reproduction, Android builds, Go checks, dashboard build, archive dry run. Some initial tasks used caches. |
| Android `testDebugUnitTest --rerun-tasks` | 99 tests, zero failures/errors | Fresh task execution. No connected or physical Android run in this review. |
| Go `go test -race -count=1 ./...` | Passed | Fresh execution, including the small load harness. The 10,000-connection run was not repeated. |
| Existing dashboard tests | 17 passed | They do not cover the failures below. |
| Existing archive tests | 3 passed | Validation tests do not establish publisher authenticity. |
| Targeted dashboard regression tests | 3 failed as expected | Uses the real App, projection, and mission-geometry builder; replaces WebGL with an output of the geometry passed to it. |
| Observer invalid-number reproduction | Confirmed | Actual Go publication and SSE handler, isolated Bolt database. A NaN event blocks later valid records on both initial and repeated replay. |
| Archive handler reproductions | Confirmed | Actual Worker handler with mocked D1 and rate-limit bindings. Anonymous publication and an oversized body are accepted. No public endpoint was written. |
| Local production browser inspection | Completed | Bangla and English view, eventual PMTiles rendering, language metadata. No fresh projector screenshot acceptance or physical camera claim. |

The targeted dashboard tests are preserved in `2026-09-05-dashboard-regressions.test.tsx.txt`. Copy the file to `apps/command/src/review-regressions.test.tsx` and run the command in its header to reproduce. Backend reproductions are preserved alongside it as `2026-09-05-observer-repro_test.go.txt` and `2026-09-05-archive-repro.test.ts.txt`. Place these in the observer package and archive src directory respectively to rerun against isolated test stores. The isolated backend reproduction workspace was `/tmp/delta-review.YHUYFp`; temporary files there are not a permanent project dependency.

## Requirements and integration findings

### R1. P1: Received requests are never applied, and distributed mission synchronization is missing

Evidence: `apps/field-android/app/src/main/java/com/example/digitaldelta/domain/identity/CredentialRevocationInboxProcessor.kt:58`, `service/MeshMaintenanceWorker.kt:30`, and `data/local/DeltaDatabase.kt:243`, with the latter two paths relative to the same Android package.

The only production inbox processor applies credential revocations. A decrypted `ReliefRequestCreated` becomes `DEFERRED_UNSUPPORTED`. The application ledger then excludes that deferred message from later pending application. Therefore a sender can encrypt and queue a request, and the recipient can acknowledge its bytes, without the recipient creating an accepted request or updating routing, inventory, or triage.

The related M2 merge engine is wired into `domain/sync/RoomConflictCoordinator.kt:61`, which creates both conflicting revisions locally with simulated authors. There is no corresponding received-revision application and outgoing replication path. The local CRDT demonstration is useful, but it cannot prove two independent phones converge.

The existing `ProductionRequestFlowTest` manually decrypts the sender's persisted outbox. It stops before receiver application. This falls short of architecture steps 7 and 8 and the PRD's request delivery and distributed convergence requirements.

Required correction: implement recipient event validation, authorization, idempotent application, and affected projections. Connect signed mission changes and conflict resolutions to the same durable transport. Keep durable transport receipt distinct from accepted domain state.

Required regression: create a request on A, transfer it through B, assert exactly one accepted request on C after duplicates and restart, and verify C's affected state. Separately create edits on two databases, exchange operations in both orders, resolve unsafe conflicts, and compare their final hashes.

### R2. P1: A relay can send the message back to its sender and stop before delivery

Evidence: `apps/field-android/app/src/main/java/com/example/digitaldelta/service/MeshRelayService.kt:173`, `domain/mesh/PeerTransport.kt:36` and `:59`, `domain/mesh/NearbyConnectionsPeerTransport.kt:279`, and `domain/mesh/RoomMeshIngress.kt:65`.

Connected peers are sorted, and each dispatch offers all pending messages to a peer without filtering by destination or previous hop. Any durable receipt or duplicate rejection marks the entire message acknowledged.

Concrete code path: N4 sends a request through RLY-01 to N6. N4 remains connected to RLY-01 and sorts before N6. The relay sends the envelope back to N4. The originating request was not entered in N4's seen-message table, so N4 accepts it. Re-enqueue collides with its existing outbox row and is ignored, but ingress still signs a durable receipt. RLY-01 marks the message complete and does not send it to N6. A duplicate rejection from N4 also causes global completion.

This is a code-path finding, not a physical-radio reproduction. It defeats the specified A-to-B-to-C topology even before interruption testing.

Required correction: retain forwarding provenance and appropriate per-peer delivery state, avoid returning an envelope to its previous hop, prefer an available destination, and define when a relay can safely retire its copy.

Required regression: keep A connected while B can also reach C. Assert that C receives exactly one copy, including when A is first in peer order and when A returns a duplicate acknowledgement.

### R3. P1: Android never publishes field events to the observer

Evidence: `docs/ARCHITECTURE.md`, dashboard observation section; `apps/field-android/app/src/main/java/com/example/digitaldelta/di/AppModule.kt`; and `services/node/cmd/delta-drill/main.go:47`.

The Go observer service and browser client exist. The Android production source contains no ObserverService client, ManagedChannel, or observer publication call. The implemented publisher is the Go drill. A phone can complete a local action without producing any corresponding dashboard update.

The presence of gRPC dependencies and generated contracts does not establish this connection. The current laptop replay evidence proves drill-to-observer-to-browser behavior only.

Required correction: add a persistent, optional Android publication queue with event identity, retries, and replay-safe deduplication. Define the allowed observation fields before transmission; protect and authenticate the publication boundary. Laptop loss must never block the phone transaction.

Required regression: create an actual field request, observe the matching event ID on the dashboard, disconnect the laptop, perform more actions, then reconnect and verify complete ordered publication without duplicate field effects.

### R4. P1: The injected proof-of-delivery workflow uses incompatible per-phone demo identities

Evidence: `apps/field-android/app/src/main/java/com/example/digitaldelta/di/AppModule.kt:223`; `domain/pod/RoomProofOfDeliveryWorkflow.kt:100`, `:154`, and `:366`; `domain/pod/DeliveryOfferCodec.kt:157`.

The production dependency graph installs the default simulated scenario with `pod-demo-boat-02` and `pod-demo-hospital-01`, rather than the selected provisioned identities. Each phone independently creates its demo Keystore aliases. When another normally provisioned phone scans the offer, lookup of the demo sender misses the operational RLY-01 credential and the simulated fallback selects that receiving phone's own demo key. The sender keys differ, so verification rejects the offer with KEY_MISMATCH.

The same-phone signed demonstration works. It does not establish an operational two-phone handoff. This is missing profile, mission, and key wiring, not merely a camera test left to perform.

Required correction: construct operational offers from the selected provisioned custodian, actual recipient, and persisted mission state. Resolve the sender's independently installed credential on the other phone. Keep the same-phone demonstration as a separate explicit scenario.

Required regression: use two independent key stores and databases, create the offer on one and accept it on the other, then prove replay/tamper rejection and linked custody reconstruction.

### R5. P1: The map does not render the observer's actual route and rendezvous

Evidence: `apps/command/src/App.tsx:294`, `:366`, and `:454`; `apps/command/src/OfflineDeltaMap.tsx:5`; `apps/command/src/offlineMap.ts:62` and `:85`.

The text reads the observer's edge IDs, but the map receives only a water-route boolean and a risk boolean. It selects fixed E1/E3 or E6/E7 geometry. Other road routes, airway routes, arbitrary failed-edge IDs, and updated rendezvous coordinates never reach the renderer. The node map keeps R3 even when a received plan chooses R2. Non-waterway live routes are also labelled Truck, including airway routes.

Reproduced: publish a ROAD route with edgeIds `[E5]`. The mission geometry's active IDs are `E1,E3`, not `E5`. Real OSM-following polylines exist, but they are not selected from the live route.

Required correction: pass a typed mission projection containing active edges, failed edges, risk values, route mode, and rendezvous coordinates to the map. Derive labels and recommendations from that same projection.

Required regression: test multiple road routes, waterways, airways, R3-to-R2 replanning, and a no-route state. Assert agreement among map geometry, text, ETA, and decision instructions.

### R6. P1: The dashboard forgets still-valid mission state after 100 events

Evidence: `apps/command/src/App.tsx:293` and `:323`; `apps/command/src/projection.ts:32`.

Every new event truncates the observations array to its final 100 entries. The application then rebuilds the entire projection from that truncated array. A route or closure drops out of state as soon as 100 unrelated events follow it, even without a replacement route or reopening event. The UI can revert to the seeded road view while the observer is still connected.

Reproduced: accept a waterway route at sequence 1 and unrelated requests at sequences 2 through 101. The previously visible `Boat • E6 → E7` disappears.

Required correction: retain a complete projection or checkpointed state independently from the bounded visible event list. Specify reset and stream-generation behavior separately.

Required regression: publish more than 100 unrelated events after a closure, route, and rendezvous; assert retained state before and after reconnect/reload.

### R7. P1: A predicted risk is rendered as a confirmed road failure

Evidence: `apps/command/src/App.tsx:299` and `:304`; `apps/command/src/offlineMap.ts:96`.

Risk selects the waterway display. The map builder marks E3 failed whenever that waterway display is selected. A live waterway route also sets the application's failedRoad flag, regardless of why that route was chosen. A low probability prediction is enough to activate the risk path because the UI checks map size rather than a threshold or risk state.

Reproduced: one E3 prediction event, with no edge failure event, produces `failed=E3` in mission GeoJSON. This violates the explicit M7 rule that a prediction must remain separate from a confirmed closure.

Required correction: use confirmed edge-status events alone for closure state. Carry predicted probability, threshold, and observation age independently; choosing a boat does not prove a road is closed.

Required regression: high risk without closure, low risk, boat route without failure, confirmed closure, and later reopening must remain distinguishable.

## Standards, security, and reliability findings

### S1. P1: Mesh origin signatures are neither produced nor verified

Evidence: `packages/proto/digitaldelta/v1/common.proto:65`; `apps/field-android/app/src/main/java/com/example/digitaldelta/domain/mesh/MeshWireCodec.kt:29`; `domain/mesh/RoomMeshIngress.kt:94`; `domain/request/ReliefRequestSubmission.kt:103`.

The schema has sender_signature, but the envelope builder does not populate it and ingress does not verify it. The 32-byte payload hash is only checked for length at the relay. Nearby proves the immediate neighbor's identity, not the origin of a relayed message. Expiry, hop limit, priority, and simulation metadata are also absent from the encrypted payload's associated data.

An authenticated relay can modify these fields and receive a durable acknowledgement from the next node. Recipient encryption still protects content confidentiality, but it does not provide the documented origin authentication or signed simulation marker.

Required correction: define canonical origin-signed immutable fields and ciphertext integrity, distinct from mutable relay-hop metadata. Check origin authority and credential state before admission. Add tampered-metadata and wrong-origin tests across more than one hop.

### S2. P1: A peer whose revocation is already known can authenticate again

Evidence: `apps/field-android/app/src/main/java/com/example/digitaldelta/domain/mesh/PeerIdentityAuthentication.kt:80`; `domain/identity/RecipientProvisioningRepository.kt:43` and `:70`; `domain/mesh/NearbyConnectionsPeerTransport.kt:310`.

verifyProof accepts a valid old credential and a fresh signature, reinstalls the credential, and returns true without consulting its preserved revocation status. The repository preserves revocation in Room but returns a RecipientEncryptionKey with revokedAtUnixMs=null. Existing authenticated sessions also rely on map membership rather than reevaluating credential state.

Concrete code path: B has received A's signed revocation, but A has not. A answers B's next challenge using its original key. B authenticates A despite already possessing the revocation. This differs from the documented unavoidable case where an offline device has not learned of revocation.

Required correction: check authoritative local revocation state during proof acceptance and invalidate established sessions when their credential expires or is revoked. Test a revoked-but-cryptographically-valid peer with a fresh challenge.

### S3. P1: Background revocation leaves the open ViewModel authorized

Evidence: `apps/field-android/app/src/main/java/com/example/digitaldelta/ui/main/MainScreenViewModel.kt:446` and `:453`; `domain/identity/CredentialRevocationInboxProcessor.kt:69`.

authorize reads a cached IdentityUiState credential. Background inbox processing updates Room, but no credential subscription refreshes the open ViewModel. Requests, conflict decisions, preemption, and custody can therefore continue using a snapshot that says the identity is not revoked until the user reloads identity state or restarts.

The existing visible revocation test imports through the identity screen, which explicitly reloads that snapshot. It does not cover revocation received through the mesh while the screen remains open.

Required correction: enforce current credential state at the action boundary and update displayed permissions from authoritative storage. Test background local-identity revocation followed by every protected action without manual refresh.

### S4. P1: A non-finite observation permanently blocks subsequent SSE replay

Evidence: `services/node/internal/observer/store.go:49`; `services/node/internal/observer/http.go:54`.

The observer validates identifiers but permits NaN and infinity in Protobuf floating-point fields. Such an event persists successfully. JSON serialization later fails, causing the SSE handler to return before advancing past it. Every ordinary reconnect starts before the same bad record and fails again, hiding valid later events.

Reproduced against actual Go services: publish a NaN risk event at sequence 1 and a valid event at sequence 2. Both initial and repeated SSE requests return HTTP 200 with no observation bytes. Sequence 2 is unreachable through normal replay.

Required correction: validate finite and sensible numeric values before persistence, and define a visible recoverable handling path for invalid records already retained. Test invalid probabilities and coordinates followed by a valid event, including database reopen and cursor replay.

### S5. P1: Anonymous clients can forge the optional public archive

Evidence: `services/headquarters-archive/src/index.ts:35` and `:65`.

The write endpoint has no publisher authentication. CORS accepts requests without Origin, and an IP rate limit does not establish identity. An anonymous client can submit another node ID and simulated=false. The event-ID uniqueness rule lets an earlier forged insertion prevent a legitimate insertion with the same ID.

Reproduced against the Worker handler with mocked D1: an unsigned, no-Origin, non-simulated observation returns 201 and reaches insertion. No request was sent to the deployed archive.

Impact is limited to the optional archive's provenance and integrity; it does not alter field operations. Required correction: authenticate the trusted publisher at a server boundary, bind event source to publisher authority, and avoid embedding a privileged publishing secret in the public browser bundle. Test anonymous and wrong-source rejection.

### S6. P2: The archive's 4 KB body limit can be bypassed

Evidence: `services/headquarters-archive/src/index.ts:38`.

The handler checks optional Content-Length and then buffers request.json(). A body without the header skips the size test. Reproduced: 1 MiB of whitespace followed by a small valid observation returns 201.

Required correction: enforce a byte-counted maximum while consuming the body, independent of Content-Length. Test omitted, incorrect, and over-limit lengths.

### S7. P2: The Go mesh harness does not satisfy the documented secure mesh contract

Evidence: `services/node/internal/mesh/store.go:109` and `:119`; `services/node/internal/mesh/store_test.go:90`.

The Go node accepts absent encrypted payloads/signatures and arbitrary 32-byte hashes. Its acknowledgements omit node_signature. A client enforcing the Android signed-acknowledgement rules cannot accept these responses. Existing tests intentionally construct this reduced envelope as their valid fixture.

The architecture calls the Go node an engineering aid, so this is not evidence of an Android transport bypass. It means the 10,000-stream result measures a reduced storage/transport workload, not the full secured interoperable protocol.

Required correction: either implement the declared secure node contract with compatible signed acknowledgements, or clearly isolate and name the reduced load-test endpoint and constrain performance claims to that workload.

### S8. P2: The configured archive rejects the documented local fair origin

Evidence: `services/headquarters-archive/wrangler.jsonc:9`; `scripts/run-demo-local.sh`; README local URL.

The archive allows http://localhost:3000 but not http://127.0.0.1:3000, which is the documented fair URL. Those are distinct browser origins. Enabling the archive from that default page results in a 403.

Required correction: align the configured origin list with supported local entry points. Add exact-origin handler tests for both accepted URLs and an unrelated rejected origin.

### S9. P2: Switching the dashboard to English leaves its document language as Bangla

Evidence: `apps/command/app/layout.tsx:15`; `apps/command/src/App.tsx:285` and `:427`.

The root html element is fixed to lang=bn. The language button changes React copy but does not set an English lang attribute on the document or the main content. Browser inspection after switching to English still reports document.documentElement.lang as bn. This gives assistive technology incorrect language metadata. Map control labels also stay English in Bangla mode, and several operational labels are hard-coded in English.

Required correction: synchronize content language metadata and translate actionable labels. Test the computed language of the main content after each switch and review screen-reader behavior in both languages.

## Evidence and claim corrections

- README's opening status says the software package is complete and only physical/organizational evidence remains. Findings R1 through R4 contradict that scope. A later README paragraph already says the modules are integrated foundations rather than complete, so the status section is internally inconsistent.
- The 61 connected-test results from 2026-09-04 remain historical evidence of those tests. They do not prove cross-phone recipient application, operational key exchange for PoD, distributed edit convergence, or phone-to-observer publication.
- The browser still mixes seeded state with observations. Node counts, batteries, inventory, custody status, and parts of the decision rail come from the local scenario reducer. They should be presented as the labelled drill until those panels consume corresponding accepted events. A live observer badge is not proof that every displayed panel is live.
- M6 and M8 have useful local simulated workflows. M6 consumes the route ETA but uses fixed simulated cargo, elapsed time, and waypoint assumptions. That is acceptable demonstration scope when labelled, but does not establish that received requests drive triage.
- The ML pipeline reproduces its synthetic data and model. Its documentation correctly limits the held-out score to synthetic labels. Real flood accuracy remains unestablished; this review did not relabel that disclosed limitation as a software defect.
- The map source and route-geometry checksums pass. The remaining route-rendering finding concerns selection of geometry from event state, not whether the packaged basemap is real.
- Prior load and emulator memory reports are appropriately limited to their recorded machines. This review did not repeat the 10,000-connection run, phone RAM measurements, physical radios/cameras, or three unchanged offline rehearsals.
- Market-research sources, organizer acceptance, and current fair logistics were not independently reverified during this engineering review.

## Recommended implementation sequence

1. Make one real request survive A-to-B-to-C forwarding and become accepted state on C. Include sender signatures, revocation enforcement, duplicates, and restart in that slice.
2. Connect independent-phone provisioning and custody to those actual mission identities. Exchange mission edits and resolutions through the same event application path.
3. Publish the resulting phone events to the optional observer. Add validation that prevents invalid records from poisoning replay.
4. Build every dashboard decision and map layer from a durable projection, retaining mission state beyond the visible event-history limit. Keep prediction, closure, and simulation provenance separate.
5. Secure the optional archive, align local deployment settings, and complete bilingual accessibility checks.
6. Run the physical device matrix and timed rehearsals on the resulting unchanged build. Update completion claims and pitch evidence only after these paths pass.

The review identifies seven requirements/integration findings and nine standards/security findings. The central requirements failure is that a request acknowledged by the recipient is not applied. The main security failures are missing origin signatures and acceptance of locally known revoked authority.
