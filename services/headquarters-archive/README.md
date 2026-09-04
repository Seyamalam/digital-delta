# Headquarters archive

This Cloudflare Worker stores a small, sanitized history of dashboard observations in D1. It is an optional headquarters convenience, not a mesh relay, command service, or field source of truth. The Worker accepts event metadata and a short operational summary only. It rejects unknown fields and sensitive payload labels.

## Local use

```bash
pnpm install
pnpm cf:typegen
pnpm d1:migrate:local
pnpm dev
```

The checked configuration targets the `digital-delta-hq` D1 database and the production Vercel origin. For a new Cloudflare account, create a replacement database, update its ID, set the exact allowed origin, and apply migrations before `pnpm deploy`.

Production endpoint: `https://digital-delta-headquarters-archive.seyamalam41.workers.dev`
