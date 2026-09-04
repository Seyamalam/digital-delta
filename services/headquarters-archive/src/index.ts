import { Hono } from "hono";
import { streamSSE } from "hono/streaming";
import { timingSafeEqual } from "node:crypto";
import { parsePublicObservation, validIdentifier, type PublicObservation } from "./presentation";

export const app = new Hono<{ Bindings: Env }>();
const maxBodyBytes = 16_384;
app.use("*", async (c, next) => {
  const origin = c.req.header("Origin");
  const allowed = c.env.ALLOWED_ORIGINS.split(",").map((value) => value.trim());
  if (origin && !allowed.includes(origin)) return c.json({ error: "origin_not_allowed" }, 403);
  if (origin) { c.header("Access-Control-Allow-Origin", origin); c.header("Vary", "Origin"); }
  c.header("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
  c.header("Access-Control-Allow-Headers", "Content-Type, Authorization, X-Source-Node, Last-Event-ID");
  c.header("Cache-Control", "no-store");
  c.header("X-Content-Type-Options", "nosniff");
  if (c.req.method === "OPTIONS") return c.body(null, 204);
  await next();
});
app.get("/health", (c) => c.json({ status: "ok", store: "d1", role: "hono_observer", protocol: "sanitized-presentation-v1" }));
app.post("/v1/observations", async (c) => {
  // Browser origin checks are not authentication. Each publisher has a source-bound secret.
  const source = c.req.header("X-Source-Node");
  if (!validIdentifier(source)) return c.json({ error: "publisher_required" }, 401);
  let keys: Record<string, unknown>;
  try { keys = JSON.parse(c.env.PUBLISHER_KEYS ?? "{}"); } catch { return c.json({ error: "publisher_configuration_invalid" }, 503); }
  const expected = keys?.[source];
  if (typeof expected !== "string" || expected.length < 32) return c.json({ error: "publisher_not_enrolled" }, 401);
  const authorization = c.req.header("Authorization") ?? "";
  if (!authorization.startsWith("Bearer ")) return c.json({ error: "unauthorized" }, 401);
  const provided = authorization.slice(7);
  const encoder = new TextEncoder();
  const [actualHash, expectedHash] = await Promise.all([provided, expected].map((value) => crypto.subtle.digest("SHA-256", encoder.encode(value))));
  if (!timingSafeEqual(new Uint8Array(actualHash), new Uint8Array(expectedHash))) return c.json({ error: "unauthorized" }, 401);
  const rate = await c.env.ARCHIVE_RATE_LIMITER.limit({ key: source });
  if (!rate.success) { c.header("Retry-After", "60"); return c.json({ error: "rate_limited" }, 429); }
  if (c.req.header("Content-Type")?.split(";")[0] !== "application/json") return c.json({ error: "presentation_json_required" }, 415);
  let body: unknown;
  try { body = JSON.parse(await readBoundedBody(c.req.raw, maxBodyBytes)); }
  catch (error) { return c.json({ error: error instanceof BodyTooLarge ? "body_too_large" : "invalid_json" }, error instanceof BodyTooLarge ? 413 : 400); }
  const observation = parsePublicObservation(body);
  if (!observation || observation.sourceNodeId !== source) return c.json({ error: "invalid_observation" }, 422);
  const eventJson = JSON.stringify(observation);
  const insert = await c.env.HQ_DB.prepare("INSERT INTO observer_events (event_id, source_node_id, event_json) VALUES (?1, ?2, ?3) ON CONFLICT(event_id) DO NOTHING").bind(observation.eventId, source, eventJson).run();
  const row = await c.env.HQ_DB.prepare("SELECT sequence, event_json FROM observer_events WHERE event_id = ?1").bind(observation.eventId).first<{ sequence: number; event_json: string }>();
  if (!row) return c.json({ error: "store_failed" }, 503);
  if (row.event_json !== eventJson) return c.json({ error: "event_id_collision" }, 409);
  return c.json({ sequence: row.sequence, stored: insert.meta.changes === 1, eventId: observation.eventId }, insert.meta.changes === 1 ? 201 : 200);
});

app.get("/v1/observations", async (c) => {
  const cursor = parseCursor(c.req.query("after"));
  if (cursor === null) return c.json({ error: "invalid_cursor" }, 400);
  return c.json({ observations: await replay(c.env.HQ_DB, cursor) });
});
app.get("/observer/events", async (c) => {
  let cursor = parseCursor(c.req.header("Last-Event-ID") ?? c.req.query("after"));
  if (cursor === null) return c.json({ error: "invalid_cursor" }, 400);
  const after = cursor;
  // Preflight storage before returning SSE headers so database errors have an HTTP status.
  const first = await replay(c.env.HQ_DB, after);
  return streamSSE(c, async (stream) => {
    let page = first;
    let sequence = after;
    await stream.writeSSE({ event: "ready", data: JSON.stringify({ after }) });
    // Bound queries per stream. EventSource reconnects with Last-Event-ID automatically.
    for (let poll = 0; poll < 25 && !stream.aborted; poll++) {
      for (const observation of page) {
        await stream.writeSSE({ id: String(observation.sequence), event: "observation", data: JSON.stringify(observation) });
        sequence = observation.sequence;
      }
      if (page.length < 100) { await stream.writeSSE({ event: "heartbeat", data: String(sequence) }); await stream.sleep(2000); }
      if (!stream.aborted) page = await replay(c.env.HQ_DB, sequence);
    }
  });
});
app.onError((_error, c) => {
  // Do not log request bodies, tokens, or underlying SQL containing operational details.
  console.error(JSON.stringify({ message: "observer_request_failed", path: c.req.path }));
  return c.json({ error: "observer_unavailable" }, 503);
});
app.notFound((c) => c.json({ error: "not_found" }, 404));

export function parseCursor(value: string | undefined): number | null {
  if (!value) return 0;
  if (!/^\d+$/.test(value)) return null;
  const result = Number(value);
  return Number.isSafeInteger(result) && result >= 0 ? result : null;
}

async function replay(database: D1Database, after: number) {
  const rows = await database.prepare("SELECT sequence, event_json FROM observer_events WHERE sequence > ?1 ORDER BY sequence ASC LIMIT 100").bind(after).all<{ sequence: number; event_json: string }>();
  return rows.results.map((row) => ({ ...JSON.parse(row.event_json) as PublicObservation, sequence: row.sequence }));
}
class BodyTooLarge extends Error {}
export async function readBoundedBody(request: Request, limit: number): Promise<string> {
  if (!request.body) return "";
  const reader = request.body.getReader();
  const chunks: Uint8Array[] = [];
  let size = 0;
  try {
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      size += value.byteLength;
      if (size > limit) { await reader.cancel(); throw new BodyTooLarge(); }
      chunks.push(value);
    }
  } finally { reader.releaseLock(); }
  const bytes = new Uint8Array(size);
  let offset = 0;
  for (const chunk of chunks) { bytes.set(chunk, offset); offset += chunk.byteLength; }
  return new TextDecoder("utf-8", { fatal: true }).decode(bytes);
}
export default app;
