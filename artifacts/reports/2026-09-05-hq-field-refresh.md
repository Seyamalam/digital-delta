# Headquarters routing, Hono and field hardening checkpoint

This is a local implementation checkpoint, not a declaration that every module or
the project is finished. No production cloud deployment was performed.

## Delivered

- Seven Next.js App Router workspaces and persistent shadcn sidebar/provider.
- Larger bilingual dashboard and Android typography; Android controls grow with
  system text size. Language/PIN entry can scroll and accommodate the keyboard.
- Dedicated map, mission register, resource directory, network, activity and lab.
- Real bundled OSM geography and road/waterway route geometry. Exercise locations
  and polygons are explicitly unverified, not authoritative safe-area data.
- Cumulative observer projection independent of the recent 100-event window.
- Persistent simulation provenance, including observations received over live SSE.
- Hono/Workers + D1 observer, source-bound authentication, strict presentation
  allow-list, byte-bounded requests, idempotent publishing and ordered replay.
- Signed Android envelope origins; revoked peers checked during active use;
  intermediate relay receipts no longer imply delivery to the final destination.
- Received request/field-event application with actor checks and transactional
  projection; unrelated clinics cannot edit another clinic's mission.

## Automated results

Final `scripts/verify-local.sh`: passed.

| Check | Result |
| --- | --- |
| Android JVM | 100 passed, 0 failed/skipped |
| Android debug + minified release | Built successfully |
| Android connected, API 35, emulator-5554 | 65 passed, 0 failed/skipped |
| Go | Race tests, vet and builds passed |
| Dashboard | 19 tests passed, TypeScript and seven-route production build passed |
| Hono in local workerd/D1 | 9 tests passed, TypeScript and Wrangler dry-run passed |
| Protobuf/localization/maps/model | Integrity, parity and reproducibility gate passed |

Connected command was scoped with `ANDROID_SERIAL=emulator-5554`.
An earlier unscoped run also discovered a separately running emulator; it is not
used as evidence. Earlier runs caught an offscreen test tap after typography grew
and a startup timeout. The test now scrolls to the visible target before tapping;
the conflict workspace also focuses its decision card. A full subsequent run
passed unchanged. No assertions or security gates were removed.

## Interactive checks

Chrome verified English switching without resetting state, sidebar navigation from
overview to map/missions, rendered geographic polylines, and mobile sidebar opening
then closing after navigation to `/network`. The current observer received the
seven explicitly simulated drill records through real Hono/D1, not a fake connected
indicator. Initial in-app preview capture failed; verification continued in Chrome.

Live Hono replay check: dashboard disconnected at sequence 7; `fair-pass-02`
published sequences 8–14 while it remained at 7; reconnect advanced it to 14.
This is observer replay evidence, not physical laptop-off field evidence.

Argent inspected the installed Android app's bilingual language screen and Bangla
offline PIN screen. Saved images are in
`artifacts/screenshots/2026-09-05-hq-field-refresh/`. These are reduced-resolution
interaction captures, not typography measurements or full motion/performance proof.

## Still open

Android signed-event publication to Hono; complete received-request UI/dispatch;
cross-device CRDT resolution propagation; physical multi-phone relay and camera
custody; target-phone RAM/latency; current production deployment; three unchanged
offline rehearsals. Real flood/shelter feeds and verified safe-area data are not
connected. No cloud or observer service is a dependency of field work.
