# Three-phone offline acceptance sheet

Use this sheet on the exact phones that will go to the fair. Run the release APK with mobile data and Wi-Fi internet access disabled. Bluetooth and local nearby Wi-Fi may remain enabled because they are the tested radio path.

## Test inventory

| Role | Profile | Device model | Android version | Serial recorded privately | Battery at start |
|---|---|---|---|---|---:|
| Phone A, requester | N4 clinic |  |  |  |  |
| Phone B, relay and boat | RLY-01 relay |  |  |  |  |
| Phone C, recipient | N6 hospital |  |  |  |  |

Release commit: ____________________

APK SHA-256: ____________________

Scenario seed: `fair-pass-01`

Date and operator: ____________________

## Preconditions

- [ ] Install the same release APK on A, B, and C.
- [ ] Complete Bangla-first setup, then confirm English is bundled and selectable.
- [ ] Provision each profile from the offline administrator and record only public credential fingerprints.
- [ ] Set airplane mode on all three phones; manually re-enable Bluetooth and nearby Wi-Fi if Android disables them.
- [ ] Prove no phone can load a public web page.
- [ ] Keep the command laptop disconnected for the first field pass.

## Journey A: authenticated store and forward

- [ ] On A, create the fixed P0 medical request for C.
- [ ] On A and B, compare Nearby digits and explicitly accept the peer.
- [ ] Verify both sides show the authenticated credential and signing-key state before any envelope moves.
- [ ] Move B into range of C and out of range of A.
- [ ] On B and C, compare digits and accept the peer.
- [ ] Verify C receives and decrypts the request while B can show only routing metadata.
- [ ] Record message ID, acknowledgement signer, total relay time, and duplicate count.

Expected: one durable application at C, zero plaintext cargo exposure at B, and signed acknowledgements for both hops.

## Journey B: interruption and recovery

- [ ] Reset to the same seed and queue a new message on A.
- [ ] Stop or power off B after it durably receives the envelope but before C receives it.
- [ ] Confirm A and C remain usable and the laptop remains unnecessary.
- [ ] Restart B, unlock locally, and restart the visible relay service.
- [ ] Re-authenticate the nearby link if required.
- [ ] Confirm C accepts exactly one copy and any duplicate path is rejected.
- [ ] Record downtime, recovery time, retry count, and duplicate disposition.

Expected: the envelope survives B's interruption, reaches C after recovery, and is applied once.

## Journey C: QR camera and custody

- [ ] Generate a fresh signed PoD QR on the sender phone.
- [ ] Scan it with the recipient camera while every phone remains offline.
- [ ] Confirm both signatures and the new receipt hash appear in the custody chain.
- [ ] Scan the same QR again and record replay rejection without another custody row.
- [ ] Change one encoded field in the prepared tamper sample and record signature rejection.

Expected: fresh offer accepted, replay and tamper rejected, chain length unchanged after both rejections.

## Journey D: laptop and power independence

- [ ] Start a field route and queue on the three phones.
- [ ] Disconnect or shut down the command laptop and projector.
- [ ] Complete one relay, on-device reroute, triage decision, and signed handoff.
- [ ] Restart the laptop and confirm the observer projection rebuilds in sequence order.
- [ ] Repeat using a phone hotspot or local router with no upstream internet if the booth network is part of the setup.

Expected: no field action blocks on the laptop; the dashboard later catches up from durable events.

## Measurements

- [ ] Run 30 active-edge recomputations and record median and p95.
- [ ] Record release-app PSS after map load, inference, reroute, and custody verification.
- [ ] Verify the under-30-percent battery broadcast interval and urgent-message behavior.
- [ ] Run 10 complete A to B to C deliveries and calculate success rate.
- [ ] Capture Bangla and English screenshots plus one large-text pass on the smallest screen.

## Result

Passed journeys: ____________________

Failed journeys and recovery: ____________________

Evidence paths: ____________________

Operator signatures: ____________________

This sheet is not complete evidence until every checked row contains a recorded result and the referenced files are committed under `artifacts/`.
