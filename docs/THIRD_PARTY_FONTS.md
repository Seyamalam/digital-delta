# Bundled font

The Android field application bundles `NotoSansBengali-Regular.ttf` from the official Noto Fonts repository so Bangla rendering does not depend on a downloaded font or a particular handset vendor image.

- Family: Noto Sans Bengali
- Version reported by the font: 2.002
- Source: `https://github.com/notofonts/noto-fonts/tree/main/hinted/ttf/NotoSansBengali`
- SHA-256: `6300c5370cd688b0641343de4c786de6d412bb6c578d129dae75e93a0322dcab`
- License: SIL Open Font License 1.1
- Packaged license: `apps/field-android/app/src/main/res/raw/noto_sans_bengali_license.txt`

`scripts/verify-local.sh` checks the exact font hash and requires equal string-resource keys in `values/` and `values-bn/` before any build or test begins.
