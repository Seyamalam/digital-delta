# M7 Android memory evidence

## Result

The minified ARM64 release stayed below the 150 MB mobile-RAM constraint after loading and running the bundled ONNX model.

| Sample | Total PSS | Total RSS |
|---|---:|---:|
| 1 | 67,118 KB | 179,280 KB |
| 2 | 67,504 KB | 180,544 KB |
| 3 | 67,471 KB | 180,512 KB |

Maximum attributed memory was **67,504 KB PSS** (about 65.9 MiB). Android's RSS total is included for transparency, but it counts every resident shared-library page in full; PSS proportionally attributes shared pages and is the metric used for the C3 comparison.

## Conditions

- Commit: `3e1f1b7`
- Device: Pixel 10 Pro XL AVD, Android 16 / API 36, ARM64, 16 KB pages
- Viewport: 1344 by 2992
- Build: R8-minified and resource-shrunk release, locally signed with the standard development key for emulator installation only
- Installed APK: 21,590,641 bytes
- Model: `route-risk-logreg-v1`, ONNX opset 17, ONNX Runtime Android 1.23.2
- Inputs: visibly simulated 82 mm/h rainfall, 3 m elevation, 0.92 soil saturation
- Visible result: 97.3% versus 28.5% threshold; E3 received a risk cost and M4 proactively chose the valid boat route
- Connectivity: inference and routing were local; no network input was used

The three readings came from `adb shell dumpsys meminfo com.example.digitaldelta`, two seconds apart, after the result was visible. No trim-memory or manual garbage-collection command was issued between inference and measurement.

## Release rehearsal finding

The first minified rehearsal exposed a Protobuf Lite field-name failure when switching languages. R8 had renamed a generated DataStore field that the Lite schema resolves by name. The release rules now preserve fields on `GeneratedMessageLite` subclasses. The release was rebuilt and the complete Bangla prediction → English switch was repeated successfully before these measurements were accepted.

## Limits

This is emulator evidence, not a target-phone report. The final fair claim still requires the same procedure on the named lowest-memory physical phone, including cold start, repeated inference, screen rotation, and a longer mission rehearsal.
