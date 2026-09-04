import { env, SELF } from "cloudflare:test";
import { beforeEach, describe, expect, it } from "vitest";
import { parsePublicObservation } from "./presentation";
import { readBoundedBody, parseCursor } from "./index";

const observation = {
  eventId: "route-one", sourceNodeId: "N4", kind: "routePlanned", occurredAtUnixMs: 1774000000000, simulated: true,
  presentation: { vehicleId: "truck-01", mode: "TRANSPORT_MODE_ROAD", edgeIds: ["E1", "E3"], etaMinutes: 65 },
};
const headers = { "Content-Type": "application/json", "X-Source-Node": "N4", Authorization: "Bearer test-only-publisher-token-not-a-deployed-secret" };
async function publish(body: unknown = observation, extraHeaders: Record<string, string> = {}) {
  return SELF.fetch("http://observer/v1/observations", { method: "POST", headers: { ...headers, ...extraHeaders }, body: JSON.stringify(body) });
}
beforeEach(async () => {
  await env.HQ_DB.prepare("CREATE TABLE IF NOT EXISTS observer_events (sequence INTEGER PRIMARY KEY AUTOINCREMENT, event_id TEXT NOT NULL UNIQUE, source_node_id TEXT NOT NULL, event_json TEXT NOT NULL, received_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP)").run();
  await env.HQ_DB.prepare("DELETE FROM observer_events").run();
});

describe("Hono observer with real local D1", () => {
  it("refuses anonymous and cross-source publications", async () => {
    expect((await publish(observation, { Authorization: "" })).status).toBe(401);
    expect((await publish(observation, { Authorization: headers.Authorization.slice(7) })).status).toBe(401);
    expect((await publish({ ...observation, sourceNodeId: "N1" })).status).toBe(422);
    expect((await env.HQ_DB.prepare("SELECT COUNT(*) AS count FROM observer_events").first<{ count: number }>())?.count).toBe(0);
  });
  it("assigns sequence numbers, deduplicates retries and rejects collisions", async () => {
    const first = await publish(); expect(first.status).toBe(201);
    const one = await first.json<{ sequence: number }>();
    const duplicate = await publish(); expect(duplicate.status).toBe(200);
    expect((await duplicate.json<{ sequence: number }>()).sequence).toBe(one.sequence);
    expect((await publish({ ...observation, presentation: { ...observation.presentation, etaMinutes: 90 } })).status).toBe(409);
    const next = await publish({ ...observation, eventId: "route-two" });
    expect((await next.json<{ sequence: number }>()).sequence).toBeGreaterThan(one.sequence);
    const replay = await SELF.fetch(`http://observer/v1/observations?after=${one.sequence}`);
    expect((await replay.json<{ observations: { eventId: string }[] }>()).observations.map((item) => item.eventId)).toEqual(["route-two"]);
  });
  it("streams only events after Last-Event-ID and supports cancellation", async () => {
    const first = await (await publish()).json<{ sequence: number }>();
    await publish({ ...observation, eventId: "route-two" });
    const response = await SELF.fetch("http://observer/observer/events?after=0", { headers: { "Last-Event-ID": String(first.sequence) } });
    expect(response.headers.get("content-type")).toContain("text/event-stream");
    const reader = response.body!.getReader();
    let text = "";
    for (let reads = 0; reads < 10 && !text.includes("route-two"); reads++) {
      const chunk = await reader.read(); if (chunk.done) break; text += new TextDecoder().decode(chunk.value);
    }
    await reader.cancel();
    expect(text).toContain("event: observation"); expect(text).toContain("route-two"); expect(text).not.toContain("route-one");
  });
  it("enforces actual bytes without trusting Content-Length", async () => {
    const response = await publish({ ...observation, padding: "x".repeat(20_000) }, { "Content-Length": "1" });
    expect(response.status).toBe(413);
    await expect(readBoundedBody(new Request("http://observer", { method: "POST", body: "abcdef" }), 2)).rejects.toThrow();
  });
  it("rejects payload fields and nonfinite/map-invalid values before storage", async () => {
    expect((await publish({ ...observation, encryptedPayload: "forbidden" })).status).toBe(422);
    expect((await publish({ ...observation, presentation: { ...observation.presentation, recipientPrivateKey: "forbidden" } })).status).toBe(422);
    expect(parsePublicObservation({ ...observation, kind: "edgeRiskPredicted", presentation: { edgeId: "E3", probability: NaN } })).toBeNull();
    expect(parsePublicObservation({ ...observation, kind: "rendezvousPlanned", presentation: { candidateId: "R2", latitudeDegrees: Infinity, longitudeDegrees: 91 } })).toBeNull();
    expect(parsePublicObservation({ ...observation, kind: "rendezvousPlanned", presentation: { candidateId: "R2", latitudeDegrees: 120, longitudeDegrees: 91 } })).toBeNull();
  });
  it("permits configured loopback origins and rejects other origins and cursors", async () => {
    const allowed = await SELF.fetch("http://observer/health", { headers: { Origin: "http://127.0.0.1:3000" } });
    expect(allowed.headers.get("Access-Control-Allow-Origin")).toBe("http://127.0.0.1:3000");
    expect((await SELF.fetch("http://observer/health", { headers: { Origin: "https://untrusted.invalid" } })).status).toBe(403);
    expect((await SELF.fetch("http://observer/observer/events?after=-1")).status).toBe(400);
    expect(parseCursor("NaN")).toBeNull(); expect(parseCursor("1e3")).toBeNull();
  });
});
