# Digital Delta technical and market report

## Offline disaster logistics and verified relief delivery in Bangladesh

Prepared for Bangladesh Innovation Fair 2026  
Prepared by Touhidul Alam Seyam  
Individual project lead and developer  
Report date 4 September 2026

## Executive summary

Digital Delta is a bilingual Android and web system for relief teams who must keep working after commercial internet, electricity, or roads fail. A clinic can record an urgent request on one phone. Nearby Android phones can store and carry its encrypted Protobuf envelope. The field app calculates routes for trucks, boats, and simulated drones, warns when a priority deadline is likely to fail, and records signed custody receipts. A local Next.js headquarters screen can observe the event history, but field operations do not depend on that laptop.

The project implements the eight modules in the HackFusion disaster resilience brief. The strongest work is the shared event contract and the way one mission passes through identity, offline storage, relay, routing, priority, risk, and custody. The fair build also has limits. It has passed automated tests and bilingual journeys on Android 15 and Android 16 emulators. It has not yet passed the required three-phone radio test, real-camera airplane-mode test, or repeated booth rehearsal. The route-risk model runs on the phone, but its training data is synthetic and cannot be described as operational flood prediction.

Bangladesh already has disaster dashboards and formal coordination bodies. The practical gap is continuity in the field when those teams cannot reach a server. The first useful deployment would be a controlled district exercise run beside existing radio, phone, and paper procedures. The system should earn trust through measured relay success, battery use, route latency, task completion, and receipt integrity before any operational use.

## The problem in Bangladesh

Flash floods repeatedly isolate the northeast. The Bangladesh Department of Disaster Management classifies Sunamganj as very high risk and Sylhet and Netrakona as high risk. Its AWARE system identifies the haor region as a flash-flood area. In June 2022, a government-endorsed humanitarian plan estimated that 7.2 million people were affected across nine northeastern districts. About 472,856 people moved to roughly 1,605 shelters. [Department of Disaster Management risk information](https://rapid.ddm.gov.bd/risk/riskinfo) [United Nations Bangladesh 2022 response plan](https://bangladesh.un.org/en/188010-flash-floods-humanitarian-response-plan-2022-united-nations-bangladesh-coordinated-appeal)

The operational failures arrived together. A United Nations situation update dated 19 June 2022 reported that authorities stopped electricity in Sylhet and Sunamganj to prevent accidents. Mobile communication became difficult, highways went under water, road connections broke, rescue boats were scarce, and Osmani International Airport suspended operations after water reached the runway. A separate account describes a Sunamganj hospital sheltering about 25,000 people while midwives worked for three days without electricity or telecommunications. [United Nations Bangladesh situation update](https://bangladesh.un.org/sites/default/files/2022-06/situation_overview_flash_flood_final_draft_.pdf) [United Nations Bangladesh account from Sunamganj](https://bangladesh.un.org/en/195588-pregnant-midwife-serves-pregnant-mothers-during-sylhet-floods)

The pattern continued in 2024. A joint response plan recorded about 3.74 million affected people across Habiganj, Kishoreganj, Moulvibazar, Netrakona, Sunamganj, and Sylhet. It estimated that 1.4 million people needed urgent assistance. The logistics section records disrupted roads and storage, identifies Gowainghat as heavily cut off, and calls for a regional temporary logistics hub with an information centre. [Bangladesh Cyclone and Monsoon Floods Humanitarian Response Plan 2024 to 2025](https://bangladesh.un.org/sites/default/files/2024-10/Bangladesh-HCTT-Humanitarian%20Response%20Plan-Cyclone%20and%20Monsoon%20Floods%202024-29-09-2024%20%28June%202024%20to%20March%202025%29.pdf)

These records support a narrow claim. Relief teams need a way to preserve requests, routing decisions, and delivery records during a communications outage. They do not prove that every tower, road, and power line fails in every flood. The original hackathon brief stated that 5.2 million people were displaced in Sylhet, Sunamganj, and Netrokona within 72 hours in July 2025. This project has not found an authoritative source for that figure and does not use it in the public pitch.

## Product scope

The field application supports the following operational path:

1. An administrator provisions a device identity and role without third-party login.
2. A clinic or relief point records a request and priority while offline.
3. The phone encrypts the event for its intended recipient and stores it in a persistent outbox.
4. Nearby phones authenticate each other, accept the envelope, store it before acknowledging it, and forward it later.
5. The routing engine selects a mode-valid path and recalculates after a confirmed closure or a high-risk prediction.
6. The triage engine compares the expected arrival against the cargo deadline and proposes a safe preemption when needed.
7. Both parties sign a QR handoff. The receipt links to the previous receipt hash.
8. A local observer can rebuild the headquarters view from ordered sanitized events.

The fair build controls no drone, boat, sensor, or IoT device. Environmental values and vehicle movement are simulated and labeled. The system does not replace medical triage, evacuation orders, aviation approval, or the authority of Bangladesh disaster management bodies.

## Users and authority

The likely field users are union and upazila disaster committee members, trained volunteers, clinics, hospitals, drivers, boat operators, and relief logisticians. District authorities and implementing organizations would own the operating procedure, identity issuance, map package, retention policy, and training. Affected households are beneficiaries and should not pay for the service.

Bangladesh already has a distributed public disaster structure. The Standing Orders on Disaster and the national plan define committees at district, upazila, union, pourashava, and city levels. The Humanitarian Coordination Task Team complements that government structure. The application should fit those responsibilities instead of inventing a parallel command system. [Standing Orders on Disaster 2019](https://modmr.gov.bd/pages/policies/standing-orders-on-disaster-2019-english-version-2296f0-6940313ac4774958d7b41e98) [National Plan for Disaster Management](https://ddm.gov.bd/sites/default/files/files/ddm.portal.gov.bd/page/332124ba_20b1_42cc_816b_d97d7eb4f478/npdm_final.pdf) [HCTT special meeting April 2024](https://bangladesh.un.org/en/273208-bangladesh-humanitarian-coordination-task-team-hctt-special-meeting-22-april-2024)

## System architecture

The Android field application is the operational core. It uses Jetpack Compose, Navigation 3, Room, Proto DataStore, Android Keystore, WorkManager, CameraX, bundled ML Kit barcode recognition, Nearby Connections, ONNX Runtime, and MapLibre Native. Room holds the operation log, mesh queues, replay claims, public-key directory, conflicts, assignments, and custody history. Private signing and encryption keys remain in Android Keystore.

The mesh contract is Protocol Buffers. Android uses framed Protobuf over Nearby Connections. The Go node uses gRPC and Protobuf on supported IP links. Relays can inspect routing metadata such as sender, recipient, expiry, hop count, and priority. They do not receive the AES content key.

The Go observer assigns a monotonic sequence to sanitized presentation events and stores them in BoltDB. A local Server-Sent Events endpoint projects an allow-listed JSON view to the browser. JSON is permitted here because this is a browser presentation boundary, not mesh transport. The projector can disconnect and replay from its last sequence. Field devices continue independently.

The headquarters interface uses Next.js 16 App Router, React, TypeScript, shadcn/ui, Tailwind CSS, MapLibre, and a checksum-pinned PMTiles archive derived from OpenStreetMap. The public Vercel deployment is a reviewer convenience. The fair uses the local Next.js build and local observer. An optional Cloudflare Worker stores a small allow-listed summary in D1. It never receives encrypted envelopes, credentials, signatures, medical fields, or authoritative field state.

## Module implementation

### Module 1 identity and authorization

Each phone creates separate RSA-2048 signing and encryption identities. The private keys are non-exportable. An offline administrator signs a role credential that binds node ID, identity, role, encryption key, signing key, issue time, expiry, and nonce. The app checks that credential again at each protected action. Selecting a profile does not grant its permissions.

The app stores only a salted PBKDF2-HMAC-SHA256 verifier for the six-digit local PIN. Five failed attempts trigger a persisted 30-second lockout. Signed RSA-PSS authorization records form a hash chain. Revocation claims are signed, encrypted per known recipient, relayed as Protobuf envelopes, and applied once.

Automated tests cover valid, expired, revoked, malformed, future-dated, wrong-role, wrong-target, untrusted-issuer, profile mismatch, key mismatch, and stale-credential cases. A real two-phone camera provisioning pass remains a release gate.

### Module 2 distributed records and conflict handling

The application appends Protobuf operations to Room and compares vector clocks. Receipt and audit identifiers use grow-only sets. Assignments use an observed-remove set with explicit tombstones. Inventory changes use a per-replica PN-counter. Concurrent changes to destination, priority, or medical quantity do not use wall-clock time as authority. They open a bilingual conflict screen for an authorized human.

The resolver records the choice, merged vector clock, policy version, and a SHA-256 convergence hash. Tests cover concurrent edits, duplicates, late arrival, deletion, re-addition after a tombstone, restart, and clock skew.

### Module 3 authenticated store and forward relay

Nearby Connections uses the cluster strategy. A visible foreground service advertises and discovers while active. Both users first compare Nearby digits. Each phone then issues a fresh nonce challenge and requires an administrator-signed credential plus an RSA-PSS proof over the transcript. Envelopes and acknowledgements remain blocked until authentication succeeds.

The receiver writes the seen-message claim, inbox bytes, and next-hop outbox entry in one Room transaction before sending a signed durable acknowledgement. TTL, hop limits, duplicate rejection, bounded retry, dead letters, and restart recovery are implemented. The broadcast interval increases from 10 seconds to 25 seconds below 30 percent battery, a 60 percent reduction in broadcast frequency. Urgent items use a shorter base interval.

The deterministic and connected tests cover interrupted sends, forged acknowledgements, duplicate envelopes, TTL and hop rejection, Room restart, and encrypted forwarding. A physical A to B to C relay with the middle phone powered off and restored remains the main missing proof.

### Module 4 multimodal routing

The routing engine validates a directed graph with road, waterway, and airway edges. It normalizes the supplied `river` value to `WATERWAY`. Deterministic Dijkstra routing enforces vehicle constraints and excludes failed edges. For the seeded mission, a truck uses E1 and E3. When simulated flooding closes E3, the truck path fails and the engine selects boat edges E6 and E7.

MapLibre Native renders a local Sylhet extract with 1,576 OpenStreetMap-derived features. The Next.js dashboard reads a local PMTiles archive. Both packages retain attribution and checksum verification. Route calculation records monotonic latency, but the final median and p95 measurement must run on the target phones.

### Module 5 signed proof of delivery

The sender builds a Protobuf offer with delivery ID, sender identity and public key, payload hash, nonce, timestamp, recipient, mission, and previous receipt hash. Android Keystore signs the canonical bytes with RSA-PSS. The recipient checks the credential, signature, delivery fields, hash, nonce, time window, and intended recipient without internet.

Nonce use and custody insertion occur in one Room transaction. Replaying the same QR cannot create another receipt. Each receipt commits to the previous receipt hash, so the device can reconstruct and verify the local chain. Tests distinguish altered content, unknown signer, wrong recipient, expired credential, expired offer, and replay. The scanner and bundled model are in the APK, but real-camera airplane-mode evidence is still required.

### Module 6 priority and preemption

The policy defines P0 critical medical cargo with a two-hour deadline, P1 high priority with six hours, P2 standard with 24 hours, and P3 low priority with 72 hours. The engine calculates baseline arrival and a 30 percent slowdown case. It treats arrival exactly at the deadline as protected.

Only P0 or P1 may preempt P2 or P3. The engine keeps other urgent cargo in the queue, rejects stale route estimates, and selects a safe lower-priority drop waypoint. A coordinator must confirm the change. The event records the policy, reason, confirmer, affected cargo, waypoint, and estimated gain. Room commits the event and the vector-clocked assignment atomically.

### Module 7 local route risk

The current model is logistic regression trained on a reproducible synthetic dataset. Its inputs are rainfall rate, elevation, and soil saturation. The held-out test produced precision 0.612766, recall 0.837209, and F1 0.707617, with confusion matrix `[[374, 182], [56, 288]]`. Maximum ONNX parity error was `8.94069672e-08`.

Android runs the model locally through ONNX Runtime. A high probability adds a cost to the route graph. It does not mark the edge closed. The UI shows the simulated inputs, probability, threshold, runtime, and the reason for any proactive route change. This proves the inference and integration path, not real flood accuracy. Historical local data and operational review are required before field use.

### Module 8 hybrid fleet handoff

The bundled graph contains air-only destination N7. The engine classifies it as drone-required, evaluates candidate rendezvous points, and minimizes final delivery time while preserving a 20 percent battery reserve. The initial plan chooses R3. A simulated 18-minute boat delay and updated position trigger a new calculation that selects R2. Room stores both the delay and revised rendezvous before the handoff continues.

The boat and simulated drone use the Module 5 handoff protocol. Both signatures appear in the receipt chain. Android 15 and Android 16 emulator journeys cover no feasible rendezvous, low battery, an unreachable graph, destination change, and delayed-boat replanning. No screen claims that a physical drone moved.

## Bilingual and accessible operation

Bangla and English strings ship in the same APK. Bangla appears first during initial setup. The choice is stored in Proto DataStore, and changing language does not clear request, identity, conflict, routing, or custody state. Noto Sans Bengali is bundled with the app, so text does not depend on a downloaded font.

The local gate requires identical resource keys and rejects literal English labels in critical Compose screens. Connected tests exercise Bengali combining marks and the primary shell at 150 percent font scale. Critical controls have accessibility descriptions and keyboard-compatible web controls. Final WCAG and TalkBack evidence still requires a complete device matrix and human review.

## Security and privacy boundaries

The system uses RSA-2048 PSS with SHA-256 for signatures, RSA-OAEP to wrap random AES keys, AES-256-GCM for content, and SHA-256 hashes. Each encrypted payload binds routing metadata through associated data. Relay nodes cannot unwrap the content key.

Cryptography does not make a false report true. A trusted but mistaken user can still enter bad information. Predictions can also be wrong. The app therefore shows provenance and simulation status, requires humans for unsafe conflicts and assignment changes, and keeps official closures and trained judgment authoritative.

Humanitarian records create privacy risks even when offline. A phone may carry copies longer during an outage. A real deployment needs a data-impact assessment, minimal fields, retention limits, a lost-device process, key rotation, revocation drills, and approved post-incident deletion. Beneficiary names, diagnoses, national IDs, phone numbers, and precise household coordinates should remain outside the default workflow. The ICRC and OCHA both treat responsible data handling as part of humanitarian protection. [ICRC Handbook on Data Protection in Humanitarian Action](https://www.icrc.org/en/data-protection-humanitarian-action-handbook) [OCHA Data Responsibility Guidelines 2025](https://centre.humdata.org/data-responsibility-guidelines-2025/)

## Verification evidence

The repository uses local verification only. It has no GitHub Actions workflow. `scripts/verify-local.sh` lints the shared schema, checks bilingual resource parity, verifies font and map hashes, reproduces the ML artifacts, runs Android unit tests, builds debug and minified release APKs, checks the bundled barcode model, runs Go race tests and vet, builds all Go commands, tests and builds the Next.js dashboard, and tests the Cloudflare archive with a Wrangler dry run.

The connected suite contains 56 Android tests after the large-text case. Earlier unchanged runs passed 55 tests on both Android 15 and Android 16 emulators. Seventeen dashboard tests cover bilingual content, deterministic scenarios, observer reconnect, local maps, security rejections, archive sanitization, and projector viewports. Go tests cover durable mesh restart, gRPC acknowledgements, signed provisioning and revocation, observer persistence, cursor replay, SSE sanitization, and deterministic drills.

A Go load run opened 10,000 independent gRPC streams. Each stream received a durable acknowledgement and remained open for five seconds under the recorded local conditions. This is a concurrency demonstration, not a claim about district radio capacity or production throughput.

## Market and alternatives

The market is public-interest deployment for government and relief organizations. It is not a consumer subscription market. The first buyer would need authority to own devices, identities, training, maps, incident records, and support.

Sahana Eden is the closest domain alternative because it covers disaster requests, warehouses, inventory, shipments, vehicles, and maps. Ushahidi supports crisis reporting and mapping. KoboCollect supports offline forms. Briar provides encrypted nearby communication. Meshtastic offers off-grid mesh communication through additional LoRa hardware. DDM already lists emergency dashboards, GIS, shelters, and committee databases. [Sahana Eden](https://sahanafoundation.org/products/eden/) [Ushahidi features](https://www.ushahidi.com/features/) [KoboCollect offline collection](https://support.kobotoolbox.org/data_collection_kobocollect.html) [Briar manual](https://briarproject.org/manual/) [Meshtastic documentation](https://meshtastic.org/docs/) [DDM internal services](https://ddm.gov.bd/pages/internal-eservices)

The project combines a different set of functions in one phone workflow: encrypted store and forward relay, conflict-aware records, multimodal routing, priority preemption, and signed custody in Bangla and English. It should not claim to be the first or only system without a wider product and patent search.

Market size should use deployments and trained nodes. Bangladesh population or mobile subscriptions are poor revenue measures. A practical serviceable area is the six northeastern districts covered in the 2024 assessment, including about 64 upazilas and 360 unions. The first obtainable deployment is one district exercise with a small number of local teams, one health referral path, and one experienced logistics partner.

Revenue or cost recovery could come from deployment preparation, integration, security review, training, offline map packaging, exercise support, and maintained releases. The field app should remain usable without a cloud subscription during an incident.

## Pilot plan

The first phase is sponsorship and workflow mapping. One district authority, health facility, and experienced relief partner should map the app roles to current procedures and decide who owns the data.

The second phase is a controlled exercise on the exact Android phones intended for use. It should measure radio range, A to B to C success, restart recovery, QR scanning in airplane mode, battery consumption, route latency, memory, Bangla and English task completion, and operator errors.

The third phase is a shadow pilot during a scheduled drill or low-risk logistics exercise. Existing authorized procedures remain the source of operational authority. The team compares the digital event trail with the official log afterward.

A limited operational pilot should begin only after security, usability, radio, battery, and governance gates pass. Any later integration should export approved summaries to existing government or partner systems rather than create another isolated dashboard.

## Commercial and operational risks

Institutional ownership is the largest adoption risk. Someone must issue roles, revoke credentials, update maps, train users, and keep devices charged between monsoons. A procurement plan without that owner is not credible.

Android radios vary by handset and environment. Nearby discovery, background limits, camera quality, battery health, and vendor firmware will affect results. The project needs a published tested-device list and measured range rather than a general claim that it works anywhere.

The route model may produce a mathematically valid but unsafe path. Operators must be able to reject it, and official closure information must override predictions. The current synthetic classifier can support a technical demonstration only.

Funding is another practical constraint. The 2024 response plan records that proposed logistics activities received no allocation. A small preparedness exercise is more realistic than asking for a national deployment during an emergency.

## Sustainable development and inclusion

The project relates most directly to SDG 3 on health, SDG 9 on resilient infrastructure, SDG 11 on resilient communities, SDG 13 on climate action, and SDG 16 on accountable institutions. The link is operational. The system tries to preserve urgent medical requests, keep logistics records available during outages, and leave an auditable custody trail.

Bangla-first operation reduces a basic language barrier for local volunteers and public staff. English remains bundled for mixed teams and external partners. A real inclusion plan must go further. It needs testing with women responders, people with disabilities, older users, low-literacy participants, and communities with weak device access. The current accessibility work is technical evidence, not proof of inclusive field adoption.

## Demonstration plan

The live demonstration should start with all phones disconnected from commercial internet. The presenter shows a Bangla clinic request, the encrypted outbox, and the role boundary. A middle relay phone receives the envelope and loses contact before forwarding it. After restart, it forwards one copy and rejects the duplicate.

The projector then shows the same mission through the local observer. A simulated E3 failure removes the truck route and selects the boat route. The longer ETA triggers a P0 breach warning and a coordinator-confirmed P2 drop. Simulated route-risk inputs then add an advisory cost without claiming a closure.

The final sequence shows the drone-required destination, the R3 rendezvous, an 18-minute simulated boat delay, replanning to R2, and the signed boat-to-simulated-drone handoff. The recipient accepts one QR. A second scan fails as a replay, and an altered QR fails signature verification. The presenter disconnects the headquarters laptop while the phones complete a field action, then reconnects the observer and shows ordered replay.

## Current status and release gates

All eight modules have working code paths and automated evidence. The command dashboard is live on Vercel at https://digital-delta-headquarters.vercel.app, and the public repository is at https://github.com/Seyamalam/digital-delta.

The project is not yet field verified. The following evidence must remain open until performed:

- three physical Android phones completing A to B to C relay with no commercial internet;
- restart recovery of the middle relay without duplicate domain effects;
- real-camera provisioning and proof-of-delivery scanning in airplane mode;
- route latency, memory, battery, and relay measurements on the target phones;
- TalkBack, large-text, Bangla wrapping, and small-screen review by people;
- field completion while the command laptop and projector are off;
- twenty rehearsals and three unchanged successful final passes;
- organizer confirmation of participation, event date, pitch duration, and booth equipment.

These are release gates, not hidden defects. The repository tracks them in `TODO.md`, `SCREENSHOTS.md`, and `docs/PHYSICAL_DEVICE_TEST.md`.

## Team

Touhidul Alam Seyam is the individual project lead and developer. The work covers product design, Android development, Go services, cryptography integration, routing and ML integration, the Next.js headquarters interface, testing, evidence capture, and the fair package. The public GitHub profile lists Agentic Institute as the current affiliation. No additional team member is recorded for this submission.

## Conclusion

Digital Delta already demonstrates the difficult software boundary: a relief mission can exist on field phones, move as encrypted Protobuf, change route and priority, and produce signed custody evidence without a central server. The headquarters screen helps people understand the mission but does not own it.

The next decision should be whether an operational sponsor will run a controlled three-phone exercise. Passing that exercise matters more than adding another dashboard feature. It would turn a strong emulator and automated-test result into evidence about the phones, radios, people, and procedures that decide whether the system is useful during a flood.

## Reference register

- Bangladesh Department of Disaster Management AWARE risk information: https://rapid.ddm.gov.bd/risk/riskinfo
- Bangladesh Department of Disaster Management internal services: https://ddm.gov.bd/pages/internal-eservices
- Ministry of Disaster Management and Relief Standing Orders on Disaster 2019: https://modmr.gov.bd/pages/policies/standing-orders-on-disaster-2019-english-version-2296f0-6940313ac4774958d7b41e98
- National Plan for Disaster Management: https://ddm.gov.bd/sites/default/files/files/ddm.portal.gov.bd/page/332124ba_20b1_42cc_816b_d97d7eb4f478/npdm_final.pdf
- United Nations Bangladesh Flash Floods Humanitarian Response Plan 2022: https://bangladesh.un.org/en/188010-flash-floods-humanitarian-response-plan-2022-united-nations-bangladesh-coordinated-appeal
- United Nations Bangladesh Severe Flash Floods Situation Update 1: https://bangladesh.un.org/sites/default/files/2022-06/situation_overview_flash_flood_final_draft_.pdf
- Bangladesh Cyclone and Monsoon Floods Humanitarian Response Plan 2024 to 2025: https://bangladesh.un.org/sites/default/files/2024-10/Bangladesh-HCTT-Humanitarian%20Response%20Plan-Cyclone%20and%20Monsoon%20Floods%202024-29-09-2024%20%28June%202024%20to%20March%202025%29.pdf
- ICRC Handbook on Data Protection in Humanitarian Action: https://www.icrc.org/en/data-protection-humanitarian-action-handbook
- OCHA Data Responsibility Guidelines 2025: https://centre.humdata.org/data-responsibility-guidelines-2025/
- Bangladesh Innovation Fair official application: https://www.innovationfairbd.org/innovator-application-form?type=innovator
- Repository architecture and evidence: https://github.com/Seyamalam/digital-delta
