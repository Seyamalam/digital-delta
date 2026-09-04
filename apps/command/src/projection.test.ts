import { describe, expect, it } from "vitest";
import { projectObservations } from "./projection";
import type { PresentationObservation } from "./observer";

const observation = (sequence: number, kind: string, presentation: Record<string, unknown>): PresentationObservation => ({
  sequence,
  sourceNodeId: "field-n4",
  eventId: `event-${sequence}`,
  kind,
  occurredAtUnixMs: 1_774_000_000_000 + sequence,
  simulated: true,
  scenarioSeed: "fair-pass-01",
  presentation,
});

describe("observer projection", () => {
  it("rebuilds ordered route, hazard, rendezvous, and vehicle state", () => {
    const projection = projectObservations([
      observation(6, "vehicleStateChanged", { vehicleId: "boat-02", stateCode: "DELAYED_18_MIN" }),
      observation(2, "edgeStatusChanged", { edgeId: "E3", failed: true }),
      observation(5, "rendezvousPlanned", { candidateId: "R3", latitudeDegrees: 25.02, longitudeDegrees: 91.7 }),
      observation(3, "routePlanned", { mode: "TRANSPORT_MODE_WATERWAY", edgeIds: ["E6", "E7"], etaMinutes: 171 }),
      observation(1, "edgeRiskPredicted", { edgeId: "E3", probability: 0.973 }),
      observation(7, "edgeStatusChanged", { edgeId: "E3", failed: false }),
    ]);

    expect(projection.latestSequence).toBe(7);
    expect(projection.route?.edgeIds).toEqual(["E6", "E7"]);
    expect(projection.route?.etaMinutes).toBe(171);
    expect(projection.failedEdges.has("E3")).toBe(false);
    expect(projection.edgeRisks.get("E3")).toBeCloseTo(0.973);
    expect(projection.rendezvous?.candidateId).toBe("R3");
    expect(projection.delayedVehicleIds.has("boat-02")).toBe(true);
  });
});
