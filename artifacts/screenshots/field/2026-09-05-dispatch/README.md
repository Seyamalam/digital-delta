# Accepted-mission dialog evidence

Source revision: `5df9ac8`. Pixel_10_Pro_XL emulator, Android 16 / API 36,
1344 × 2992, September 5, 2026. These are the production `MissionWorkspaceContent`
dialogs running with the explicitly simulated `ui-urgent` presentation fixture.
They are not screenshots of a physically provisioned three-phone mission.

Twelve captures pair Bangla/English for dispatch, assignment and reconciliation:

- `light-{bn,en}-{dispatch,assignment,reconcile}.png`: default system font size.
- `dark-xl-{bn,en}-{dispatch,assignment,reconcile}.png`: dark theme and system
  font scale 1.5. The emulator's original 1.0 setting was restored after the run.

Both focused tests passed in each final capture run. They assert disabled dispatch
before review, cancellation without writes, preservation of reviewed revision IDs,
coordinator-only path presentation and explicit reconciliation reasons. The full
connected gate separately passed 83 tests; independent Room/Keystore tests prove
actual encrypted authority and custody behavior.

Capture arguments: `captureMissionEvidence=true`, optional `qaDarkMode=true` and
`qaFontScale=1.5`. Native dialog windows also need the task emulator's system
font scale changed for genuine XL evidence. Compose-only scaling did not affect
those windows on this device. Normal verification writes no screenshots.

An Argent recording was inspected for dialog and keyboard transitions. It remains
local because test installation exposed another app before/after the test; it is
not a publishable demonstration video. Neither these debug captures nor that
recording establish release frame rate or target-phone performance.
