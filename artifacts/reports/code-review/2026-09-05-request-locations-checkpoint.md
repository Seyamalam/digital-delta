# Request locations checkpoint, September 5

The request composer now selects pickup and destination from the bundled graph,
with Bangla/English names, native Material dialogs and selection retained during
language changes. The production submission service validates both endpoints
before creating encrypted fan-out. It still rejects unavailable recipient keys.

Distinct profiles now cover N1 through N7 and RLY-01. Profile selection cannot
grant authority: the existing exact identity/role/key credential checks still apply.
Initial custody follows the selected pickup. Route availability and vehicle
assignment remain separate from accepting a request.

The full local gate passed 104 JVM tests, 79 connected Android tests, 27 dashboard
tests and 12 Worker tests on the Android 16 Pixel_10_Pro_XL AVD. Debug/minified
release, Go race/vet/build, Protobuf/localization/map/model checks, Next.js build and
Wrangler dry run also passed. The log is
[saved here](../verification/2026-09-05-request-locations-local.log).

New regressions prove graph membership checks happen before persistence, selected
pickup reaches the encrypted production payload, picker choices survive language
changes/cancellation, all map-node profiles have distinct identities and explicit
roles, and future or exactly five-minute-old ETA observations require refresh.
The latter fixes a triage path that previously treated future estimates as fresh.

The running request surface and picker transitions were inspected. The screen-tree
inspector cached a prior screen after navigation, so no taps were inferred from
pixels. A recorded Compose journey supplied transition evidence; opt-in settled
captures supply the [layout evidence](../../screenshots/field/2026-09-05-request-locations/README.md).
This is emulator evidence, not a measured 60 fps claim or physical phone acceptance.
The subsequent dark-mode capture found low-contrast cargo icons and selected-tab
labels; these now use the theme's primary color. The focused dark picker journey
passed again and the corrected screen was recaptured. This small visual fix and
the opt-in capture helper were checked after the full gate recorded above.

Operational dispatch/preemption, post-handoff path recovery and the physical
three-phone gates remain separate work. No hosted deployment was performed.
