# Market landscape and real-world case

Research and access date: **2026-09-04**

This note uses Bangladesh government, United Nations, World Bank, ITU, ICRC, and first-party product sources wherever possible. Facts are separated from project hypotheses. A dash in the competitor table means that a capability was not established in the cited official material; it is not proof that the capability can never be added.

## Executive conclusion

The strongest case for this project is not that Bangladesh lacks disaster dashboards. The Department of Disaster Management already lists an Emergency Operational Dashboard, GIS Map, shelter system, and Disaster Management Committee database among its internal services. The gap worth testing is a **field execution layer that continues when connectivity is unreliable**: signed requests, phone-to-phone relay, constrained routing, priority decisions, and verifiable handoffs, with the headquarters view treated as optional. [DDM internal e-services](https://ddm.gov.bd/pages/internal-eservices)

The need is credible and recurrent. Government and UN reporting has documented large northeastern floods, deliberate power shutdowns for safety, impaired mobile communications, submerged roads, disrupted storage, and difficulty moving supplies. The 2024 response plan also called for a regional temporary logistics hub with an information centre. [UN Bangladesh 2022 situation update](https://bangladesh.un.org/sites/default/files/2022-06/situation_overview_flash_flood_final_draft_.pdf) [Bangladesh Cyclone and Monsoon Floods Humanitarian Response Plan 2024-2025](https://bangladesh.un.org/sites/default/files/2024-10/Bangladesh-HCTT-Humanitarian%20Response%20Plan-Cyclone%20and%20Monsoon%20Floods%202024-29-09-2024%20%28June%202024%20to%20March%202025%29.pdf)

The market is best understood as **public-interest B2G and B2NGO deployment**, not a consumer app market. Likely users include district, upazila, and union disaster committees, relief organizations, health facilities, logistics partners, and trained volunteers. Affected residents are beneficiaries, not the default paying customer. The first commercial question is therefore not “How many people live in Bangladesh?” but “Which operational owner will sponsor a controlled pilot, maintain identities and maps, train staff, and include the tool in an approved response procedure?”

The defensible product position is integration. Sahana Eden manages disaster resources and logistics; Ushahidi and KoboToolbox collect crisis data; Briar provides secure phone-to-phone communication; and Meshtastic provides long-range off-grid messaging with extra radio hardware. Among the reviewed official materials, none combines phone-only encrypted relay, conflict-safe operational records, multimodal routing, priority preemption, and signed chain of custody in one Bangla-and-English relief workflow.

## Evidence of the problem

### Flood exposure is structural, not a one-off event

Bangladesh DDM's AWARE risk table classifies Sunamganj as **very high risk** and Sylhet and Netrakona as **high risk**. It identifies Sylhet, Sunamganj, and Moulvibazar as haor and flash-flood areas. [DDM AWARE risk information](https://rapid.ddm.gov.bd/risk/riskinfo)

In June 2022, the Government-endorsed humanitarian response plan estimated that **7.2 million people were affected** across nine northeastern districts and that 472,856 people had been taken to about 1,605 shelters. The plan prioritized Sunamganj, Netrakona, Sylhet, Habiganj, and Moulvibazar. [UN Bangladesh Flash Floods Humanitarian Response Plan 2022](https://bangladesh.un.org/en/188010-flash-floods-humanitarian-response-plan-2022-united-nations-bangladesh-coordinated-appeal)

In June 2024, a joint response plan recorded about **3.74 million affected people** across Habiganj, Kishoreganj, Moulvibazar, Netrakona, Sunamganj, and Sylhet, spanning roughly 64 upazilas and 360 unions. It estimated that 1.4 million people needed urgent life-saving assistance and that 723,331 people had sought refuge elsewhere at the height of displacement. [Bangladesh Cyclone and Monsoon Floods Humanitarian Response Plan 2024-2025](https://bangladesh.un.org/sites/default/files/2024-10/Bangladesh-HCTT-Humanitarian%20Response%20Plan-Cyclone%20and%20Monsoon%20Floods%202024-29-09-2024%20%28June%202024%20to%20March%202025%29.pdf)

The recurrence has continued. In April 2026, WFP activated forecast-triggered assistance in Moulvibazar and Habiganj ahead of expected peak flooding, demonstrating that anticipatory action remains operationally relevant in the northeast. [WFP anticipatory action in Sylhet Division, April 2026](https://www.wfp.org/news/early-action-saves-lives-wfp-activates-assistance-bangladesh-ahead-peak-flooding)

### Power, connectivity, and transport can fail together

The 19 June 2022 UN Resident Coordinator update reported that electricity in Sylhet and Sunamganj was stopped to prevent accidents, mobile communication became difficult because of the electricity disruption, major highways were submerged, road connectivity had snapped, and boats were scarce for search and rescue. Osmani International Airport also suspended operations after water reached the runway. [UN Bangladesh Severe Flash Floods Situation Update 1](https://bangladesh.un.org/sites/default/files/2022-06/situation_overview_flash_flood_final_draft_.pdf)

A UNFPA account from the same flood describes a Sunamganj hospital sheltering around 25,000 people while midwives worked for three days without electricity or a functioning telecommunications network. This is direct evidence that health and relief work may continue after both services fail. [UN Bangladesh account from Sunamganj](https://bangladesh.un.org/en/195588-pregnant-midwife-serves-pregnant-mothers-during-sylhet-floods)

The 2024 response plan reports that severe flooding in Sylhet and Sunamganj disrupted road communication and storage facilities, affecting distribution of food and non-food items. It specifically identifies Gowainghat as heavily cut off, describes gaps in logistics capacity, and calls for a regional temporary humanitarian logistics hub with a logistics information centre. [Bangladesh Cyclone and Monsoon Floods Humanitarian Response Plan 2024-2025, logistics section](https://bangladesh.un.org/sites/default/files/2024-10/Bangladesh-HCTT-Humanitarian%20Response%20Plan-Cyclone%20and%20Monsoon%20Floods%202024-29-09-2024%20%28June%202024%20to%20March%202025%29.pdf)

The broader resilience need is also recognized internationally. ITU states that timely information flow is critical for emergency decisions and coordination and, as of August 2026, lists Bangladesh among the countries for which it is preparing support for a National Emergency Telecommunication Plan. [ITU National Emergency Telecommunication Plans](https://www.itu.int/en/ITU-D/Emergency-Telecommunications/Pages/NETPs.aspx)

### What these facts support, and what they do not

They support this report-ready problem statement:

> Recurrent flash floods in northeastern Bangladesh isolate communities and disrupt power, telecommunications, roads, storage, health services, and relief delivery. Field teams need a way to preserve essential requests, routing decisions, and delivery records when commercial internet is unavailable.

They do **not** prove that every tower, road, and power connection fails in every flood. They also do not substantiate the hackathon brief's claim that 5.2 million people were displaced in Sylhet, Sunamganj, and Netrakona within 72 hours in July 2025. That exact claim should not appear in the pitch unless an authoritative source is found.

## Disaster coordination workflow and product fit

Bangladesh's formal system is government-led and distributed across administrative levels. The Ministry of Disaster Management and Relief publishes the Standing Orders on Disaster 2019, and the national plan describes district, upazila, union, pourashava, and city-corporation Disaster Management Committees with defined coordination responsibilities. [Standing Orders on Disaster 2019, official ministry page](https://modmr.gov.bd/pages/policies/standing-orders-on-disaster-2019-english-version-2296f0-6940313ac4774958d7b41e98) [National Plan for Disaster Management](https://ddm.gov.bd/sites/default/files/files/ddm.portal.gov.bd/page/332124ba_20b1_42cc_816b_d97d7eb4f478/npdm_final.pdf)

The humanitarian layer complements that structure. HCTT is co-led by MoDMR and the UN Resident Coordinator, while the Inter-Cluster Coordination Group provides operational coordination across national clusters. The official 2024 description says the wider system includes more than 50 organizations across government, UN, national and international NGOs, and donor representatives. [UN Bangladesh HCTT special meeting, April 2024](https://bangladesh.un.org/en/273208-bangladesh-humanitarian-coordination-task-team-hctt-special-meeting-22-april-2024)

**Inference for product design:** the application should fit this chain rather than attempt to replace it. A reasonable operational sequence is:

1. A designated authority provisions roles, trusted device identities, offline maps, and the scenario or operating area before deployment.
2. A clinic, camp, or local responder records a need and priority on a field phone.
3. Nearby phones store, carry, and forward the encrypted event until it reaches an authorized coordinator or logistics node.
4. A human-authorized dispatch decision assigns cargo and a valid vehicle mode, then recomputes when an edge fails or becomes risky.
5. Both sides sign the handoff; the receiving device keeps the receipt offline.
6. When a trusted local link becomes available, the event history can rebuild an optional headquarters view and later reconcile with approved institutional systems.
7. After the incident, the audit record supports review, while personal or sensitive data follows a defined retention and deletion schedule.

This mapping makes the field application an execution and continuity tool. DDM's existing dashboard, GIS, committee database, and shelter system remain systems to integrate with, not systems to displace. [DDM internal e-services](https://ddm.gov.bd/pages/internal-eservices)

## Users, buyers, and beneficiaries

| Group | Role in the workflow | Likely value | Buying or adoption role |
|---|---|---|---|
| MoDMR and DDM | Policy owner, disaster coordination, standards, oversight | Continuity, auditability, common operating procedures | National sponsor, approver, or integration owner |
| District and upazila administrations, DDMC and UzDMC | Local command, resource mobilization, inter-agency coordination | Shared offline mission state and faster exception handling | Primary public-sector deployment owner |
| Union committees and trained volunteers | Need capture, local verification, relay, last-mile handoff | Bangla-first workflows that function beyond the command room | Daily field users and pilot participants |
| DGHS-linked health facilities, community clinics, hospitals | Raise and receive urgent medical supply requests | Clear priority, route status, and custody evidence | Institutional user and safety reviewer |
| WFP/Logistics Working Group, BDRCS, UN agencies, national and international NGOs | Supply, transport, warehousing, assessment, and cluster coordination | Cross-organization visibility and verifiable handoffs | Pilot funder, implementing partner, or service buyer |
| Boat, truck, and simulated drone operators | Move supplies and accept custody | Mode-valid routing, unambiguous job state, signed receipt | Operational user, not usually buyer |
| Flood-affected households | Receive safer and timelier relief | Better delivery reliability and accountability | Beneficiary; should not bear the product cost |

The DDM Disaster Management Committee portal currently exposes 2,914 committee records and large directories of committee and volunteer members. These portal counts show that a structured adoption network exists, but they should not be treated as complete national coverage or as paying accounts without confirmation from DDM. [DDM Disaster Management Committee portal](https://dmcdb.ddm.gov.bd/auth/dashboard)

Bangladesh also had 186.06 million mobile subscriptions in March 2026. This supports the practicality of designing around phones, but subscriptions are not unique people, smartphones, compatible Android devices, or trained users. The number must not be used as a consumer-market estimate. [BTRC mobile subscriber statistics](https://btrc.gov.bd/site/page/0ae188ae-146e-465c-8ed8-d76b7947b5dd/)

## Market sizing without invented revenue

The right unit is an **operational deployment**, not an affected person and not a SIM card.

### Total addressable need

Count institutions that may need disconnected relief coordination:

- disaster management committees and emergency operations functions at national and local levels;
- government health and relief facilities that originate or receive critical-supply requests;
- humanitarian and Red Crescent partners that operate fleets, warehouses, distribution points, or field teams;
- recurring preparedness exercises and flood responses in which those teams need a common workflow.

This count should be assembled from validated government registries, organization rosters, and interviews. DDM's committee portal is a useful starting dataset, not a final market denominator. [DDM Disaster Management Committee portal](https://dmcdb.ddm.gov.bd/auth/dashboard)

### Serviceable beachhead

Use the six northeastern districts covered by the June 2024 assessment: Habiganj, Kishoreganj, Moulvibazar, Netrakona, Sunamganj, and Sylhet. That documented operating area included about 64 upazilas and 360 unions. The serviceable count is the subset of their committees, health facilities, and relief partners that use compatible Android phones and agree to the pilot's governance and training requirements. [Bangladesh Cyclone and Monsoon Floods Humanitarian Response Plan 2024-2025](https://bangladesh.un.org/sites/default/files/2024-10/Bangladesh-HCTT-Humanitarian%20Response%20Plan-Cyclone%20and%20Monsoon%20Floods%202024-29-09-2024%20%28June%202024%20to%20March%202025%29.pdf)

### Obtainable first deployment

Start with one sponsoring district, a small number of upazila and union teams, one health referral chain, and one experienced relief or logistics partner. Run in “shadow mode”: the app records and recommends, while the existing authorized radio, phone, paper, and command procedures remain authoritative. Expansion should follow evidence, not a national rollout promise.

### Metrics that can later support pricing and procurement

- deployed field nodes and active trained users;
- successful message delivery under controlled disconnection;
- end-to-end time from verified request to assignment and receipt;
- duplicate, rejected, expired, and unresolved-conflict rates;
- route-recalculation latency and rate of human overrides;
- chain-of-custody completion rate;
- battery use across an operational shift;
- Bangla and English task completion, error rate, and training time;
- installation, support, map-update, security-review, and exercise costs.

Only after a pilot measures these inputs should the team estimate per-deployment pricing or avoided loss. A source-backed resilience budget, such as the World Bank's May 2025 flood-recovery financing, shows institutional investment in the problem but is **not** the product's revenue market. [World Bank Bangladesh flood recovery and resilience financing](https://www.worldbank.org/en/news/press-release/2025/05/14/world-bank-supports-bangladesh-in-flood-risk-reduction-and-recovery)

## Competitor and substitute capability matrix

Legend: ✓ documented capability; ◐ partial or adjacent capability; — not established in the cited official materials.

| Platform | Offline field use | Device-to-device relay | Relief logistics | Mapping or triage | Security evidence | Main gap relative to this project |
|---|---:|---:|---:|---:|---:|---|
| [Sahana Eden](https://sahanafoundation.org/products/eden/) | ✓ Local deployments and instance synchronization | ◐ Mobile Bluetooth/offline work appears in a blueprint rather than a clearly documented production feature | ✓ Requests, warehouses, inventory, shipments, vehicles, assets | ✓ Maps and disaster coordination | ◐ Roles and audit-oriented workflows; no cited cryptographic handoff | Closest domain analogue, but the current page is a legacy archive and the reviewed material does not establish phone-to-phone encrypted mesh, predictive routing, or signed proof of delivery. [Synchronization blueprint](https://eden-legacy.sahanafoundation.org/wiki/BluePrint/Synchronisation) [Mobile blueprint](https://eden-legacy.sahanafoundation.org/wiki/BluePrint/Mobile) |
| [Ushahidi](https://www.ushahidi.com/features/) | ◐ Older official material describes offline mobile reporting | — | — | ✓ Crowdsourced reports, workflows, geolocation, maps, visualizations | ◐ Roles and permissions | Strong crisis reporting and situational awareness, but its documented platform uses a backend API and does not establish fleet, inventory, route-planning, mesh-relay, or PoD workflows. [Official platform repository](https://github.com/ushahidi/platform) [Official client repository](https://github.com/ushahidi/platform-client) |
| [KoboToolbox / KoboCollect](https://support.kobotoolbox.org/data_collection_kobocollect.html) | ✓ Forms can be completed and queued offline after setup | —; USB or a local server is a documented fallback | — | ◐ GPS and assessment data | ◐ Authentication and upload controls | Excellent complementary needs-assessment tool, but aggregation normally follows server upload or manual transfer. It is not a live logistics or routing engine. [Manual upload guidance](https://support.kobotoolbox.org/manual_upload.html) |
| [Briar](https://briarproject.org/manual/) | ✓ | ✓ Bluetooth and Wi-Fi synchronization; shared forum messages support store-carry-forward | — | — | ✓ End-to-end encryption and nearby QR contact verification | Secure messaging rather than relief execution. Official guidance gives an approximate 10 m local range, says private messages are only synchronized directly, and warns about battery use. [Briar quick-start guide](https://briarproject.org/quick-start/) |
| [Meshtastic](https://meshtastic.org/) | ✓ | ✓ Managed flooding, acknowledgements, hop limits, optional store-and-forward | — | ◐ Position and telemetry, not relief orchestration | ◐ Encryption with documented channel-security limitations | Long range requires external LoRa hardware. Its store-and-forward function needs a suitable ESP32 device with PSRAM, which conflicts with this fair build's phone-only scope. [Mesh algorithm](https://meshtastic.org/docs/overview/mesh-algo/) [Store-and-forward module](https://meshtastic.org/docs/configuration/module/store-and-forward-module/) [Security documentation](https://meshtastic.org/docs/overview/encryption/) |
| This project | ✓ Target architecture and current prototype evidence | ✓ Phone-only Nearby transport with store-and-forward semantics | ✓ Requests, mixed fleet, route changes, priority, custody | ✓ Offline map, risk overlay, triage support | ✓ Signed identity, encrypted payload, signed handoff target | Integration is the proposition. Physical-phone range, reliability, battery, and field usability still require controlled pilot evidence. [Repository architecture](../docs/ARCHITECTURE.md) [Testing evidence policy](../docs/TESTING.md) |

### Positioning that survives scrutiny

Avoid saying “no disaster platform works offline” or “the first disaster mesh.” A stronger statement is:

> Existing products solve individual parts of the problem. This project brings phone-only encrypted relay, conflict-aware field records, flood-aware multimodal routing, priority preemption, and signed custody into one Bangla-and-English relief workflow that can keep working without the headquarters laptop.

Sahana Eden is the closest functional substitute because it already covers relief resources and logistics. KoboToolbox is more likely to be a partner than an enemy: its assessment forms can feed verified needs into a later integration. Briar and Meshtastic are useful technical benchmarks for nearby and off-grid communication. DDM's existing dashboards and registries are incumbent systems to exchange approved summaries with after connectivity returns.

## Differentiation to prove, not merely claim

1. **Field continuity:** request, route, triage, relay, and custody actions finish locally when the laptop and internet are absent.
2. **Phone-only deployment:** ordinary compatible Android phones are sufficient; no LoRa boards, IoT sensors, or controlled physical drone are required.
3. **Operational integration:** one event history connects need, inventory, vehicle constraint, risk, preemption, and receipt instead of leaving operators to reconcile separate apps.
4. **Bangla and English parity:** field users can complete the same critical paths in either bundled language without downloading a pack.
5. **Human authority and audit:** predictions are visibly advisory, safety-sensitive conflicts require an authorized human decision, and custody changes require signed evidence.
6. **A non-authoritative headquarters view:** the projector can rebuild from sanitized events, but losing it does not stop field work.

Each differentiator should have an automated test, a live failure demonstration, and a measured result. Until the physical multi-phone tests are complete, phrases such as “proven in the field,” “production-ready,” and “works across a district” are not justified.

## Adoption and procurement risks

| Risk | Why it matters | Mitigation or decision gate |
|---|---|---|
| Institutional ownership | A disconnected system still needs one authority to issue roles, revoke credentials, approve operating areas, and own incident records. | Obtain a named DDM/DDMC or implementing-partner sponsor before a field pilot. Map every role to an existing SOP. |
| Integration, not dashboard duplication | DDM already lists EOD, GIS, shelter, and committee systems. | Export only approved summaries and define a reconciliation format. Position the field app as continuity and last-mile execution. |
| Funding and procurement timing | The 2024 response plan states that proposed logistics activities for Cyclone Remal and the northeastern floods were not carried out because the logistics cluster received no allocation. | Design a small preparedness/exercise pilot that can be funded before an emergency. Keep recurring infrastructure costs low. [2024-2025 response plan](https://bangladesh.un.org/sites/default/files/2024-10/Bangladesh-HCTT-Humanitarian%20Response%20Plan-Cyclone%20and%20Monsoon%20Floods%202024-29-09-2024%20%28June%202024%20to%20March%202025%29.pdf) |
| Training and staff turnover | Committee membership and responsibilities change, and offline trust mistakes are hard to repair during a flood. | Provide short Bangla role cards, repeatable drills, supervised provisioning, and an annual credential refresh. DDM's PROVATi3 work already includes committee and flood-warning training, which is a useful implementation channel. [PROVATi3 DDM training notes](https://provati.ddm.gov.bd/gallery.php) |
| Device and radio variability | Nearby connectivity, Android background limits, camera quality, and battery health vary by handset. | Publish a tested-device list. Measure range and battery on the actual pilot phones. Keep QR/manual fallbacks and visible queue state. |
| Power scarcity | Local networking and continuous discovery consume power, while disaster charging is constrained. | Use battery-aware cadence, allow planned sync windows, and measure a full-shift power budget. Do not promise unlimited mesh operation. |
| Trust and misinformation | Encryption protects content in transit but cannot make a malicious or mistaken authorized report true. | Use scoped roles, signed acknowledgements, replay protection, revocation, two-person approval for sensitive actions, and visible provenance. |
| Model error | A route-risk classifier trained on simulated data may not generalize to real roads, water levels, or seasonal conditions. | Keep the model advisory and visibly simulated until validated with local historical and operational data. Never let prediction alone close a route. |
| Safety liability | A software route may be mathematically valid but physically unsafe. | Keep official closure reports and trained human judgment authoritative. The product must not replace medical triage, evacuation orders, boat safety, or aviation rules. |
| Maintenance and security review | Open-source components and cryptography require updates beyond the fair. | Name a maintainer, publish a vulnerability process, perform key-rotation drills, and budget for map/app updates before operational adoption. |

## Data, privacy, and safety limits

Humanitarian data can expose already vulnerable people. The ICRC describes personal-data protection as integral to life, integrity, and dignity, and OCHA defines data responsibility as safe, ethical, and effective management of both personal and non-personal operational data. [ICRC Handbook on Data Protection in Humanitarian Action](https://www.icrc.org/en/data-protection-humanitarian-action-handbook) [OCHA Data Responsibility Guidelines 2025](https://centre.humdata.org/data-responsibility-guidelines-2025/)

Before any real pilot, the project should therefore:

- complete a data-impact assessment with the sponsoring organization;
- collect the minimum operational fields needed to move supplies;
- avoid names, diagnoses, national IDs, phone numbers, precise household coordinates, and vulnerability labels unless the approved workflow genuinely requires them;
- use delivery IDs and hashes rather than beneficiary identity in QR handoffs where possible;
- define who may see each event class, for how long, and how a device is retired or lost;
- keep private keys non-exportable and require a revocation and replacement procedure;
- keep encrypted mesh payloads, credentials, signatures, and medical details out of any public or optional cloud dashboard;
- show source, age, simulation status, and confidence for every environmental or vehicle claim;
- maintain a paper/radio fallback and allow operators to reject a recommendation;
- prohibit the prototype from controlling a real drone or issuing public evacuation or medical advice.

**Inference:** an offline design reduces dependency on cloud connectivity, but it does not remove privacy risk. Data is copied across field devices and may remain there longer during an outage. Retention, device custody, key recovery, and post-incident deletion are therefore as important as encryption.

## Pilot and go-to-market plan

### Phase 0: sponsorship and workflow fit

Secure one operational sponsor and one implementation partner. Candidate combinations include a DDMC with a district health facility and an experienced NGO or BDRCS unit. Map the application's roles and events to the Standing Orders, local plans, existing radio/phone procedures, and the partner's incident forms. Complete the data-impact and safety review before collecting real operational data.

### Phase 1: controlled readiness exercise

Use ordinary Android phones in a training venue. Test provisioning, Bangla and English task completion, queued relay, duplicate handling, route failure, signed handoff, device loss, credential revocation, laptop disconnection, and end-of-exercise reconciliation. The acceptance gate is repeatable evidence on the exact phones, not a polished dashboard.

### Phase 2: district shadow pilot

Run alongside existing authorized procedures during a scheduled flood drill or low-risk logistics exercise. Do not use the prototype as the sole channel. Include a district coordination point, selected upazila/union teams, one health referral path, and one logistics partner. Compare the digital event trail with the official log after the exercise.

### Phase 3: monsoon-limited operational pilot

Only after the shadow pilot passes security, usability, radio, battery, and governance gates should the system support a bounded real deployment. Keep geography, cargo types, operator roles, and decision authority narrow. Publish incidents, overrides, and failures as well as successes.

### Phase 4: interoperability and scale

Add approved import/export connectors for existing DDM or partner systems, standardized map-update packages, trainer materials, and a support model. Expand district by district based on local ownership and exercises rather than promising a single national launch.

### Commercialization hypothesis to validate

The core application can remain openly inspectable, while revenue or cost recovery comes from deployment preparation, partner-specific integration, security review, training, offline map packaging, exercise support, and maintained releases. An optional hosted dashboard may be sold as a convenience, but the field workflow must never require a subscription or cloud service during an incident.

Likely entry routes are a government or NGO preparedness pilot, an innovation or resilience grant, or an implementation partnership with an organization already participating in HCTT or the Logistics Working Group. The buyer interview must test budget owner, procurement route, acceptable support model, data-controller responsibility, and willingness to maintain devices between flood seasons.

## Report-ready claims

### Safe headline

> A bilingual, phone-only relief coordination system that preserves requests, routing, priority decisions, and signed delivery records when internet connectivity fails.

### Source-backed problem proof

> The June 2022 northeastern floods affected an estimated 7.2 million people. Official response reporting documented power shutdowns, difficult mobile communication, submerged highways, scarce rescue boats, and isolated health services. In 2024, another northeastern flood affected about 3.74 million people across six districts, while the humanitarian logistics plan recorded disrupted roads and storage in Sylhet and Sunamganj.

Sources: [2022 response plan](https://bangladesh.un.org/en/188010-flash-floods-humanitarian-response-plan-2022-united-nations-bangladesh-coordinated-appeal), [2022 situation update](https://bangladesh.un.org/sites/default/files/2022-06/situation_overview_flash_flood_final_draft_.pdf), and [2024-2025 response plan](https://bangladesh.un.org/sites/default/files/2024-10/Bangladesh-HCTT-Humanitarian%20Response%20Plan-Cyclone%20and%20Monsoon%20Floods%202024-29-09-2024%20%28June%202024%20to%20March%202025%29.pdf).

### Honest market statement

> The first users are local disaster committees, health facilities, and relief logistics teams in flood-prone districts. The first buyer is an institutional sponsor that can own training, credentials, operating procedures, and maintenance. Market size will be measured in validated deployments and field nodes, not Bangladesh's population or mobile-subscription count.

### Claims to avoid

- “The first” or “the only” system without a comprehensive patent and product search.
- “Works anywhere” before range and device testing across real terrain.
- “AI predicts floods” when the current model predicts route-edge risk from simulated features.
- “Tamper-proof” when the accurate claim is tamper-evident, signed, and replay-protected.
- “Military-grade encryption,” “unhackable,” or other undefined security language.
- “Replaces command centres,” “replaces medical triage,” or “autonomously dispatches relief.”
- Treating public resilience funding, affected population, DMC portal records, or mobile subscriptions as product revenue.

## Research gaps before a real procurement pitch

1. Interview DDM/DDMC personnel, PIOs, health-facility staff, boat operators, BDRCS/NGO logisticians, and cluster information-management staff about the current paper, phone, radio, and spreadsheet workflow.
2. Confirm the data owner, retention period, incident classification, and legal review required for government and humanitarian records.
3. Measure compatible Android ownership, charging availability, battery condition, and radio performance among intended users rather than inferring from national subscription data.
4. Obtain official route, shelter, facility, storage, and transporter data and document update ownership.
5. Validate the risk model against historical and locally reviewed data before operational use.
6. Test whether existing systems accept a safe export or whether manual reconciliation is the only approved option.
7. Obtain budget-owner and procurement-cycle evidence before presenting pricing, market revenue, or national scale.

## Primary source register

All sources below were accessed on **2026-09-04**.

- [Bangladesh Department of Disaster Management: AWARE risk information](https://rapid.ddm.gov.bd/risk/riskinfo)
- [Bangladesh Department of Disaster Management: internal e-services](https://ddm.gov.bd/pages/internal-eservices)
- [Bangladesh Department of Disaster Management: Disaster Management Committee portal](https://dmcdb.ddm.gov.bd/auth/dashboard)
- [Bangladesh Ministry of Disaster Management and Relief: Standing Orders on Disaster 2019](https://modmr.gov.bd/pages/policies/standing-orders-on-disaster-2019-english-version-2296f0-6940313ac4774958d7b41e98)
- [Bangladesh Department of Disaster Management: National Plan for Disaster Management](https://ddm.gov.bd/sites/default/files/files/ddm.portal.gov.bd/page/332124ba_20b1_42cc_816b_d97d7eb4f478/npdm_final.pdf)
- [Bangladesh Telecommunication Regulatory Commission: mobile subscribers](https://btrc.gov.bd/site/page/0ae188ae-146e-465c-8ed8-d76b7947b5dd/)
- [UN Bangladesh: Flash Floods Humanitarian Response Plan 2022](https://bangladesh.un.org/en/188010-flash-floods-humanitarian-response-plan-2022-united-nations-bangladesh-coordinated-appeal)
- [UN Bangladesh: Severe Flash Floods Situation Update 1, 19 June 2022](https://bangladesh.un.org/sites/default/files/2022-06/situation_overview_flash_flood_final_draft_.pdf)
- [UN Bangladesh: Humanitarian Response Plan, June 2024 to March 2025](https://bangladesh.un.org/sites/default/files/2024-10/Bangladesh-HCTT-Humanitarian%20Response%20Plan-Cyclone%20and%20Monsoon%20Floods%202024-29-09-2024%20%28June%202024%20to%20March%202025%29.pdf)
- [UN Bangladesh: HCTT special meeting, April 2024](https://bangladesh.un.org/en/273208-bangladesh-humanitarian-coordination-task-team-hctt-special-meeting-22-april-2024)
- [World Food Programme: anticipatory action in Sylhet Division, April 2026](https://www.wfp.org/news/early-action-saves-lives-wfp-activates-assistance-bangladesh-ahead-peak-flooding)
- [International Telecommunication Union: National Emergency Telecommunication Plans](https://www.itu.int/en/ITU-D/Emergency-Telecommunications/Pages/NETPs.aspx)
- [World Bank: Bangladesh flood recovery and resilience financing, May 2025](https://www.worldbank.org/en/news/press-release/2025/05/14/world-bank-supports-bangladesh-in-flood-risk-reduction-and-recovery)
- [International Committee of the Red Cross: Handbook on Data Protection in Humanitarian Action](https://www.icrc.org/en/data-protection-humanitarian-action-handbook)
- [OCHA Centre for Humanitarian Data: Data Responsibility Guidelines 2025](https://centre.humdata.org/data-responsibility-guidelines-2025/)
- [Sahana Foundation: Sahana Eden](https://sahanafoundation.org/products/eden/)
- [Ushahidi: platform features](https://www.ushahidi.com/features/)
- [KoboToolbox: KoboCollect offline workflow](https://support.kobotoolbox.org/data_collection_kobocollect.html)
- [Briar: user manual](https://briarproject.org/manual/)
- [Meshtastic: official documentation](https://meshtastic.org/docs/)
