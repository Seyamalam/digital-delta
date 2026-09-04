# Headquarters archive

This Cloudflare Worker stores a small, sanitized history of dashboard observations in D1. It is an optional headquarters convenience, not a mesh relay, command service, or field source of truth. The Worker accepts event metadata and a short operational summary only. It rejects unknown fields and sensitive payload labels.

## Local use

```bash
pnpm install
pnpm cf:typegen
pnpm d1:migrate:local
pnpm dev
```

Replace the placeholder D1 database ID in `wrangler.jsonc` after running `wrangler d1 create digital-delta-hq`. Set `ALLOWED_ORIGINS` to the exact Vercel production origin before deployment. Apply migrations remotely before `pnpm deploy`.
