# Signed local RBAC connected evidence — 2026-09-04

Command:

```bash
cd apps/field-android
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./gradlew connectedDebugAndroidTest
```

Result after the signed-audit addition: **49/49 passed, zero failed, zero skipped** on each emulator:

- Mento API 35 — Android 15
- Pixel 10 Pro XL API 36 — Android 16

New security evidence:

- a selected profile has no permissions without an installed administrator-signed credential;
- local authorization requires exact node, identity, role, encryption-key, and signing-key binding to Android Keystore;
- a validly signed coordinator credential cannot elevate the N4 clinic profile;
- a clinic credential is rejected below the UI when it calls coordinator-only conflict resolution;
- Compose disables the same forbidden action and explains the restriction in Bangla and English;
- the production request journey provisions the real N4 identity plus N6 recipient and still produces a decryptable recipient-only Protobuf envelope.
- authorization decisions form an ordered RSA-PSS-signed Protobuf hash chain; mutation is rejected, and the production identity screen displays its latest audit ID.

This does not prove remote revocation propagation or physical-phone secure-hardware backing. Those remain explicit release evidence tasks.
