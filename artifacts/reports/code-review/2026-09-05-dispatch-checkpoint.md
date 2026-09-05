# Accepted-mission dispatch checkpoint

Source: changes following `e44d9fb`, September 5, 2026. This is a software
checkpoint, not final physical-phone acceptance.

## Implemented

- Coordinator-only signed/encrypted operator reservations for accepted missions.
- One Protobuf field revision defines driver, declared truck/boat and itinerary.
- Same-pickup P0/P1 preemption of P2/P3 with a freshly computed breach, identical
  reader sets and explicit human confirmation that no handoff occurred.
- Atomic lower hold, urgent reservation and encrypted fan-out. No physical deposit
  or estimated time saving is asserted.
- Exact reviewed-event checks and fail-closed conflicting operator reservations.
- First signed receipt pins the reservation; a crossing hold cannot release cargo
  in custody. Completion releases the driver for a new plan.
- Production Compose presentation separated from Room ownership for UI checks.
  The runtime still obtains authority exclusively from the provisioned ViewModel.
- Bangla/English dispatch, assignment and reconciliation dialog tests. Full-row
  labelled radio/checkbox targets and scrollable form contents support accessibility.

## Evidence

`scripts/verify-local.sh --connected` passed on Pixel_10_Pro_XL, Android 16 / API 36:

- 106 JVM tests and 83 connected Android tests;
- debug and minified release builds;
- Go race/vet/build and Protobuf/localization/map/model checks;
- 27 headquarters tests, typecheck and Next.js production build;
- 12 Worker tests, typecheck and Wrangler dry run.

Log: [2026-09-05-dispatch-local.log](../verification/2026-09-05-dispatch-local.log).
Additional focused runs exercise dark/light and large-text UI fixtures. Captures
and their exact scope live in [the capture README](../../screenshots/field/2026-09-05-dispatch/README.md).

Independent Room/Keystore tests prove credential enforcement, stale-dialog rejection,
wrong vehicle/priority rejection, encryption rollback, disconnected coordinator
double-booking, encrypted replica application after restart, two genuine signed
handoffs, driver release and held-mission reassignment. UI fixtures are not evidence
of physical radio communication or production credential provisioning.

## Defects caught during the pass

1. Separate itinerary and reservation events could arrive partly. Replaced by a
   single authoritative DISPATCH revision with a derived itinerary.
2. High-priority edits could overtake lower-priority request creation. The existing
   dependency retry is exercised with another inbox pass, not a fake transport ACK.
3. A density-only large-text fixture enlarged the page but not dialog windows.
   The final capture also sets and restores the task emulator's system font scale;
   a scoped Compose configuration override did not affect this native window.
4. The emulator disappeared during one APK installation. That run executed zero
   tests and is not counted; the task AVD was rebooted and tests rerun successfully.

## Remaining gates

Post-handoff route/operator recovery is still a separate implementation gate.
Three physical phones, real camera scans, encrypted replacement/backlog recovery,
measured target-phone performance, human accessibility checks and three unchanged
offline rehearsals remain unverified. Disconnected replicas cannot provide global
fleet exclusion before they communicate.
