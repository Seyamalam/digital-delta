# Hono observer bridge

The active observer is Hono on Cloudflare Workers with D1 storage. At the fair it
runs under Wrangler on the laptop, with local persisted D1 and no cloud dependency.
The Go node runs the Protobuf/gRPC mesh harness only by default. Its old observer
is retained behind `--legacy-observer` for migration comparisons, not normal use.

## Trust boundary

- Mesh domain traffic remains Protobuf. The observer is outside the mesh.
- A trusted publisher converts domain events to an explicit presentation allow-list.
  The HTTP JSON API cannot accept envelopes, ciphertext, credentials, signatures,
  arbitrary summaries, or unknown presentation fields.
- Publication requires a source-bound bearer secret in `PUBLISHER_KEYS`.
  This authenticates the enrolled collector, **not** each originating field event.
  Android's signed-event publisher is not yet integrated.
- D1 assigns monotonically increasing sequences. Repeating identical event IDs is
  idempotent; changing their contents returns 409. Sequences may contain gaps.
- Public reads contain only approved presentation metadata. Do not publish sensitive
  locations or identifying medical details to the hosted instance.
- Exact CORS origins, a 16 KiB streamed byte limit, numeric/coordinate validation,
  and publisher rate limits apply. CORS alone is not authentication.
- Streamed history is ascending. EventSource resumes with Last-Event-ID; full reload
  rebuilds from sequence zero. Each stream bounds its D1 polling then reconnects.
- Seeded data is explicitly simulated even when received over a real connection.
- Field Room operations, routing, custody and mesh do not call this service.

## Run locally

From the repository root:

```bash
make demo
```

The runner creates ignored, machine-local publisher credentials with restricted
permissions; it does not overwrite or print them. It migrates local D1, starts the
Hono observer on 7071, the Go mesh on 7070, and Next.js on 3000, then seeds seven
simulated observations. D1 persists in `.demo-state/observer`.

Separate commands for the observer:

```bash
node scripts/observer-local.mjs setup
cd services/headquarters-archive
pnpm exec wrangler d1 migrations apply digital-delta-hq --local --persist-to ../../.demo-state/observer
pnpm exec wrangler dev --local --ip 127.0.0.1 --port 7071 --persist-to ../../.demo-state/observer
```

Set `NEXT_PUBLIC_OBSERVER_URL=http://127.0.0.1:7071/observer/events` for the
local dashboard. Publisher tokens must never be bundled in Next.js or committed.

## Disconnect and replay

1. Run `node scripts/observer-local.mjs seed fair-pass-01`.
2. Open `/network` and disconnect the dashboard.
3. Run `node scripts/observer-local.mjs seed fair-pass-02`.
4. Reconnect. Events missed during disconnection must appear in ascending order.
5. Reload the page and confirm the same projection rebuilds from D1.

This tests observer isolation and replay, not physical three-phone independence.
That still requires a separate laptop-off test on real phones.

## Hosted deployment

The package retains its existing Worker/database names for migration continuity.
Migration 0002 adds an authenticated-publication log; old anonymous archive rows
are deliberately not promoted into it. Configure a source-to-secret JSON object
through `wrangler secret put PUBLISHER_KEYS`, review exact allowed origins, apply
remote migrations, and deploy. Hosted publishers must use HTTPS.

The implementation has been tested in local workerd/D1 and dry-run bundled.
A new production deployment is not implied by a local test or dry run. The checked
compatibility date is 2026-09-03, the latest supported by this installed runtime.

## Evidence

Local Worker tests exercise authentication, source binding, actual streamed size,
malformed data rejection, sequence assignment, idempotency, collision rejection,
cursor replay and SSE cancellation. Dashboard tests cover projection retention,
navigation, provenance and connection lifecycle. Go tests retain legacy transport
coverage but do not certify the Hono service.

```bash
scripts/verify-local.sh
```
