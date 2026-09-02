# Security and safety model

## Scope

The fair build demonstrates offline identity, encrypted relay, signed custody, role enforcement, replay protection, and tamper-evident audit. It is not certified for production emergency deployment.

## Protected assets

- Device and user private keys
- Identity and role grants
- Medical cargo details
- Relief-request status and destination
- Inventory operations
- Route and assignment decisions
- Proof-of-delivery nonces and signatures
- Audit and custody history
- Conflict-resolution decisions

## Threats considered

| Threat | Required control |
|---|---|
| Stolen or copied phone | Local unlock, protected key storage, revocation event, minimal visible data |
| Malicious relay | Recipient encryption, signed envelope, limited metadata, TTL and hop limit |
| Duplicate message | Message ID deduplication before event application |
| Replayed QR | Persistent nonce cache, delivery-state check, expiry |
| Tampered QR or event | SHA-256 payload hash and Ed25519 signature verification |
| Unauthorized role | Policy enforcement below the interface and signed audit denial |
| False simulation presented as fact | Signed simulation marker and persistent screen label |
| Clock disagreement | Bounded skew rules and logical ordering through events and vector clocks |
| Database modification | Signature verification during projection rebuild and integrity report |
| Denial through queue flooding | Envelope size limit, per-peer quotas, expiry, priority scheduling |
| Lost revocation while offline | Short credential validity, last-known revocation state, visible uncertainty |

## Identity lifecycle

1. An administrator identity exists before deployment.
2. The administrator signs a provisioning package containing user, device, role, validity, issuer, and public key information.
3. The field device verifies and stores the package offline.
4. The private key remains in device-protected storage where available.
5. Role grants, expiry, and revocation are signed events.
6. Offline devices apply the newest verifiable information they possess and display when revocation status may be stale.

The app must not describe stale offline identity as globally current.

## Cryptographic choices

- Ed25519 for device identity and signatures
- AES-256-GCM for protected payloads
- SHA-256 for hashes and content addressing
- Platform secure random source for keys and nonces

Do not invent cryptographic primitives. Use maintained platform or audited libraries. Record library and platform decisions in `DECISIONS.md`.

## Message processing order

For an incoming envelope:

1. Enforce byte-size and structural limits.
2. Check schema support, TTL, hop limit, and message ID.
3. Verify sender identity state and envelope signature.
4. Check the encrypted payload hash.
5. Persist the envelope before acknowledgement.
6. If this node is the recipient, decrypt and verify the domain event.
7. Deduplicate before applying the event.
8. Enforce role and state-transition policy.
9. Append accepted event and rebuild affected projections.
10. Record rejection or acceptance without exposing protected content.

## Proof-of-delivery verification

The verifier checks:

- QR version and size;
- delivery ID and expected current custodian;
- sender public key and credential validity;
- payload hash;
- nonce uniqueness;
- timestamp and permitted skew;
- signature;
- proposed transition validity;
- recipient role.

The recipient signs a separate acceptance event. A sender's QR alone cannot complete custody.

## Role examples

| Action | Requester | Relay | Operator | Coordinator | Recipient | Auditor |
|---|---:|---:|---:|---:|---:|---:|
| Create request | Yes | No | No | Yes | Yes | No |
| Relay envelope | Optional | Yes | Yes | Yes | Optional | No |
| View protected cargo | Own requests | No | Assigned cargo | Yes | Intended delivery | As authorized |
| Confirm preemption | No | No | No | Yes | No | No |
| Offer custody | No | No | Current custodian | Yes | No | No |
| Accept custody | No | No | If assigned | No | Yes | No |
| Resolve safety conflict | No | No | No | Yes | No | No |
| Inspect audit | Own scope | Relay metadata | Own scope | Yes | Own scope | Yes |

The final policy will use explicit permissions rather than hard-coded screen names.

## Privacy rules

- Use fictional people and contact data in demonstrations.
- Do not store NID, birth-registration, passport, or personal medical records in the demo dataset.
- Keep role identity separate from public display name where possible.
- Redact key material and protected payloads from screenshots and logs.
- Export incident reports only through an authorized, signed action.
- Set retention and deletion policy before any real pilot.

## Safety boundaries

- Route risk is advisory unless an authorized user confirms an edge closure.
- Triage recommends but does not make medical decisions.
- Preemption that changes cargo custody requires human confirmation.
- The simulated drone cannot imply flight control or regulatory compliance.
- When identity or route state is uncertain, the interface must say so.
- A manual override records actor, reason, prior state, and resulting state.

## Security test cases

- Valid and invalid provisioning QR
- Expired, revoked, unknown, and wrong-role identity
- Altered envelope metadata and payload
- Message ID collision and duplicate delivery
- Oversized and unsupported envelope
- Expired TTL and exceeded hop count
- Modified, expired, reused, and wrong-delivery QR
- Concurrent custody offers
- Database row modification followed by projection rebuild
- Queue flooding from one peer
- Clock skew beyond policy
- Missing revocation update while offline

## Known fair-build limitations

- Physical possession of a provisioned unlocked phone may authorize actions until it locks.
- Offline revocation cannot reach a disconnected device until contact occurs.
- Nearby transport security does not replace application-layer recipient encryption.
- The demo has not undergone an independent security audit.
- Load simulation does not prove production behavior across real disaster geography.

