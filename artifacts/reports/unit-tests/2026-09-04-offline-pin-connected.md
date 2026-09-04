# Offline PIN verification

Status: **Passed on 2026-09-04.**

The complete connected suite passed unchanged on both configured emulators:

- Android 15 Mento: 46 tests, 0 failures, 0 errors, 0 skipped.
- Android 16 Pixel: 46 tests, 0 failures, 0 errors, 0 skipped.

The PIN tests use the production Protobuf DataStore repository. They prove that a six-digit PIN is stored only as a random 16-byte-salted PBKDF2-HMAC-SHA256 verifier, survives repository recreation, rejects incorrect entries, persists a 30-second lockout after five failures, refuses the correct PIN during lockout, and accepts it after expiry. A Compose journey proves that the Bangla setup gate prevents field navigation from being mounted before setup completes. Existing Hilt production request and identity journeys pass through the same language and PIN gates.

This is emulator evidence. The PIN protects casual local access; it is not a substitute for full-disk encryption, hardware-backed user authentication, or administrator-signed role credentials.
