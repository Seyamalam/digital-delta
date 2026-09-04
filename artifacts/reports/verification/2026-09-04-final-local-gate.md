# Final local verification gate

Date: 4 September 2026

Command: `scripts/verify-local.sh --connected`

Result: passed

## Verified in one unchanged run

- Protobuf schema lint and stored v1 binary-envelope compatibility.
- Exact Bangla and English Android string-key parity and the no-raw-English critical-screen gate.
- Bundled Noto Sans Bengali hash.
- Checksum-pinned PMTiles archive and Android OpenStreetMap extract provenance.
- Deterministic synthetic route-risk dataset, metrics, configuration, Android assets, and ONNX byte parity.
- Android JVM tests, debug APK, minified release APK, and bundled offline ML Kit barcode model.
- Sixty connected Android journeys on the Android 15 Mento emulator with zero failures.
- The same sixty connected journeys on the Android 16 Pixel emulator with zero failures.
- Go race tests, vet, and all command builds.
- Seventeen Next.js headquarters tests, TypeScript check, and production build.
- Three Cloudflare archive tests, TypeScript check, and Wrangler deployment dry run.

## Reproduced model result

- Precision: 0.612766
- Recall: 0.837209
- F1: 0.707617
- Confusion matrix: `[[374, 182], [56, 288]]`
- Maximum ONNX parity error: `8.94069672e-08`

These values measure deterministic synthetic labels only. They are not field flood-forecast accuracy.

## Evidence boundary

The gate proves software behavior on the recorded laptop and emulators. It does not replace the three-physical-phone relay, real-camera airplane-mode, target-phone memory and latency, booth-power, TalkBack human review, or rehearsal gates in `TODO.md` and `docs/PHYSICAL_DEVICE_TEST.md`.
