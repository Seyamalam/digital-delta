# Development inspection captures

Captured September 5, 2026 with Argent on emulator-5554 / CareNow_API_35,
Android 15 API 35, 1080×2400. Base commit `d33fe10` plus uncommitted custody-path,
reconciliation and historical-credential changes. These captures precede the
audit-label refresh correction. Do not present them as final release acceptance.

| File | Language | SHA-256 |
|---|---|---|
| mission-bn.png | Bangla | bda5be6cbd2de7a9d3b64d6510bc1947f14678aaed1c017fdbbdf5a802ba5c29 |
| mission-en.png | English | ca28cead207bab68e620e17a7c357f283cfad8f9ee7ebf1b9b9c1ed15791c914 |

Source: opt-in `ProductionRequestFlowTest` visual inspection window. Each image
uses a separate generated test request with 10 medicine packs and 20 ORS packs;
the screen correctly totals 30 medical units. Local persistence and permission
checks are real app code, but no real person requested or received supplies.
Packaged route assumptions remain labelled. Neither image proves radio range,
camera scanning, a signed driver handoff or physical-device performance.
