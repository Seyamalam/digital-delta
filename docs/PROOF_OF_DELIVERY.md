# Offline proof of delivery

This runbook describes the current M5 implementation and the exact limits of its evidence. It does not claim that camera-to-camera or production credential provisioning is finished.

## Working flow

1. The seeded boat identity creates a `DeliveryOffer` containing the delivery and mission IDs, sender and recipient IDs, SHA-256 payload hash, 128-bit nonce, timestamp, previous receipt hash, and simulation marker.
2. Android Keystore signs the canonical Protobuf bytes with RSA-2048-PSS/SHA-256. The app wraps the offer, public key, key ID, and signature in `SignedDeliveryOffer` and renders a real ZXing QR image.
3. The recipient checks the expected key and every operational field, verifies the signature, and enforces a bounded ten-minute disconnected-clock window.
4. Room claims the nonce and appends the Protobuf custody event in one transaction. A concurrent or later claim cannot create another receipt.
5. The custody event retains the sender offer signature and adds a recipient signature. Its SHA-256 hash becomes the next offer's `previous_receipt_sha256` value.
6. Chain reconstruction parses the Room operation log, checks every previous-hash link, and verifies both signatures without internet or the command laptop.

The fair build labels the boat and delivery scenario as simulated. The cryptographic operations, Keystore keys, QR bytes, Room transaction, and rejection paths are real application behavior.

## Threat checks

| Attempt | Result before custody mutation |
|---|---|
| Change a signed recipient or other offer field | RSA-PSS verification fails |
| Present a valid offer for another delivery or mission | Trusted-state comparison fails |
| Present an unexpected sender key | Key comparison fails |
| Present an offer outside the ten-minute clock window | Clock-window check fails |
| Present a previously accepted nonce | Atomic Room nonce claim fails |
| Present an offer linked to an old chain head | Previous-receipt comparison fails |

Malformed and rejected offers do not claim their nonce. This matters because the original, unmodified offer must remain usable after an attacker presents an altered copy.

## Live demonstration

1. Disable commercial internet and open **Handoff / হস্তান্তর**.
2. Point out the QR and `Protobuf • RSA-PSS • <key suffix>` line.
3. Tap **Verify signed handoff / স্বাক্ষরিত হস্তান্তর যাচাই করুন**. Show the green custody result.
4. Tap **Verify the same QR again / একই QR আবার যাচাই করুন**. Show replay rejection and explain that the receipt count did not change.
5. Tap **Prepare next linked handoff**, then **Test altered QR**. Show that the signed fields changed but neither nonce nor custody chain was updated.
6. Switch languages while the result remains visible.

The repeatable automated equivalent is:

```bash
ANDROID_SERIAL=emulator-5556 scripts/verify-local.sh --connected
```

Use the actual target serial. The command runs locally and does not use GitHub Actions.

## Evidence and remaining work

Current evidence includes codec tests, Android Keystore device tests, four Room workflow tests, a Compose replay journey, and Bangla/English screenshots 17 through 19 in `artifacts/screenshots/`.

Before calling M5 hardened, complete:

- bundled ML Kit camera scanning and airplane-mode proof on two phones;
- signed administrator credentials binding remote identity IDs to public keys;
- unknown, revoked, and expired credential rejection;
- a deliberate manual override policy with a signed audit event;
- a full scrollable custody timeline and inventory projection update;
- physical-device clock-drift and secure-key capability evidence.
