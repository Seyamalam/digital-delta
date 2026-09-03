# Live demonstration plan

The demo proves one complete disaster mission. Every action belongs to the same delivery ID so the audience can follow cause and effect.

## Equipment

- Three charged Android phones with the release build installed
- Laptop A connected to the projector and running Delta Command
- Laptop B running Disaster Control, logs, and the local scenario service
- Projector and the required video adapter
- Charging cables and power strip
- Printed recovery card with reset commands and test accounts

No sensor, microcontroller, LoRa radio, physical drone, or other external device is part of the system.

## Screen arrangement

The projector shows four areas:

1. The map occupies the largest area.
2. A mission panel shows cargo, priority, SLA, assigned vehicle, and current custodian.
3. A node panel shows connection state, battery, relay queue, and last contact.
4. An event panel explains the latest decision in the selected language.

Laptop B should face the presenter. Its Disaster Control actions appear on the projector as signed or simulated events, not as unexplained changes.

## Before the audience arrives

- Build and install the exact tagged release.
- Set every device clock and verify tolerated skew.
- Charge all devices above 80 percent.
- Disable mobile data and disconnect commercial Wi-Fi.
- Enable only the local communication methods required by the build.
- Start the dashboard and confirm that disconnecting it does not stop the phones.
- Run the reset command with the published scenario seed.
- Complete one private rehearsal.
- Keep the backup recording paused at its first frame.

## Ten-minute judged demo

### 0:00 to 0:45, the failure

State the problem in one sentence:

> During a flood, a clinic can have medicine, volunteers, and boats nearby while losing the internet service that normally coordinates them.

Show that all field phones have no commercial internet. Explain that the projected laptop observes the mission but does not control field survival.

### 0:45 to 1:30, identity and language

- Open Phone A in Bangla.
- Show the offline identity badge and clinic role.
- Attempt one coordinator-only action and show the denial.
- Switch Phone C to English without restarting or losing state.

Evidence: M1, bilingual field operation, audit event.

### 1:30 to 2:30, urgent request

- Create a P0 medicine request at Kanaighat Point.
- Show the two-hour SLA and required delivery quantity.
- Save while offline.
- Display the pending encrypted envelope in Phone A's outbox.

Evidence: offline write, P0 policy, local persistence.

### 2:30 to 3:45, interrupted relay

- Let Phone A discover Phone B.
- Start the relay toward Phone C.
- Disconnect or close Phone B during transfer.
- Show the message remaining in Phone B's persistent queue.
- Restore Phone B and complete the relay.
- Inject the same envelope again and show deduplication.

Evidence: M2 and M3, store-and-forward, recovery, TTL, deduplication.

### 3:45 to 5:00, routing failure

- Show the initial valid route and vehicle assignment.
- Flood the active road from Disaster Control.
- Display the event as simulated.
- Show the route recomputation timer.
- Verify that the new route obeys vehicle constraints.
- Open the route explanation.

Evidence: M4 and visible simulation boundary.

### 5:00 to 6:15, prediction and triage

- Increase simulated rainfall and soil saturation.
- Show an edge risk crossing the chosen threshold.
- Display the risk as a prediction.
- Show the 30 percent slowdown ETA breaching the P0 SLA.
- Deposit P2 cargo at a safe waypoint and prioritize P0.
- Confirm the decision as a human coordinator.

Evidence: M6 and M7, explainability, human approval.

### 6:15 to 7:30, drone-required handoff

- Open **Handoff / হস্তান্তর** and point to the visible `DRONE-REQUIRED` and `SIMULATED` badges for air-only N7.
- Explain why R3 was selected: 33-minute boat ETA, 19-minute drone ETA, 45-minute final delivery ETA, and 25 percent projected battery above the 20 percent reserve.
- Tap **Simulate boat arrival**, then generate the signed boat-to-drone Protobuf QR while the progress animation covers Android Keystore work.
- Tap **Simulated drone signs and accepts** and show the two-party receipt, simulated-drone identity, receipt hash, and valid linked chain.
- Switch language without resetting the completed transfer.

Evidence: M8. State clearly that the vehicle movement is simulated and the coordination logic is real.

### 7:30 to 8:45, delivery proof

- Generate the sender's signed QR.
- Scan and accept it on Phone C.
- Show the receipt on every reachable node.
- Scan the same code again and show replay rejection.
- Use the tamper control and show signature rejection.

Evidence: M5, signature verification, replay protection, custody chain.

### 8:45 to 9:30, convergence and audit

- Reconnect the separated nodes.
- Show vector clocks and convergence hashes.
- Resolve one prepared safety-sensitive conflict.
- Open the complete request-to-delivery timeline.

Evidence: M2, conflict handling, auditability.

### 9:30 to 10:00, measured result and boundary

Show the measured summary:

- route recomputation latency;
- relay recovery time;
- duplicate and replay rejection count;
- field-app memory on the tested phone;
- model metric and dataset status;
- number of unchanged successful rehearsals.

End with the deployment claim, not a technical slogan:

> Digital Delta lets a clinic, volunteer, vehicle operator, and field hospital coordinate a verified relief delivery even when commercial internet is gone.

## Ninety-second booth loop

1. Show all devices offline.
2. Create the P0 request.
3. Relay it through Phone B.
4. Let the visitor flood the active road.
5. Show rerouting and SLA preemption.
6. Let the visitor scan the signed QR twice.
7. Finish on the rejected replay and custody timeline.

## Reset procedure

The reset command must:

- stop scenario playback;
- clear only demo mission data;
- preserve provisioned demo identities unless `--full` is supplied;
- restore the same map, stock, clocks, batteries, and vehicle positions;
- use a known seed;
- verify that all devices report the expected starting hash;
- print a clear ready or failed result.

## Failure recovery

| Failure | Recovery |
|---|---|
| One phone cannot discover peers | Continue with two-phone direct sync and state the missing relay proof; use the recorded relay clip afterward |
| Dashboard loses connection | Keep operating on phones; reconnect the observer after the handoff |
| Projector fails | Use Laptop A screen and the printed QR flow; do not alter the field mission |
| Scenario becomes inconsistent | Run the one-click reset and use the same seed |
| Model fails to load | Use the documented baseline risk score and state that the model evidence is in the recording |
| QR camera fails | Enter the compact handoff code manually and show the same signature verification path |

Do not hide a failure. State what failed, use the prepared fallback, and avoid claiming that the failed path ran live.
