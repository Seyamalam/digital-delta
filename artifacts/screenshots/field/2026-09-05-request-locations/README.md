# Request-location evidence

Pixel_10_Pro_XL AVD, Android 16, September 5, 2026. These are settled captures from
the real Compose request screen in `MainScreenTest`, using an explicitly injected
coordinator authorization fixture. They are layout evidence, not enrollment,
physical network, or camera evidence. The separate `ProductionRequestFlowTest`
verifies selected pickup in a real signed/encrypted Room-backed request.

- `bn-picker.png`: all seven offline locations, Bangla, light theme.
- `en-selected.png`: non-default N4 pickup and N3 destination, English, light theme.
- `bn-picker-dark.png`: the same Bangla dialog in dark theme.
- `en-selected-dark.png`: retained choices in English, dark theme.

Capture with `captureRequestEvidence=true`; add `qaDarkMode=true` for dark mode.
Default verification does not write images. The light/dark capture variants each
passed their selection-retention and cancellation assertions. The capture helper
was added after the full 79-test gate and checked in these focused reruns.

A recorded picker journey was inspected separately for transitions. It does not
establish release-build frame rate or target-phone performance. These captures
belong to the request-location checkpoint commit containing this README.
