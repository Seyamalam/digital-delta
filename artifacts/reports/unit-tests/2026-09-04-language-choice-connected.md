# First-run language choice verification

Status: **Passed on 2026-09-04.**

The connected Android suite completed unchanged on both configured emulators:

- Android 15 Mento: 44 tests, 0 failures, 0 errors, 0 skipped.
- Android 16 Pixel: 44 tests, 0 failures, 0 errors, 0 skipped.

The new tests exercise the real Proto DataStore default and persistence behavior plus the visible Compose journey: a fresh installation presents `বাংলা` first and `English` second, and selecting Bangla enters the field interface without requiring a network connection. Existing production identity and encrypted-request journeys also pass through this first-run gate.

This is emulator evidence. Physical-phone language, font rendering, memory, and airplane-mode evidence remain release gates.
