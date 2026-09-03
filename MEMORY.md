# Project environment

- Repository: `https://github.com/Seyamalam/digital-delta`, branch `main`.
- Field app: native Android under `apps/field-android`.
- Toolchain: JDK 17 at `/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home`, Android SDK at `/Users/seyam/Library/Android/sdk`, Android CLI at `/Users/seyam/.local/bin/android`.
- Android CLI version recorded during scaffold: `1.0.15985488`.
- Available emulators: `Pixel_10_Pro_XL` and `Mento_API_35`; `Mento_API_35` was running on `emulator-5554` during the initial implementation.
- Android project baseline: AGP 9.0.1, Gradle 9.1, Kotlin 2.3.20, Compose BOM 2026.03.01, compile/target SDK 36, min SDK 26.
- Local-only automation: no GitHub Actions or hosted CI.
- Selected visual target: `artifacts/design-options/selected-delta-map-refined.png`.
- First implementation screenshot: `artifacts/screenshots/android-home-bn.png`.
- Build commands and testing strategy: `docs/TESTING.md` and `scripts/verify-local.sh`.
