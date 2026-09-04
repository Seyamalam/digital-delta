# Five-minute pitch deck

Ten slides, including the cover. Target pace: 25 to 35 seconds per slide. Product interfaces appear in Bangla and English; technical identifiers remain language-neutral.

## Slide 1: Relief coordination after the network fails

**Offline relief coordination for Bangladesh**  
**ইন্টারনেট না থাকলেও ত্রাণ সমন্বয়**

One Android application for field teams. One optional local command view for the projector.

Visual: `artifacts/screenshots/command-bn/02-command-bn-live-overview-1920x1080.png`

Speaker note: Flood response loses more than roads. Power and commercial communications may disappear together. This project keeps the field workflow on the phones and treats the laptop as an observer.

## Slide 2: The operational break

### When the internet disappears

- Clinics cannot publish urgent supply requests.
- Drivers lose route updates when roads fail.
- Teams cannot prove who handed cargo to whom.
- A central dashboard becomes a single point of failure.

Speaker note: The project focuses on the coordination gap. Environmental and vehicle inputs in the fair scenario are simulations. The cryptography, local storage, routing, model inference, and protocol flows run in the prototype.

## Slide 3: One mission, eight working systems

### The field mission

A clinic queues P0 blood and medicine offline. Nearby phones relay the encrypted request. A failed road triggers a boat route. The triage engine predicts an SLA breach. A computed rendezvous transfers custody to a simulated drone. Both parties sign the receipt.

On-screen labels identify every simulated flood and vehicle event.

Visual: `artifacts/screenshots/field-bn/22-field-bn-hybrid-transferred.png`

Speaker note: Walk through this as one story rather than eight disconnected features. The same mission data moves through identity, sync, mesh, routing, custody, triage, prediction, and fleet orchestration.

## Slide 4: The laptop can disappear

### Offline architecture

```text
Clinic phone        Relay phone        Hospital phone
Room + keys    ⇄    durable queue  ⇄   Room + keys
       framed Protobuf over Nearby Connections

                     optional observation
                              ↓
                  Go gRPC service + SSE
                              ↓
                   projector dashboard
```

CAP choice: field nodes preserve availability and accept temporary divergence. Vector clocks and human review restore safe convergence later.

Speaker note: Closing the browser does not stop field publication. An automated fault test disconnects the SSE client, publishes two more events, then replays both in order after reconnection.

## Slide 5: Relays carry ciphertext, not cargo details

### Zero-trust nearby exchange

- Administrator-signed device credentials bind node, role, and public keys.
- Peers prove possession with fresh nonce challenges and RSA-PSS signatures.
- AES-256-GCM encrypts each payload. RSA-OAEP wraps the key for the final recipient.
- TTL, hop limit, durable acknowledgement, and deduplication protect the queue.

Visual: `artifacts/screenshots/field-bn/10-field-bn-nearby-relay-active-1280x2856.png`

Speaker note: A relay can read only the routing metadata needed to forward an envelope. It never receives the content key. Acknowledgements are also signed before a sender advances its outbox.

## Slide 6: Route risk changes the plan before a road closes

### Routing, prediction, and triage

The phone runs a bundled ONNX classifier over visibly simulated rainfall, elevation, and soil saturation. Risk adds a cost to the directed graph. Confirmed failures remove an edge. Vehicle constraints prevent trucks from using waterways and prevent boats from using roads.

Measured model result on held-out synthetic data: precision 0.613, recall 0.837, F1 0.708.

Visual: `artifacts/screenshots/field-en/20-field-en-onnx-risk-reroute.png`

Speaker note: The interface never presents a prediction as a confirmed flood. When the risk-adjusted ETA threatens the P0 SLA under a 30 percent slowdown, the phone proposes a safe P2 drop at N3 and waits for a coordinator.

## Slide 7: Every handoff leaves a verifiable chain

### Offline proof of delivery

The QR contains the delivery ID, sender key, payload hash, nonce, timestamp, and previous receipt hash. The recipient verifies the signature offline, claims the nonce atomically, signs the result, and extends the custody chain.

Tampering and replay produce different rejection reasons without changing the verified ledger.

Visual: `artifacts/screenshots/field-en/18-field-en-pod-replay-rejected.png`

Speaker note: This is real local cryptography and Room persistence around a simulated vehicle handoff. CameraX and the barcode model are bundled, while a two-phone airplane-mode camera run remains required before claiming physical evidence.

## Slide 8: The ten-minute live demonstration

### What the judges see

1. Switch the phone and dashboard between Bangla and English.
2. Queue the encrypted P0 request with commercial internet unavailable.
3. Interrupt the relay, reconnect it, and show duplicate rejection.
4. Inject route risk and confirmed road failure, then inspect the reroute and triage proposal.
5. Complete the signed handoff, then reject the same QR as a replay.
6. Disconnect and reconnect the projector while field work continues.

Speaker note: The dashboard uses the local observer during the live path and falls back to the fixed seed if that optional link fails. Keep the test runner visible on the second laptop.

## Slide 9: Evidence already in the repository

### Measured and repeatable

- 48 connected Android tests pass unchanged on Android 15 and Android 16 emulators.
- 13 projector tests cover observer replay, bilingual parity, local map sources, and faults.
- 10,000 independent gRPC streams received durable acknowledgements and stayed open together for five seconds.
- The ARM64 emulator measured 67,504 KB peak PSS after on-device model inference.

Limit: the 10,000-client burst reached 57.645 seconds p95 acknowledgement latency. It proves capacity, not production throughput.

Speaker note: State the limitation before a judge asks. Physical three-phone radio recovery and target-phone measurements remain the last hardware evidence, not completed claims.

## Slide 10: A practical field trial

### Next validation step

Run the complete mission on three ordinary Android phones in airplane mode with Bluetooth and local Wi-Fi enabled. Record relay recovery, camera handoff, phone memory, route latency, and three unchanged demo passes.

The fair build controls no drone or sensor. It coordinates people and records simulated vehicle events safely.

**Repository:** `github.com/Seyamalam/digital-delta`

Speaker note: Ask for access to field responders, disaster-management mentors, and representative Android phones. End on the Bangla interface and the tested ability to keep working without the command laptop.
