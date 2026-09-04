# Signed revocation propagation — connected evidence

Date: 2026-09-04  
Commit under test: working tree after `e18c597`

## Targeted gate

```bash
cd apps/field-android
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
  ./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.example.digitaldelta.domain.identity.CredentialRevocationPropagationTest,com.example.digitaldelta.data.local.DeltaMigrationTest,com.example.digitaldelta.domain.mesh.RoomMeshIngressTest
```

Result: **8/8 passed, zero failed, zero skipped** on each emulator:

- Mento API 35 — Android 15
- Pixel 10 Pro XL API 36 — Android 16

The targeted run proves Room 4→5 preservation, local-recipient scheduling, relay non-application, recipient-specific RSA-OAEP/AES-256-GCM wrapping, exact signed revocation application, idempotence, and altered-AAD rejection.

## Unchanged full suite

```bash
cd apps/field-android
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
  ./gradlew connectedDebugAndroidTest
```

Result: **53/53 passed, zero failed, zero skipped** on each emulator in 2m 1s.

This is emulator evidence for the complete software path. It does not replace the pending physical multi-phone relay/recovery pass or measure real radio propagation time.
