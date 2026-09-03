# Engineering decisions

This log records decisions that affect judging claims, interoperability, or recovery behavior. Evidence links are repository paths so every decision can be reproduced offline.

## ADR-001: Native Android field client

- **Status:** accepted
- **Decision:** Kotlin, Jetpack Compose, Room, Proto DataStore, Hilt, and Android Keystore.
- **Reason:** field phones must remain independently useful without a browser, laptop, or commercial internet.
- **Evidence:** `apps/field-android`, `scripts/verify-local.sh --connected`.

## ADR-002: Protobuf mesh domain contract

- **Status:** accepted
- **Decision:** domain events and node-to-node envelopes are Protocol Buffers. JSON is forbidden in mesh packages.
- **Reason:** one versioned binary contract is shared by Android and Go and satisfies mandatory constraint C1/C6.
- **Evidence:** `packages/proto`, generated Android/Go bindings, and the JSON guard in `scripts/verify-local.sh`.

## ADR-003: Recipient-only hybrid payload encryption

- **Status:** accepted
- **Decision:** encrypt each payload with a fresh AES-256-GCM key and wrap that key with the final recipient's RSA-2048 public key using RSA-OAEP/SHA-256. RSA private identity keys remain non-exportable in Android Keystore.
- **Reason:** relays need routing metadata but must be unable to read domain payloads. RSA-2048 is explicitly permitted by constraint C5 and is consistently available across the Android baseline.
- **Compatibility note:** OAEP uses SHA-256 as its main digest and MGF1/SHA-1 for Android Keystore interoperability, matching Android's documented provider behavior. Payload hashes and provisioning signatures use SHA-256.
- **Evidence:** `HybridPayloadCipherTest`, `AndroidDeviceIdentityKeyStoreTest`, and Protobuf round-trip tests.

## ADR-004: Signed offline provisioning

- **Status:** accepted, UI integration in progress
- **Decision:** administrator credentials carry language-neutral identity claims, RSA encryption/signing public keys, validity times, issuer identity, and an RSA-PSS/SHA-256 signature in Protobuf.
- **Reason:** a field device must validate identity and role with no online authority.
- **Evidence:** `ProvisioningCredentialServiceTest`, `RecipientProvisioningRepositoryTest`, and Room schema version 2.
