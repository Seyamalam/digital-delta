import { describe, expect, it, vi } from "vitest";
import { archiveObservation, probeArchive, toArchiveObservation } from "./archive";

const observation = {
  sequence: 9,
  sourceNodeId: "field-n4",
  eventId: "route-live-9",
  kind: "routePlanned",
  occurredAtUnixMs: 1_774_000_000_000,
  simulated: false,
  presentation: { vehicleId: "boat-02", mode: "TRANSPORT_MODE_WATERWAY", etaMinutes: 171, forbidden: "not archived" },
};

describe("optional D1 archive client", () => {
  it("projects only allow-listed presentation facts", () => {
    expect(toArchiveObservation(observation)).toEqual({
      eventId: "route-live-9",
      sequence: 9,
      sourceNodeId: "field-n4",
      kind: "routePlanned",
      occurredAtUnixMs: 1_774_000_000_000,
      simulated: false,
      summary: "routePlanned · boat-02 · TRANSPORT_MODE_WATERWAY · 171",
    });
  });

  it("checks the archive role before showing it as ready", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue({ ok: true, json: async () => ({ role: "optional_sanitized_archive" }) }));
    await expect(probeArchive("https://archive.example/")).resolves.toBe(true);
    vi.unstubAllGlobals();
  });

  it("fails open when the optional archive is unavailable", async () => {
    vi.stubGlobal("fetch", vi.fn().mockRejectedValue(new Error("offline")));
    await expect(archiveObservation("https://archive.example", observation)).resolves.toBe(false);
    vi.unstubAllGlobals();
  });
});
