# Product requirements

## Product statement

Digital Delta is an offline-first relief coordination system for flood response in Bangladesh. It lets field teams create urgent requests, relay them between nearby devices, choose valid mixed-fleet routes, adjust priorities, and verify physical handoffs without commercial internet.

## Product boundaries

Digital Delta includes software for Android phones and laptops. A projector can display the command dashboard. The product does not depend on external hardware, IoT sensors, physical vehicles, or physical drones.

The system may simulate rainfall, route failures, batteries, vehicle movement, and environmental features for demonstrations and training. It must label simulated values on every screen where a user could mistake them for live observations.

## Primary users

### Clinic or relief-camp requester

Creates supply requests, describes urgency, monitors acknowledgement, and confirms receipt.

### Volunteer or relay operator

Carries encrypted messages between disconnected areas and may also transport cargo.

### Dispatcher or coordinator

Assigns vehicles, reviews route and triage recommendations, resolves safety-sensitive conflicts, and confirms preemption.

### Driver, boat operator, or simulated-drone operator

Receives an assignment, follows the route, reports arrival, and participates in signed handoffs.

### Hospital or final recipient

Verifies delivery identity, accepts cargo, and creates the final receipt.

### Auditor or incident reviewer

Reconstructs decisions and custody without gaining permissions to alter them.

## Common user experience

### First run

1. The app displays `বাংলা` and `English` before any sign-in form.
2. The selected language applies immediately and persists offline.
3. An authorized administrator provisions the device and user through a signed QR or local transfer.
4. The user unlocks the local identity and sees their role, device identity, offline state, and last successful synchronization.

Acceptance criteria:

- The user completes first run without internet.
- Both language choices expose the same actions and information.
- An invalid or expired provisioning package produces an actionable error.
- Changing language never clears a form, queue, request, or mission.

### Connectivity state

Every critical screen displays one of these states with text, icon, and accessible label:

- Offline / অফলাইন
- Nearby connected / কাছাকাছি সংযুক্ত
- Syncing / সিঙ্ক হচ্ছে
- Conflict detected / দ্বন্দ্ব শনাক্ত
- Verified / যাচাইকৃত
- Action required / পদক্ষেপ প্রয়োজন

Color may reinforce a state but cannot be the only indicator.

## Epic M1: offline identity and authorization

User stories:

- As an administrator, I can provision a field identity without internet so a team can start after infrastructure failure.
- As a field user, I can unlock my provisioned identity and work offline.
- As a coordinator, I can assign roles that restrict dangerous actions.
- As an auditor, I can inspect signed actions but cannot change them.

Acceptance criteria:

- Valid offline credentials unlock the correct role.
- Expired, revoked, malformed, or wrongly signed credentials fail.
- Calling a forbidden action outside the visible interface still fails.
- Identity, role, and audit changes are signed and persist after restart.
- Key fingerprints and role names remain understandable in both languages.

## Epic M2: distributed state and conflict handling

User stories:

- As a disconnected user, I can create and change requests locally.
- As a returning user, I can synchronize with a nearby device.
- As a coordinator, I can review conflicts that are unsafe to merge automatically.
- As an auditor, I can see which device produced each change.

Acceptance criteria:

- Concurrent safe changes converge to the same projection on every device.
- A duplicate operation changes projected state at most once.
- A safety-sensitive conflict remains unresolved until an authorized person chooses an outcome.
- The conflict interface shows both values, authors, times, and consequences.
- Restarting during synchronization does not corrupt committed operations.

## Epic M3: nearby mesh relay

User stories:

- As a requester, I can send a request through intermediate phones when the recipient is out of range.
- As a relay operator, I can carry queued messages without viewing encrypted contents.
- As a low-battery user, I can conserve power without silently dropping urgent messages.

Acceptance criteria:

- Phone A can deliver to Phone C through Phone B without commercial internet.
- Phone B can stop during relay and resume after restart.
- Expired or over-hop-limit messages do not continue propagating.
- Duplicate envelopes do not duplicate requests, inventory changes, or receipts.
- A relay sees destination, priority class, expiry, and queue status but not protected cargo details.
- Below 30 percent battery, normal broadcasts reduce by 60 percent while urgent policy remains documented.

## Epic M4: multi-modal routing

User stories:

- As a dispatcher, I can choose routes across road, waterway, and airway edges.
- As an operator, I receive only routes compatible with my vehicle.
- As a coordinator, I can see why a route changed when an edge fails or becomes risky.

Acceptance criteria:

- The graph supports weighted directed edges and parallel transport modes.
- Trucks use roads, boats use waterways, and simulated drones use airways.
- A failed edge leaves the candidate graph immediately.
- Active affected missions recompute within the measured target on the tested laptop or phone.
- The interface shows the previous route, selected route, ETA change, and reason.

## Epic M5: proof of delivery

User stories:

- As a sender, I can create a signed handoff while offline.
- As a recipient, I can verify and accept a handoff while offline.
- As an auditor, I can reconstruct custody from creation to final delivery.

Acceptance criteria:

- The signed payload includes delivery ID, sender public key, payload hash, nonce, and timestamp.
- A valid QR updates custody once.
- A changed field, invalid signature, wrong delivery, expired credential, or reused nonce fails.
- The user sees a distinct rejection reason and safe next action.
- Every receipt links to the preceding custody event.

## Epic M6: triage and preemption

User stories:

- As a requester, I can classify cargo as P0, P1, P2, or P3 with a visible deadline.
- As a dispatcher, I can see whether a 30 percent slowdown would breach the SLA.
- As a coordinator, I can approve dropping lower-priority cargo at a safe waypoint to protect urgent delivery.

Acceptance criteria:

- Priority definitions and deadlines appear in both languages.
- The app compares normal and slowed ETA against the deadline.
- A recommendation identifies affected cargo, waypoint, time gained, and policy reason.
- A human confirms any assignment-changing preemption.
- The audit log records the policy version and confirmer.

## Epic M7: route-risk prediction

User stories:

- As a dispatcher, I can see which edges the model considers at risk within two hours.
- As an auditor, I can see the inputs, threshold, model version, and limitations.
- As a route engine, I can penalize predicted risk without treating it as confirmed closure.

Acceptance criteria:

- The model runs on the field device without internet.
- Rainfall, elevation, and saturation features feed the model.
- The interface distinguishes prediction, simulated input, and confirmed failure.
- The model card reports dataset source, split, precision, recall, F1, threshold, and failure cases.
- M4 can compare a shortest route with a risk-adjusted route.

## Epic M8: hybrid fleet and simulated-drone handoff

User stories:

- As a dispatcher, I can identify destinations unreachable by truck or boat.
- As a coordinator, I can compute a rendezvous for a boat and simulated drone.
- As an operator, I can transfer custody through the same signed protocol used for other handoffs.

Acceptance criteria:

- A location with no valid road or water route becomes drone-required.
- The rendezvous result shows coordinates, arrival estimates, assumptions, and objective.
- Boat and simulated-drone roles can sign the custody transfer.
- Low battery changes mesh broadcast frequency according to policy.
- Every physical-vehicle implication is marked as simulation in the fair build.

## Command dashboard

The dashboard helps an audience and coordinator understand the system. It is not the single source of truth.

Required views:

- offline map and active routes;
- supply requests, inventory, SLA, and priority;
- node status, battery, signal, and last contact;
- mesh queues and relay path;
- risk overlay and input status;
- conflicts and convergence hashes;
- custody timeline and verification state;
- measured latency and test evidence.

Acceptance criteria:

- Critical changes become understandable within three seconds.
- The dashboard works at common projector resolutions.
- Disconnecting it does not stop field operations.
- Bangla and English modes contain the same information.

## Disaster Control

An authorized presenter can inject deterministic, simulated conditions:

- edge flood and recovery;
- rainfall and saturation changes;
- vehicle delay;
- node disconnection and reconnection;
- low battery;
- conflicting offline edit;
- duplicate message;
- tampered or replayed receipt.

Every injected event carries a simulation label, actor, scenario seed, and timestamp.

## Safety, privacy, and accessibility

- Collect the minimum personal data required for role and audit.
- Never display secret keys or full sensitive identifiers.
- Keep medical cargo details encrypted for intended roles.
- Require human confirmation for preemption, unsafe conflict resolution, and manual security override.
- Provide keyboard navigation on laptop interfaces.
- Support screen-reader labels, large text, and 4.5 to 1 text contrast on critical paths.
- Do not rely only on color, animation, sound, or English technical terms.
- Do not claim that a prediction is an observed flood.

## Non-goals for the fair build

These are explicit boundaries rather than removed ambitions:

- controlling a real drone or vehicle;
- reading physical sensors;
- replacing official emergency command;
- providing medical diagnosis;
- public production deployment with real citizen data;
- claiming nation-scale performance from a laptop-only load test;
- hiding simulation behind production-looking labels.

