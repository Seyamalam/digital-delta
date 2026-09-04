import { describe, expect, it } from "vitest";
import { parseArchiveObservation } from "./observation";

const valid = {
  eventId: "route-live-9",
  sequence: 9,
  sourceNodeId: "field-n4",
  kind: "routePlanned",
  occurredAtUnixMs: 1_774_000_000_000,
  simulated: false,
  summary: "Boat route E6 to E7, ETA 171 minutes",
};

describe("archive observation boundary", () => {
  it("accepts only the sanitized presentation contract", () => {
    expect(parseArchiveObservation(valid)).toEqual(valid);
  });

  it("rejects unknown fields so mesh payloads cannot leak into D1", () => {
    expect(parseArchiveObservation({ ...valid, encryptedPayload: "secret" })).toBeNull();
  });

  it("rejects sensitive summary labels and invalid event kinds", () => {
    expect(parseArchiveObservation({ ...valid, summary: "cipher payload bytes" })).toBeNull();
    expect(parseArchiveObservation({ ...valid, kind: "rawMeshEnvelope" })).toBeNull();
  });
});
