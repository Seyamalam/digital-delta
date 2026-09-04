import { parseArchiveObservation } from "./observation";

const jsonHeaders = { "content-type": "application/json; charset=utf-8" };

export default {
  async fetch(request, env): Promise<Response> {
    const url = new URL(request.url);
    const cors = corsHeaders(request, env.ALLOWED_ORIGINS);
    if (!cors) return Response.json({ error: "origin_not_allowed" }, { status: 403, headers: jsonHeaders });
    if (request.method === "OPTIONS") return new Response(null, { status: 204, headers: cors });
    if (url.pathname === "/health" && request.method === "GET") {
      return Response.json({ status: "ok", store: "d1", role: "optional_sanitized_archive" }, { headers: { ...jsonHeaders, ...cors } });
    }
    if (url.pathname !== "/v1/observations") return Response.json({ error: "not_found" }, { status: 404, headers: { ...jsonHeaders, ...cors } });
    if (request.method === "GET") return listObservations(url, env, cors);
    if (request.method === "POST") return storeObservation(request, env, cors);
    return Response.json({ error: "method_not_allowed" }, { status: 405, headers: { ...jsonHeaders, ...cors, allow: "GET, POST, OPTIONS" } });
  },
} satisfies ExportedHandler<Env>;

async function listObservations(url: URL, env: Env, cors: Record<string, string>): Promise<Response> {
  const requested = Number(url.searchParams.get("limit") ?? "50");
  const limit = Number.isInteger(requested) ? Math.min(Math.max(requested, 1), 100) : 50;
  const result = await env.HQ_DB.prepare(
    `SELECT event_id AS eventId, sequence, source_node_id AS sourceNodeId,
            event_kind AS kind, occurred_at_unix_ms AS occurredAtUnixMs,
            simulated, summary, received_at AS receivedAt
       FROM headquarters_observations
      ORDER BY sequence DESC
      LIMIT ?1`,
  ).bind(limit).all();
  return Response.json({ observations: result.results }, { headers: { ...jsonHeaders, ...cors, "cache-control": "no-store" } });
}

async function storeObservation(request: Request, env: Env, cors: Record<string, string>): Promise<Response> {
  const rate = await env.ARCHIVE_RATE_LIMITER.limit({ key: request.headers.get("cf-connecting-ip") ?? "unknown" });
  if (!rate.success) return Response.json({ error: "rate_limited" }, { status: 429, headers: { ...jsonHeaders, ...cors, "retry-after": "60" } });
  const length = Number(request.headers.get("content-length") ?? "0");
  if (length > 4096) return Response.json({ error: "body_too_large" }, { status: 413, headers: { ...jsonHeaders, ...cors } });
  let body: unknown;
  try {
    body = await request.json();
  } catch {
    return Response.json({ error: "invalid_json" }, { status: 400, headers: { ...jsonHeaders, ...cors } });
  }
  const observation = parseArchiveObservation(body);
  if (!observation) return Response.json({ error: "invalid_observation" }, { status: 422, headers: { ...jsonHeaders, ...cors } });
  const result = await env.HQ_DB.prepare(
    `INSERT INTO headquarters_observations
      (event_id, sequence, source_node_id, event_kind, occurred_at_unix_ms, simulated, summary)
     VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7)
     ON CONFLICT(event_id) DO NOTHING`,
  ).bind(
    observation.eventId,
    observation.sequence,
    observation.sourceNodeId,
    observation.kind,
    observation.occurredAtUnixMs,
    observation.simulated ? 1 : 0,
    observation.summary,
  ).run();
  return Response.json({ stored: result.meta.changes === 1, eventId: observation.eventId }, { status: result.meta.changes === 1 ? 201 : 200, headers: { ...jsonHeaders, ...cors } });
}

function corsHeaders(request: Request, configured: string): Record<string, string> | null {
  const origin = request.headers.get("origin");
  const allowed = configured.split(",").map((entry) => entry.trim()).filter(Boolean);
  if (origin && !allowed.includes(origin)) return null;
  const selected = origin ?? allowed[0];
  return {
    "access-control-allow-origin": selected,
    "access-control-allow-methods": "GET, POST, OPTIONS",
    "access-control-allow-headers": "content-type",
    "access-control-max-age": "86400",
    vary: "Origin",
  };
}
