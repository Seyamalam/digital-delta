# Local observer bridge

The observer bridge makes field events visible on the projector without making the laptop part of the field system. The Go node accepts presentation events through `ObserverService.Publish`, assigns a durable monotonic sequence in BoltDB, replays through `ObserverService.Observe`, and exposes a browser-only SSE projection at `/observer/events`.

## Trust boundary

- Node-to-node publication and replay use gRPC and Protocol Buffers.
- The SSE response is a disposable JSON presentation projection, never a mesh transport.
- The browser allow-list contains only explicit local dashboard origins.
- The projection mapper copies an allow-listed set of event fields. It never serializes `Envelope`, `EncryptedPayload`, ciphertext, wrapped content keys, or mesh signatures.
- The dashboard rebuilds from sequence zero after a page reload. During one open connection, the browser sends `Last-Event-ID` automatically and the server resumes after that sequence.
- The current publication endpoint is plaintext and does not yet authenticate the publishing peer. Keep it on a controlled local link. Signed peer publication is required before this becomes trusted field evidence.

## Run locally

Terminal one starts the durable node and browser bridge:

```bash
cd services/node
go run ./cmd/delta-node \
  -listen 127.0.0.1:7070 \
  -observer-listen 127.0.0.1:7071 \
  -data data/mesh.db \
  -observer-data data/observer.db
```

Terminal two starts the projector app:

```bash
cd apps/command
NEXT_PUBLIC_OBSERVER_URL=http://127.0.0.1:7071/observer/events pnpm dev
```

Terminal three can publish the repeatable rehearsal. These are synthetic disaster and vehicle facts and every event is marked `simulated=true` with the supplied scenario seed:

```bash
cd services/node
go run ./cmd/delta-drill \
  -observer 127.0.0.1:7070 \
  -source simulator-local-drill \
  -seed fair-pass-01
```

The dashboard should advance to `LIVE SEQ 7`, show the `E6 → E7` waterway route, ETA 171 minutes, the R3 rendezvous, risk state, and the simulated-event labels.

## Disconnect and replay proof

1. Publish `fair-pass-01` and confirm sequence 7.
2. Select **Disconnect dashboard**. The SSE connection closes and the UI states that field work continues.
3. While disconnected, publish another seed:

   ```bash
   go run ./cmd/delta-drill -observer 127.0.0.1:7070 -seed fair-pass-02 -interval 0
   ```

4. Confirm the projector remains on sequence 7 while the node prints sequences 8 through 14.
5. Select **Reconnect dashboard**. The projection must replay to sequence 14 in order.

This proves process-level observer isolation and durable replay. It does not replace the required physical-phone test proving that three field phones continue routing, queuing, and handoff operations with the laptop powered off.

## Automated evidence

- Go store tests cover ordered persistence, duplicate publication, cursor replay, and live wake-up.
- gRPC tests publish through the generated `ObserverService` client and replay through the generated stream.
- SSE tests cover cursor resume, exact CORS, and the presentation-field security boundary.
- The drill test guarantees deterministic IDs and explicit simulation labels.
- Dashboard tests cover connection state, duplicate sequence rejection, event projection, and complete route/hazard/rendezvous reconstruction from out-of-order input.

Run all of it through:

```bash
scripts/verify-local.sh
```
