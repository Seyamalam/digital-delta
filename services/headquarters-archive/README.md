# Hono headquarters observer

Hono runs the optional, non-authoritative observer on Cloudflare Workers. D1 stores
strictly allow-listed presentation events and assigns replay sequences. Publication
requires a source-bound bearer secret; public reads and SSE contain no mesh payloads.

Use [the observer runbook](../../docs/OBSERVER.md) for local setup, migration,
authentication, replay checks, deployment and known integration gaps.

```bash
pnpm install
pnpm test
pnpm typecheck
pnpm exec wrangler deploy --dry-run
```

The package and Worker retain the historical headquarters-archive name. The former
anonymous archive is not the current publication API. Local verification is not
evidence of a deployed cloud migration.
