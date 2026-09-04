# Local verification baseline

Status: **Passed on 2026-09-04 at commit `73dd13c`.**

`scripts/verify-local.sh --connected` completed successfully on macOS 26.5 using Android 15 and Android 16 emulators. The run included deterministic model regeneration, offline-map checksum verification, Kotlin/JVM tests, two 42-test connected suites, debug and minified release APK assembly, bundled barcode-model inspection, Go race tests and vet, and the command-dashboard test and production build.

The result establishes a reproducible development baseline. It does not replace the physical-device and three-pass release evidence listed in `docs/TESTING.md`.
